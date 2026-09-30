package br.joao.agrosaldo.feature.conta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.domain.conta.RequisitosSenha
import br.joao.agrosaldo.domain.conta.ResultadoCadastro
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
    /** Erro que não é de um campo só — aparece na tela de consentimento. */
    val erroGeral: String? = null,
    val verificando: Boolean = false,
    val criando: Boolean = false,
    /** Evento: o formulário passou e a navegação deve abrir o consentimento. */
    val irParaConsentimento: Boolean = false
) {
    /** Recalculado a cada tecla: a tela marca cada requisito assim que ele é cumprido. */
    val requisitos: RequisitosSenha
        get() = RegrasDeConta.requisitosDaSenha(senha, email, nome)
}

/**
 * ViewModel das telas 03 (Criar conta) e 04 (Consentimento LGPD).
 *
 * As duas telas compartilham este MESMO ViewModel. O formulário preenche
 * os dados; o consentimento é que grava. Assim nada é salvo antes do aceite:
 * se a pessoa voltar ou recusar, a conta simplesmente não existe — que é o
 * que a LGPD pede (consentimento antes do tratamento do dado).
 *
 * Como duas telas usam um ViewModel só: veja AppNavegacao, na rota do
 * consentimento. Ela pede o ViewModel "emprestado" da tela de cadastro,
 * que continua na pilha por baixo.
 */
class CadastroViewModel(
    private val conta: ContaService
) : ViewModel() {

    private val _uiState = MutableStateFlow(CadastroUiState())
    val uiState: StateFlow<CadastroUiState> = _uiState.asStateFlow()

    fun aoMudarNome(v: String) = _uiState.update { it.copy(nome = v, erroNome = null) }
    fun aoMudarEmail(v: String) = _uiState.update { it.copy(email = v.trim(), erroEmail = null) }
    fun aoMudarSenha(v: String) = _uiState.update { it.copy(senha = v, erroSenha = null) }
    fun aoMudarConfirmacao(v: String) = _uiState.update { it.copy(confirmacao = v, erroConfirmacao = null) }

    /** Botão "Continuar" do formulário. */
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
            // Conferir o e-mail agora, e não só no fim, poupa a pessoa de ler
            // o termo inteiro para descobrir que já tinha conta.
            val emUso = conta.emailEmUso(estado.email)
            _uiState.update {
                if (emUso) {
                    it.copy(verificando = false, erroEmail = "Já existe uma conta com este e-mail.")
                } else {
                    it.copy(verificando = false, irParaConsentimento = true)
                }
            }
        }
    }

    /** A navegação chama isto depois de abrir o consentimento, para o evento não disparar de novo. */
    fun aoAbrirConsentimento() = _uiState.update { it.copy(irParaConsentimento = false) }

    /** Botão "Aceito e quero continuar". É aqui que a conta nasce. */
    fun aoAceitarTermo() {
        val estado = _uiState.value
        if (estado.criando) return

        // Se o Android fechou o app com a tela de consentimento aberta, ao
        // voltar o formulário está vazio. Melhor pedir de novo do que falhar.
        if (validar(estado) != null) {
            _uiState.update { it.copy(erroGeral = "Os dados do cadastro se perderam. Volte e preencha de novo.") }
            return
        }

        _uiState.update { it.copy(criando = true, erroGeral = null) }
        viewModelScope.launch {
            val resultado = conta.cadastrar(
                nome = estado.nome,
                email = estado.email,
                senha = estado.senha,
                aceitouTermo = true
            )
            _uiState.update {
                when (resultado) {
                    // A sessão já foi iniciada pelo ContaService; a navegação
                    // percebe e abre o início. Aqui só limpamos as senhas.
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

    /** Devolve o estado com os erros marcados, ou null se estiver tudo certo. */
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { CadastroViewModel(Repositorios.conta) }
        }
    }
}
