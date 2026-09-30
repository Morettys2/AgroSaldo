package br.joao.agrosaldo.domain.conta

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
 * Tudo o que envolve conta: criar, entrar, sair, trocar a senha e excluir
 * (RF01, RF02, RNF10, RF16).
 *
 * É uma classe de domínio, sem Android — o equivalente a um AuthService do
 * NestJS. Os ViewModels só chamam estes métodos; nenhum deles sabe o que é
 * hash, sal, sessão ou bloqueio.
 *
 * Os parâmetros com valor padrão existem para os testes:
 * - [relogio] fixa o horário (para testar o bloqueio sem esperar);
 * - [dispatcherCalculo] troca a thread do PBKDF2 por uma de teste;
 * - [aoTrocarDeUsuario] é avisado quando a pessoa conectada muda, para o app
 *   esquecer a propriedade que estava selecionada.
 */
class ContaService(
    private val usuarios: UsuarioRepository,
    private val sessao: SessaoRepository,
    private val tentativas: TentativasRepository,
    private val hash: HashDeSenha = HashDeSenha(),
    private val relogio: () -> Long = System::currentTimeMillis,
    private val dispatcherCalculo: CoroutineDispatcher = Dispatchers.Default,
    private val aoTrocarDeUsuario: () -> Unit = {}
) {

    val usuarioLogadoId: StateFlow<String?> = sessao.usuarioId

    fun estaLogado(): Boolean = sessao.usuarioId.value != null

    /** Emite o usuário conectado, e emite de novo se ele mudar ou sair. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observarUsuarioLogado(): Flow<Usuario?> =
        sessao.usuarioId.flatMapLatest { id ->
            if (id == null) flowOf(null) else usuarios.observar(id)
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
        // A tela já valida tudo isto. Validar de novo aqui é a rede de
        // segurança: nenhuma tela nova, feita às pressas, consegue gravar
        // uma conta sem senha forte ou sem o aceite da LGPD.
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

        // O banco tem índice único no e-mail. Se dois toques rápidos
        // chegarem aqui juntos, o segundo é recusado por ele.
        if (!usuarios.criar(usuario)) return ResultadoCadastro.EmailJaCadastrado

        conectar(usuario.id)
        return ResultadoCadastro.Sucesso(usuario)
    }

    // ---------- login ----------

    suspend fun entrar(email: String, senha: String): ResultadoLogin {
        segundosDeBloqueio()?.let { return ResultadoLogin.Bloqueado(it) }

        val usuario = usuarios.buscarPorEmail(RegrasDeConta.normalizarEmail(email))

        // Mesmo sem conta para esse e-mail, o PBKDF2 roda contra um hash de
        // mentira. Assim "e-mail não existe" e "senha errada" demoram o mesmo
        // tempo, e ninguém descobre quais e-mails têm conta cronometrando.
        val senhaConfere = conferir(senha, usuario?.senhaHash)

        if (usuario == null || !senhaConfere) {
            registrarFalha()
            return ResultadoLogin.CredenciaisInvalidas
        }

        tentativas.gravar(EstadoTentativas())

        // Conta criada quando o custo do hash era menor: aproveita que a senha
        // está em mãos e grava um hash novo, com o custo de hoje. A pessoa
        // não percebe nada — é assim que se sobe o custo sem pedir senha nova.
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
     * Direito de exclusão da LGPD (art. 18, VI): apaga a conta, as
     * propriedades e os lançamentos. Pede a senha de novo para que um
     * celular emprestado não perca tudo com dois toques.
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
     * Chamado ao abrir o app. Se a sessão aponta para uma conta que não
     * existe mais no banco (dados apagados pelas configurações do Android,
     * por exemplo), encerra a sessão em vez de abrir o app vazio.
     */
    suspend fun validarSessao() {
        usuarioConectado()
    }

    // ---------- bloqueio contra tentativa e erro ----------

    /**
     * Toda conferência de senha passa pelo mesmo contador: login, troca de
     * senha e exclusão. Senão bastaria chutar senhas pela tela de exclusão.
     *
     * A espera cresce: 5 erros seguidos travam 30 s; cada erro depois disso
     * dobra a espera (1 min, 2 min, 4 min...) até o máximo de 15 min. O
     * contador é gravado, então fechar e abrir o app não zera nada.
     */
    private fun registrarFalha() {
        val falhas = tentativas.ler().falhasSeguidas + 1
        val bloqueadoAte = if (falhas >= MAXIMO_TENTATIVAS) {
            val dobras = (falhas - MAXIMO_TENTATIVAS).coerceAtMost(MAXIMO_DOBRAS)
            relogio() + (BLOQUEIO_MS shl dobras).coerceAtMost(BLOQUEIO_MAXIMO_MS)
        } else {
            0L
        }
        tentativas.gravar(EstadoTentativas(falhasSeguidas = falhas, bloqueadoAte = bloqueadoAte))
    }

    /** Segundos que ainda faltam de bloqueio, ou null se está liberado. */
    private fun segundosDeBloqueio(): Long? {
        val restante = tentativas.ler().bloqueadoAte - relogio()
        if (restante <= 0L) return null
        // Se alguém atrasar o relógio do celular, a espera não vira meses:
        // nunca passa do máximo.
        val limitado = restante.coerceAtMost(BLOQUEIO_MAXIMO_MS)
        return (limitado + 999) / 1000
    }

    // ---------- apoio ----------

    /** O usuário da sessão, ou null. Sessão apontando para conta apagada é encerrada. */
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

    // PBKDF2 é cálculo pesado de propósito. Fora da thread principal, a tela
    // continua respondendo enquanto ele roda.
    private suspend fun calcularHash(senha: String): String =
        withContext(dispatcherCalculo) { hash.gerar(senha) }

    /** Com [armazenado] nulo, confere contra o hash de mentira (e sempre dá false). */
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
