package br.joao.agrosaldo.feature.conta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.domain.conta.ContaService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NovoConsentimentoUiState(
    val processando: Boolean = false,
    val erro: String? = null
)

/**
 * Reaceite após mudança da política. Aceitar grava a versão nova e a navegação
 * segue ao Início; recusar encerra a sessão, pois sem aceite o tratamento não é permitido.
 */
class NovoConsentimentoViewModel(
    private val conta: ContaService
) : ViewModel() {

    private val _uiState = MutableStateFlow(NovoConsentimentoUiState())
    val uiState: StateFlow<NovoConsentimentoUiState> = _uiState.asStateFlow()

    fun aoAceitar() {
        if (_uiState.value.processando) return
        _uiState.update { it.copy(processando = true, erro = null) }
        viewModelScope.launch {
            val gravou = conta.registrarNovoAceite()
            _uiState.update {
                if (gravou) it.copy(processando = false)
                else it.copy(processando = false, erro = "Não foi possível registrar o aceite. Entre de novo.")
            }
        }
    }

    fun aoRecusar() = conta.sair()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { NovoConsentimentoViewModel(Repositorios.conta) }
        }
    }
}
