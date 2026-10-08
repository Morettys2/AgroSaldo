package br.joao.agrosaldo.domain.conta

import br.joao.agrosaldo.domain.model.InstanteMonotonico
import br.joao.agrosaldo.domain.model.TermoDePrivacidade
import br.joao.agrosaldo.domain.model.Usuario
import br.joao.agrosaldo.domain.repository.EstadoTentativas
import br.joao.agrosaldo.domain.repository.SessaoRepository
import br.joao.agrosaldo.domain.repository.TentativasRepository
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

sealed interface ResultadoCadastro {
    data class Sucesso(val usuario: Usuario) : ResultadoCadastro
    data object EmailJaCadastrado : ResultadoCadastro
    data object TermoNaoAceito : ResultadoCadastro
    data class DadosInvalidos(val motivo: String) : ResultadoCadastro
}

sealed interface ResultadoLogin {
    data class Sucesso(val usuario: Usuario) : ResultadoLogin
    data object CredenciaisInvalidas : ResultadoLogin
    data class Bloqueado(val segundosRestantes: Long) : ResultadoLogin
}

sealed interface ResultadoExclusao {
    data object Sucesso : ResultadoExclusao
    data object SenhaIncorreta : ResultadoExclusao
    data class Bloqueado(val segundosRestantes: Long) : ResultadoExclusao
    data object SemSessao : ResultadoExclusao
}

sealed interface ResultadoAlteracaoSenha {
    data object Sucesso : ResultadoAlteracaoSenha
    data object SenhaAtualIncorreta : ResultadoAlteracaoSenha
    data object NovaIgualAAtual : ResultadoAlteracaoSenha
    data object NovaNaoCumpreRequisitos : ResultadoAlteracaoSenha
    data class Bloqueado(val segundosRestantes: Long) : ResultadoAlteracaoSenha
    data object SemSessao : ResultadoAlteracaoSenha
}

/**
 * Casos de uso de conta local: cadastro, login, logout, troca de senha e
 * exclusão (RF01, RF02, RNF10, RF16). Encapsula hash, sessão e bloqueio por
 * tentativas; os ViewModels só consomem os resultados selados.
 *
 * @param relogio fonte de tempo em epoch ms (injetável em testes).
 * @param relogioMonotonico tempo imune a mudanças de data/hora, usado no bloqueio
 *   por tentativas; null quando indisponível (cai para [relogio]).
 * @param dispatcherCalculo dispatcher do PBKDF2; nunca a main thread.
 * @param aoTrocarDeUsuario chamado antes de iniciar ou encerrar uma sessão,
 *   para limpar estado ligado ao usuário anterior (ex.: propriedade selecionada).
 */
class ContaService(
    private val usuarios: UsuarioRepository,
    private val sessao: SessaoRepository,
    private val tentativas: TentativasRepository,
    private val hash: HashDeSenha = HashDeSenha(),
    private val relogio: () -> Long = System::currentTimeMillis,
    private val relogioMonotonico: () -> InstanteMonotonico? = { null },
    private val dispatcherCalculo: CoroutineDispatcher = Dispatchers.Default,
    private val aoTrocarDeUsuario: () -> Unit = {}
) {

    val usuarioLogadoId: StateFlow<String?> = sessao.usuarioId

    fun estaLogado(): Boolean = sessao.usuarioId.value != null

    /** Usuário da sessão atual; reemite em login, logout e alterações do registro. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observarUsuarioLogado(): Flow<Usuario?> =
        sessao.usuarioId.flatMapLatest { id ->
            if (id == null) flowOf(null) else usuarios.observar(id)
        }

    /** true se o usuário conectado aceitou uma versão anterior a [TermoDePrivacidade.VERSAO_ATUAL]. */
    fun precisaNovoAceite(): Flow<Boolean> =
        observarUsuarioLogado()
            .map { usuario -> usuario != null && usuario.versaoTermo < TermoDePrivacidade.VERSAO_ATUAL }
            .distinctUntilChanged()

    /** Registra o aceite da versão atual do termo. Retorna false sem sessão válida. */
    suspend fun registrarNovoAceite(): Boolean {
        val usuario = usuarioConectado() ?: return false
        usuarios.registrarConsentimento(usuario.id, Instant.ofEpochMilli(relogio()), TermoDePrivacidade.VERSAO_ATUAL)
        return true
    }

    suspend fun emailEmUso(email: String): Boolean =
        usuarios.buscarPorEmail(RegrasDeConta.normalizarEmail(email)) != null

    // ---------- cadastro ----------

    suspend fun cadastrar(
        nome: String,
        email: String,
        senha: String,
        aceitouTermo: Boolean
    ): ResultadoCadastro {
        // Revalida mesmo que a UI já tenha validado: o serviço é a última barreira.
        if (!aceitouTermo) return ResultadoCadastro.TermoNaoAceito
        if (!RegrasDeConta.nomeValido(nome)) return ResultadoCadastro.DadosInvalidos("Nome inválido.")
        if (!RegrasDeConta.emailValido(email)) return ResultadoCadastro.DadosInvalidos("E-mail inválido.")
        if (!RegrasDeConta.senhaValida(senha, email, nome)) {
            return ResultadoCadastro.DadosInvalidos("A senha não cumpre os requisitos.")
        }

        val emailNormalizado = RegrasDeConta.normalizarEmail(email)
        if (usuarios.buscarPorEmail(emailNormalizado) != null) return ResultadoCadastro.EmailJaCadastrado

        val agora = Instant.ofEpochMilli(relogio())
        val usuario = Usuario(
            id = UUID.randomUUID().toString(),
            nome = nome.trim(),
            email = emailNormalizado,
            senhaHash = calcularHash(senha),
            consentimentoEm = agora,
            versaoTermo = TermoDePrivacidade.VERSAO_ATUAL,
            criadoEm = agora
        )

        // Índice único no e-mail cobre a corrida entre a checagem acima e o insert.
        if (!usuarios.criar(usuario)) return ResultadoCadastro.EmailJaCadastrado

        conectar(usuario.id)
        return ResultadoCadastro.Sucesso(usuario)
    }

    // ---------- login ----------

    suspend fun entrar(email: String, senha: String): ResultadoLogin {
        segundosDeBloqueio()?.let { return ResultadoLogin.Bloqueado(it) }

        val usuario = usuarios.buscarPorEmail(RegrasDeConta.normalizarEmail(email))

        // Sem conta, confere contra um hash fictício: tempo de resposta igual
        // ao de senha errada, evitando enumeração de e-mails por timing.
        val senhaConfere = conferir(senha, usuario?.senhaHash)

        if (usuario == null || !senhaConfere) {
            registrarFalha()
            return ResultadoLogin.CredenciaisInvalidas
        }

        tentativas.gravar(EstadoTentativas())

        // Rehash transparente quando o hash gravado usa menos iterações que o padrão atual.
        if (hash.precisaAtualizar(usuario.senhaHash)) {
            usuarios.atualizarSenha(usuario.id, calcularHash(senha))
        }

        conectar(usuario.id)
        return ResultadoLogin.Sucesso(usuario)
    }

    fun sair() {
        aoTrocarDeUsuario()
        sessao.encerrar()
    }

    // ---------- senha ----------

    suspend fun alterarSenha(senhaAtual: String, novaSenha: String): ResultadoAlteracaoSenha {
        val usuario = usuarioConectado() ?: return ResultadoAlteracaoSenha.SemSessao
        segundosDeBloqueio()?.let { return ResultadoAlteracaoSenha.Bloqueado(it) }

        if (!conferir(senhaAtual, usuario.senhaHash)) {
            registrarFalha()
            return ResultadoAlteracaoSenha.SenhaAtualIncorreta
        }
        tentativas.gravar(EstadoTentativas())

        if (novaSenha == senhaAtual) return ResultadoAlteracaoSenha.NovaIgualAAtual
        if (!RegrasDeConta.senhaValida(novaSenha, usuario.email, usuario.nome)) {
            return ResultadoAlteracaoSenha.NovaNaoCumpreRequisitos
        }

        usuarios.atualizarSenha(usuario.id, calcularHash(novaSenha))
        return ResultadoAlteracaoSenha.Sucesso
    }

    // ---------- exclusão ----------

    /**
     * Exclusão de dados (LGPD art. 18, VI): remove a conta, propriedades e
     * lançamentos e encerra a sessão. Exige reautenticação por senha.
     */
    suspend fun excluirConta(senha: String): ResultadoExclusao {
        val usuario = usuarioConectado() ?: return ResultadoExclusao.SemSessao
        segundosDeBloqueio()?.let { return ResultadoExclusao.Bloqueado(it) }

        if (!conferir(senha, usuario.senhaHash)) {
            registrarFalha()
            return ResultadoExclusao.SenhaIncorreta
        }
        tentativas.gravar(EstadoTentativas())

        usuarios.excluir(usuario.id)
        sair()
        return ResultadoExclusao.Sucesso
    }

    /**
     * Chamado na abertura do app: encerra a sessão se ela aponta para uma
     * conta que não existe mais no banco.
     */
    suspend fun validarSessao() {
        usuarioConectado()
    }

    // ---------- bloqueio contra tentativa e erro ----------

    /**
     * Contador único para login, troca de senha e exclusão de conta, para que
     * nenhum fluxo sirva de atalho para força bruta.
     *
     * A partir de [MAXIMO_TENTATIVAS] falhas seguidas bloqueia por [BLOQUEIO_MS],
     * dobrando a cada falha adicional até [BLOQUEIO_MAXIMO_MS]. Estado persistido.
     */
    private fun registrarFalha() {
        val falhas = tentativas.ler().falhasSeguidas + 1
        if (falhas < MAXIMO_TENTATIVAS) {
            tentativas.gravar(EstadoTentativas(falhasSeguidas = falhas))
            return
        }
        val dobras = (falhas - MAXIMO_TENTATIVAS).coerceAtMost(MAXIMO_DOBRAS)
        bloquear(falhas, duracao = (BLOQUEIO_MS shl dobras).coerceAtMost(BLOQUEIO_MAXIMO_MS))
    }

    /**
     * Grava o fim do bloqueio nos dois relógios: o monotônico decide enquanto o
     * aparelho não reinicia (alterar data/hora não tem efeito); o de parede é a
     * reserva depois de um reinício.
     */
    private fun bloquear(falhas: Int, duracao: Long) {
        tentativas.gravar(
            EstadoTentativas(
                falhasSeguidas = falhas,
                bloqueadoAte = relogio() + duracao,
                fimMonotonico = relogioMonotonico()?.mais(duracao)
            )
        )
    }

    /** Segundos restantes de bloqueio (arredondados para cima), ou null se liberado. */
    private fun segundosDeBloqueio(): Long? {
        val estado = tentativas.ler()
        val fimMonotonico = estado.fimMonotonico
        val agoraMonotonico = relogioMonotonico()
        val restante = if (fimMonotonico != null && agoraMonotonico?.boot == fimMonotonico.boot) {
            fimMonotonico.desdeOBootMs - agoraMonotonico.desdeOBootMs
        } else {
            estado.bloqueadoAte - relogio()
        }
        if (restante <= 0L) return null

        // Mais que o máximo só acontece se o relógio de parede mudou depois da
        // gravação: reancora no máximo para a espera não durar meses ou anos.
        if (restante > BLOQUEIO_MAXIMO_MS) {
            bloquear(estado.falhasSeguidas, BLOQUEIO_MAXIMO_MS)
            return BLOQUEIO_MAXIMO_MS / 1000
        }
        return (restante + 999) / 1000
    }

    // ---------- apoio ----------

    /** Usuário da sessão, ou null. Encerra a sessão se a conta não existir mais. */
    private suspend fun usuarioConectado(): Usuario? {
        val id = sessao.usuarioId.value ?: return null
        val usuario = usuarios.buscarPorId(id)
        if (usuario == null) sair()
        return usuario
    }

    private fun conectar(usuarioId: String) {
        aoTrocarDeUsuario()
        sessao.iniciar(usuarioId)
    }

    // PBKDF2 é CPU-bound (centenas de ms): sempre em [dispatcherCalculo].
    private suspend fun calcularHash(senha: String): String =
        withContext(dispatcherCalculo) { hash.gerar(senha) }

    /** Com [armazenado] nulo, roda contra [hashFicticio] e retorna sempre false. */
    private suspend fun conferir(senha: String, armazenado: String?): Boolean =
        withContext(dispatcherCalculo) { hash.confere(senha, armazenado ?: hashFicticio) && armazenado != null }

    private val hashFicticio: String by lazy { hash.gerar(UUID.randomUUID().toString()) }

    companion object {
        const val MAXIMO_TENTATIVAS = 5
        const val BLOQUEIO_MS = 30_000L
        const val BLOQUEIO_MAXIMO_MS = 15 * 60_000L
        private const val MAXIMO_DOBRAS = 5
    }
}
