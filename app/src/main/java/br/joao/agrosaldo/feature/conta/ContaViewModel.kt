package br.joao.agrosaldo.feature.conta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.formatarDataHoraBr
import br.joao.agrosaldo.core.util.formatarEspera
import br.joao.agrosaldo.di.dependencias
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.domain.conta.ResultadoExclusao
import br.joao.agrosaldo.domain.repository.BloqueioRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContaUiState(
    val carregando: Boolean = true,
    val nome: String = "",
    val email: String = "",
    val iniciais: String = "",
    /** Data/hora do aceite do termo LGPD, já formatada ("30/09/2026 às 19:40"). */
    val consentimentoEm: String = "",
    val versaoTermo: Int = 0,
    val quantidadePropriedades: Int = 0,
    /** Exige biometria ou credencial do aparelho ao abrir o app. */
    val bloqueioAtivo: Boolean = false,
    val excluindo: Boolean = false,
    val erroExclusao: String? = null
) {
    val resumoPropriedades: String
        get() = when (quantidadePropriedades) {
            0 -> "Nenhuma cadastrada"
            1 -> "1 cadastrada"
            else -> "$quantidadePropriedades cadastradas"
        }
}

/**
 * Tela de conta: dados do usuário logado, consentimento LGPD e bloqueio do app.
 * Expõe as ações exigidas pela LGPD: sair e excluir a conta com todos os dados.
 */
class ContaViewModel(
    private val conta: ContaService,
    private val propriedadeRepository: PropriedadeRepository,
    private val bloqueio: BloqueioRepository,
    private val zona: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val exclusaoEmAndamento = MutableStateFlow(EstadoExclusao())

    private val _uiState = MutableStateFlow(ContaUiState())
    val uiState: StateFlow<ContaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                conta.observarUsuarioLogado(),
                propriedadeRepository.observarTodas(),
                bloqueio.ativo,
                exclusaoEmAndamento
            ) { usuario, propriedades, bloqueioAtivo, exclusao ->
                if (usuario == null) {
                    // Transitório após sair/excluir, até a navegação trocar de tela.
                    ContaUiState(carregando = false)
                } else {
                    ContaUiState(
                        carregando = false,
                        nome = usuario.nome,
                        email = usuario.email,
                        iniciais = RegrasDeConta.iniciais(usuario.nome),
                        consentimentoEm = usuario.consentimentoEm.formatarDataHoraBr(zona),
                        versaoTermo = usuario.versaoTermo,
                        quantidadePropriedades = propriedades.size,
                        bloqueioAtivo = bloqueioAtivo,
                        excluindo = exclusao.excluindo,
                        erroExclusao = exclusao.erro
                    )
                }
            }.collect { estado -> _uiState.value = estado }
        }
    }

    fun aoSair() = conta.sair()

    /**
     * Persiste a preferência de bloqueio. Só deve ser chamado após a tela confirmar
     * a autenticação (o prompt biométrico exige FragmentActivity), ao ligar e ao desligar.
     */
    fun aoDefinirBloqueio(ativo: Boolean) = bloqueio.definir(ativo)

    fun aoExcluirConta(senha: String) {
        if (exclusaoEmAndamento.value.excluindo) return
        if (senha.isEmpty()) {
            exclusaoEmAndamento.value = EstadoExclusao(erro = "Digite sua senha para confirmar.")
            return
        }

        exclusaoEmAndamento.value = EstadoExclusao(excluindo = true)
        viewModelScope.launch {
            exclusaoEmAndamento.value = when (val resultado = conta.excluirConta(senha)) {
                // Sessão encerrada; a navegação leva ao login.
                ResultadoExclusao.Sucesso -> EstadoExclusao()
                ResultadoExclusao.SenhaIncorreta -> EstadoExclusao(erro = "Senha incorreta.")
                is ResultadoExclusao.Bloqueado -> EstadoExclusao(
                    erro = "Muitas tentativas. Tente de novo em ${formatarEspera(resultado.segundosRestantes)}."
                )
                ResultadoExclusao.SemSessao -> EstadoExclusao()
            }
        }
    }

    /** Limpa o erro para não reaparecer na próxima abertura do diálogo. */
    fun aoCancelarExclusao() = exclusaoEmAndamento.update { EstadoExclusao() }

    private data class EstadoExclusao(val excluindo: Boolean = false, val erro: String? = null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ContaViewModel(dependencias.conta, dependencias.propriedades, dependencias.bloqueio) }
        }
    }
}
