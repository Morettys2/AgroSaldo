package br.joao.agrosaldo.feature.conta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.tentar
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.domain.conta.RequisitosSenha
import br.joao.agrosaldo.domain.conta.ResultadoCadastro
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CadastroUiState(
    val nome: String = "",
    val email: String = "",
    val senha: String = "",
    val confirmacao: String = "",
    val erroNome: String? = null,
    val erroEmail: String? = null,
    val erroSenha: String? = null,
    val erroConfirmacao: String? = null,
    /** Erro não associado a um campo; exibido na tela de consentimento. */
    val erroGeral: String? = null,
    val verificando: Boolean = false,
    val criando: Boolean = false,
    /** Evento de navegação para o consentimento; consumido por [CadastroViewModel.aoAbrirConsentimento]. */
    val irParaConsentimento: Boolean = false
) {
    val requisitos: RequisitosSenha
        get() = RegrasDeConta.requisitosDaSenha(senha, email, nome)
}

/**
 * Compartilhado entre o formulário de cadastro e a tela de consentimento LGPD.
 * O formulário só coleta e valida; a conta é gravada apenas no aceite do termo,
 * então voltar ou recusar não persiste nada (consentimento antes do tratamento).
 *
 * A rota de consentimento na AppNavegacao obtém esta instância a partir da
 * back stack entry do cadastro.
 */
@HiltViewModel
class CadastroViewModel @Inject constructor(private val conta: ContaService) : ViewModel() {

    private val _uiState = MutableStateFlow(CadastroUiState())
    val uiState: StateFlow<CadastroUiState> = _uiState.asStateFlow()

    fun aoMudarNome(v: String) = _uiState.update { it.copy(nome = v, erroNome = null) }
    fun aoMudarEmail(v: String) = _uiState.update { it.copy(email = v.trim(), erroEmail = null) }
    fun aoMudarSenha(v: String) = _uiState.update { it.copy(senha = v, erroSenha = null) }
    fun aoMudarConfirmacao(v: String) = _uiState.update { it.copy(confirmacao = v, erroConfirmacao = null) }

    fun aoContinuar() {
        val estado = _uiState.value
        if (estado.verificando) return

        val validado = validar(estado)
        if (validado != null) {
            _uiState.value = validado
            return
        }

        _uiState.update { it.copy(verificando = true) }
        viewModelScope.launch {
            // Checa duplicidade antes do consentimento para não falhar só após o aceite.
            val emUso = tentar { conta.emailEmUso(estado.email) }.getOrElse { erro ->
                _uiState.update {
                    it.copy(verificando = false, erroEmail = erro.mensagemParaUsuario("Não foi possível conferir o e-mail. Tente de novo."))
                }
                return@launch
            }
            _uiState.update {
                if (emUso) {
                    it.copy(verificando = false, erroEmail = "Já existe uma conta com este e-mail.")
                } else {
                    it.copy(verificando = false, irParaConsentimento = true)
                }
            }
        }
    }

    /** Consome o evento [CadastroUiState.irParaConsentimento]. */
    fun aoAbrirConsentimento() = _uiState.update { it.copy(irParaConsentimento = false) }

    /** Cria a conta com o termo aceito; em sucesso o [ContaService] já inicia a sessão. */
    fun aoAceitarTermo() {
        val estado = _uiState.value
        if (estado.criando) return

        // Após morte do processo o estado não é restaurado (sem SavedStateHandle)
        // e o formulário chega vazio aqui.
        if (validar(estado) != null) {
            _uiState.update { it.copy(erroGeral = "Os dados do cadastro se perderam. Volte e preencha de novo.") }
            return
        }

        _uiState.update { it.copy(criando = true, erroGeral = null) }
        viewModelScope.launch {
            val resultado = tentar {
                conta.cadastrar(nome = estado.nome, email = estado.email, senha = estado.senha, aceitouTermo = true)
            }.getOrElse { erro ->
                _uiState.update {
                    it.copy(criando = false, erroGeral = erro.mensagemParaUsuario("Não foi possível criar a conta. Tente de novo."))
                }
                return@launch
            }
            _uiState.update {
                when (resultado) {
                    // Sessão já iniciada pelo ContaService; a navegação segue ao início.
                    is ResultadoCadastro.Sucesso -> it.copy(criando = false, senha = "", confirmacao = "")
                    ResultadoCadastro.EmailJaCadastrado ->
                        it.copy(criando = false, erroGeral = "Já existe uma conta com este e-mail.")
                    ResultadoCadastro.TermoNaoAceito ->
                        it.copy(criando = false, erroGeral = "É preciso aceitar o termo para criar a conta.")
                    is ResultadoCadastro.DadosInvalidos ->
                        it.copy(criando = false, erroGeral = resultado.motivo)
                }
            }
        }
    }

    /** Retorna o estado com os erros preenchidos, ou null se for válido. */
    private fun validar(estado: CadastroUiState): CadastroUiState? {
        var houveErro = false
        var novo = estado

        if (!RegrasDeConta.nomeValido(estado.nome)) {
            novo = novo.copy(erroNome = "Informe seu nome.")
            houveErro = true
        }
        if (!RegrasDeConta.emailValido(estado.email)) {
            novo = novo.copy(erroEmail = "Informe um e-mail válido, como nome@exemplo.com.")
            houveErro = true
        }
        if (!RegrasDeConta.senhaValida(estado.senha, estado.email, estado.nome)) {
            novo = novo.copy(erroSenha = "A senha não cumpre os requisitos abaixo.")
            houveErro = true
        }
        if (estado.confirmacao != estado.senha) {
            novo = novo.copy(erroConfirmacao = "As senhas não são iguais.")
            houveErro = true
        }

        return if (houveErro) novo else null
    }
}
