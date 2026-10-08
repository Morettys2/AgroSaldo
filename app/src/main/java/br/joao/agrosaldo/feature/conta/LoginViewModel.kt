package br.joao.agrosaldo.feature.conta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.formatarEspera
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.conta.ResultadoLogin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val senha: String = "",
    val erro: String? = null,
    val entrando: Boolean = false
)

/**
 * Login (RF02). Não navega: o sucesso abre a sessão no [ContaService], que é a
 * fonte única de "logado ou não" observada pela navegação.
 */
class LoginViewModel(
    private val conta: ContaService
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun aoMudarEmail(v: String) = _uiState.update { it.copy(email = v.trim(), erro = null) }
    fun aoMudarSenha(v: String) = _uiState.update { it.copy(senha = v, erro = null) }

    fun aoEntrar() {
        val estado = _uiState.value
        if (estado.entrando) return

        if (estado.email.isBlank() || estado.senha.isEmpty()) {
            _uiState.update { it.copy(erro = "Informe o e-mail e a senha.") }
            return
        }

        _uiState.update { it.copy(entrando = true, erro = null) }
        viewModelScope.launch {
            when (val resultado = conta.entrar(estado.email, estado.senha)) {
                // Remove a senha do estado assim que não é mais necessária.
                is ResultadoLogin.Sucesso -> _uiState.update { it.copy(entrando = false, senha = "") }

                // Mensagem única para e-mail inexistente e senha errada (evita enumeração de contas).
                ResultadoLogin.CredenciaisInvalidas -> _uiState.update {
                    it.copy(entrando = false, senha = "", erro = "E-mail ou senha incorretos.")
                }

                is ResultadoLogin.Bloqueado -> _uiState.update {
                    it.copy(
                        entrando = false,
                        senha = "",
                        erro = "Muitas tentativas. Tente de novo em ${formatarEspera(resultado.segundosRestantes)}."
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(Repositorios.conta) }
        }
    }
}
