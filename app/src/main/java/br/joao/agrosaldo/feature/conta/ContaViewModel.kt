package br.joao.agrosaldo.feature.conta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.formatarDataHoraBr
import br.joao.agrosaldo.core.util.formatarEspera
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.domain.conta.ResultadoExclusao
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

data class ContaUiState(
    val carregando: Boolean = true,
    val nome: String = "",
    val email: String = "",
    val iniciais: String = "",
    /** "30/09/2026 às 19:40" — quando a pessoa aceitou o termo da LGPD. */
    val consentimentoEm: String = "",
    val versaoTermo: Int = 0,
    val quantidadePropriedades: Int = 0,
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
 * ViewModel da tela 19 — Conta.
 *
 * Mostra quem está conectado e o que o app guarda sobre essa pessoa, e
 * oferece os dois botões que a LGPD exige que existam: sair e apagar tudo.
 */
class ContaViewModel(
    private val conta: ContaService,
    private val propriedadeRepository: PropriedadeRepository,
    private val zona: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _exclusao = MutableStateFlow(EstadoExclusao())

    private val _uiState = MutableStateFlow(ContaUiState())
    val uiState: StateFlow<ContaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                conta.observarUsuarioLogado(),
                propriedadeRepository.observarTodas(),
                _exclusao
            ) { usuario, propriedades, exclusao ->
                if (usuario == null) {
                    // Acontece por um instante depois de sair ou excluir,
                    // antes de a navegação trocar de tela.
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
                        excluindo = exclusao.excluindo,
                        erroExclusao = exclusao.erro
                    )
                }
            }.collect { estado -> _uiState.value = estado }
        }
    }

    fun aoSair() = conta.sair()

    fun aoExcluirConta(senha: String) {
        if (_exclusao.value.excluindo) return
        if (senha.isEmpty()) {
            _exclusao.value = EstadoExclusao(erro = "Digite sua senha para confirmar.")
            return
        }

        _exclusao.value = EstadoExclusao(excluindo = true)
        viewModelScope.launch {
            _exclusao.value = when (val resultado = conta.excluirConta(senha)) {
                // A sessão acabou; a navegação leva para o login.
                ResultadoExclusao.Sucesso -> EstadoExclusao()
                ResultadoExclusao.SenhaIncorreta -> EstadoExclusao(erro = "Senha incorreta.")
                is ResultadoExclusao.Bloqueado -> EstadoExclusao(
                    erro = "Muitas tentativas. Tente de novo em ${formatarEspera(resultado.segundosRestantes)}."
                )
                ResultadoExclusao.SemSessao -> EstadoExclusao()
            }
        }
    }

    /** Ao fechar o diálogo de exclusão, o erro antigo não deve reaparecer na próxima vez. */
    fun aoCancelarExclusao() = _exclusao.update { EstadoExclusao() }

    private data class EstadoExclusao(val excluindo: Boolean = false, val erro: String? = null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ContaViewModel(Repositorios.conta, Repositorios.propriedades) }
        }
    }
}
