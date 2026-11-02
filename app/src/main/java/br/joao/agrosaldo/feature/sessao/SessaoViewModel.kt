package br.joao.agrosaldo.feature.sessao

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.joao.agrosaldo.core.util.repetirAposErro
import br.joao.agrosaldo.core.util.tentar
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.RelogioMonotonico
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.repository.BloqueioRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Fonte única do estado de sessão observado pela navegação:
 *
 * - [logado]: há usuário autenticado.
 * - [precisaNovoAceite]: a política mudou desde o último aceite (null enquanto a conta não foi lida).
 * - [precisaDesbloquear]: bloqueio biométrico ativo e app ainda não desbloqueado
 *   desde a abertura ou desde [TEMPO_PARA_TRAVAR_MS] em segundo plano.
 * - [oferecerBloqueio]: conta recém-criada, com o bloqueio ainda desligado.
 *
 * As ações só alteram estado; o redirecionamento é feito pela navegação.
 */
@HiltViewModel
class SessaoViewModel @Inject constructor(
    private val conta: ContaService,
    private val bloqueio: BloqueioRepository,
    /** Só encontra propriedades do usuário conectado. */
    private val propriedades: PropriedadeRepository,
    /** Monotônico: mudar a hora do aparelho não destrava nem trava o app. */
    private val relogio: RelogioMonotonico,
    /** Escolhe a propriedade do aviso de chuva. */
    private val selecao: SelecaoAtual
) : ViewModel() {

    /** Valor inicial lido da sessão gravada, para evitar um frame com a tela de login. */
    val logado: StateFlow<Boolean> = conta.usuarioLogadoId
        .map { id -> id != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, conta.estaLogado())

    /**
     * Começa desconhecido (null), e não false: até a conta ser lida, a navegação não mostra
     * nenhuma tela, para o Início não aparecer antes do desvio para o novo aceite.
     */
    val precisaNovoAceite: StateFlow<Boolean?> = conta.precisaNovoAceite()
        // Banco travado ao abrir: a navegação segue coberta (null) e a leitura recomeça sozinha.
        .repetirAposErro {}
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Começa bloqueado: sessão restaurada exige biometria.
    private val desbloqueadoNestaSessao = MutableStateFlow(false)
    private var foiParaSegundoPlanoEm: Long? = null

    val precisaDesbloquear: StateFlow<Boolean> =
        combine(logado, bloqueio.ativo, desbloqueadoNestaSessao) { estaLogado, ativo, desbloqueado ->
            estaLogado && ativo && !desbloqueado
        }.stateIn(viewModelScope, SharingStarted.Eagerly, conta.estaLogado() && bloqueio.ativo.value)

    /**
     * Oferta de ligar o bloqueio por digital logo depois do cadastro (o padrão é desligado
     * e a sessão não expira). Some ao ligar, ao dispensar ou ao sair; a tela só a mostra
     * se o aparelho tiver biometria ou credencial.
     */
    val oferecerBloqueio: StateFlow<Boolean> =
        combine(conta.sessaoDeContaNova, bloqueio.ativo) { contaNova, ativo -> contaNova && !ativo }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Banco travado: a sessão é conferida de novo na próxima abertura do app.
        viewModelScope.launch { tentar { conta.validarSessao() } }

        // drop(1) ignora a sessão restaurada. Login com senha desbloqueia; logout bloqueia.
        viewModelScope.launch {
            conta.usuarioLogadoId.drop(1).collect { id -> desbloqueadoNestaSessao.value = id != null }
        }

        // Ativar o bloqueio já exige biometria; não bloqueia imediatamente.
        viewModelScope.launch {
            bloqueio.ativo.drop(1).collect { ativo -> if (ativo) desbloqueadoNestaSessao.value = true }
        }
    }

    fun aoDesbloquear() {
        desbloqueadoNestaSessao.value = true
        // O prompt de PIN/credencial do sistema põe o app em segundo plano; descarta essa saída.
        foiParaSegundoPlanoEm = null
    }

    /**
     * Pedido de fora do app para abrir o Clima (toque na notificação de chuva; a Activity é
     * exportada, então pode vir de outro app). Espera a conta ser lida e só vale com sessão
     * e sem novo aceite pendente, para o pedido não furar o aceite. Com [propriedadeId],
     * seleciona a propriedade do aviso; um id que não é do usuário conectado invalida o
     * pedido. Sem ele (notificação de versão anterior), o Clima abre na seleção atual.
     *
     * @return true se a navegação deve abrir o Clima.
     */
    suspend fun prepararAberturaDoClima(propriedadeId: String?): Boolean {
        val aceitePendente = precisaNovoAceite.first { it != null }
        if (!logado.value || aceitePendente == true) return false
        if (propriedadeId != null) {
            // Banco travado: ignora o pedido em vez de fechar o app.
            tentar { propriedades.buscarPorId(propriedadeId) }.getOrNull() ?: return false
            selecao.selecionar(propriedadeId)
        }
        return true
    }

    fun aoDispensarOfertaDeBloqueio() = conta.esquecerContaNova()

    /** Depois de a pessoa confirmar a digital ou o PIN no prompt. */
    fun aoLigarBloqueioPelaOferta() {
        conta.esquecerContaNova()
        bloqueio.definir(true)
    }

    /** Opção de senha na tela de bloqueio: encerra a sessão e leva ao login. */
    fun aoUsarSenha() = conta.sair()

    fun aoIrParaSegundoPlano() {
        foiParaSegundoPlanoEm = relogio.milissegundos()
    }

    /** Rebloqueia se o app ficou em segundo plano por [TEMPO_PARA_TRAVAR_MS] ou mais. */
    fun aoVoltarParaPrimeiroPlano() {
        val saiu = foiParaSegundoPlanoEm ?: return
        foiParaSegundoPlanoEm = null
        if (relogio.milissegundos() - saiu >= TEMPO_PARA_TRAVAR_MS) desbloqueadoNestaSessao.value = false
    }

    companion object {
        const val TEMPO_PARA_TRAVAR_MS = 2 * 60_000L
    }
}
