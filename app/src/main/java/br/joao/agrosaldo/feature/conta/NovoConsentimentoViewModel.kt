package br.joao.agrosaldo.feature.conta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.tentar
import br.joao.agrosaldo.domain.conta.ContaService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NovoConsentimentoUiState(val processando: Boolean = false, val erro: String? = null)

/**
 * Reaceite após mudança da política. Aceitar grava a versão nova e a navegação
 * segue ao Início; recusar encerra a sessão, pois sem aceite o tratamento não é permitido.
 */
@HiltViewModel
class NovoConsentimentoViewModel @Inject constructor(private val conta: ContaService) : ViewModel() {

    private val _uiState = MutableStateFlow(NovoConsentimentoUiState())
    val uiState: StateFlow<NovoConsentimentoUiState> = _uiState.asStateFlow()

    fun aoAceitar() {
        if (_uiState.value.processando) return
        _uiState.update { it.copy(processando = true, erro = null) }
        viewModelScope.launch {
            val gravou = tentar { conta.registrarNovoAceite() }.getOrElse { erro ->
                _uiState.update {
                    it.copy(processando = false, erro = erro.mensagemParaUsuario("Não foi possível registrar o aceite. Tente de novo."))
                }
                return@launch
            }
            _uiState.update {
                if (gravou) {
                    it.copy(processando = false)
                } else {
                    it.copy(processando = false, erro = "Não foi possível registrar o aceite. Entre de novo.")
                }
            }
        }
    }

    fun aoRecusar() = conta.sair()
}
