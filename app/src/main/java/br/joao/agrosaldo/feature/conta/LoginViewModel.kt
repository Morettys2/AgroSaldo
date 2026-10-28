package br.joao.agrosaldo.feature.conta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.joao.agrosaldo.core.util.formatarEspera
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.tentar
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.domain.conta.ResultadoLogin
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(val email: String = "", val senha: String = "", val erro: String? = null, val entrando: Boolean = false)

/**
 * Login. Não navega: o sucesso abre a sessão no [ContaService], que é a
 * fonte única de "logado ou não" observada pela navegação.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(private val conta: ContaService) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Cortados no limite do domínio ao digitar ou colar.
    fun aoMudarEmail(v: String) = _uiState.update { it.copy(email = v.trim().take(RegrasDeConta.EMAIL_MAXIMO), erro = null) }
    fun aoMudarSenha(v: String) = _uiState.update { it.copy(senha = v.take(RegrasDeConta.SENHA_MAXIMO), erro = null) }

    fun aoEntrar() {
        val estado = _uiState.value
        if (estado.entrando) return

        if (estado.email.isBlank() || estado.senha.isEmpty()) {
            _uiState.update { it.copy(erro = "Informe o e-mail e a senha.") }
            return
        }

        _uiState.update { it.copy(entrando = true, erro = null) }
        viewModelScope.launch {
            val resultado = tentar { conta.entrar(estado.email, estado.senha) }.getOrElse { erro ->
                _uiState.update { it.copy(entrando = false, erro = erro.mensagemParaUsuario("Não foi possível entrar. Tente de novo.")) }
                return@launch
            }
            when (resultado) {
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
}
