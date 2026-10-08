package br.joao.agrosaldo.feature.conta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.formatarEspera
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.domain.conta.RequisitosSenha
import br.joao.agrosaldo.domain.conta.ResultadoAlteracaoSenha
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlterarSenhaUiState(
    val senhaAtual: String = "",
    val novaSenha: String = "",
    val confirmacao: String = "",
    val erroSenhaAtual: String? = null,
    val erroNovaSenha: String? = null,
    val erroConfirmacao: String? = null,
    val salvando: Boolean = false,
    val concluido: Boolean = false,
    /** Usados na validação: a nova senha não pode conter nome nem e-mail. */
    val email: String = "",
    val nome: String = ""
) {
    val requisitos: RequisitosSenha
        get() = RegrasDeConta.requisitosDaSenha(novaSenha, email, nome)
}

/**
 * Troca de senha do usuário logado. Exige a senha atual para que alguém com o
 * aparelho desbloqueado não consiga tomar a conta.
 */
class AlterarSenhaViewModel(
    private val conta: ContaService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlterarSenhaUiState())
    val uiState: StateFlow<AlterarSenhaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val usuario = conta.observarUsuarioLogado().first()
            if (usuario != null) {
                _uiState.update { it.copy(email = usuario.email, nome = usuario.nome) }
            }
        }
    }

    fun aoMudarSenhaAtual(v: String) = _uiState.update { it.copy(senhaAtual = v, erroSenhaAtual = null) }
    fun aoMudarNovaSenha(v: String) = _uiState.update { it.copy(novaSenha = v, erroNovaSenha = null) }
    fun aoMudarConfirmacao(v: String) = _uiState.update { it.copy(confirmacao = v, erroConfirmacao = null) }

    fun aoSalvar() {
        val estado = _uiState.value
        if (estado.salvando) return

        var houveErro = false
        var novo = estado
        if (estado.senhaAtual.isEmpty()) {
            novo = novo.copy(erroSenhaAtual = "Digite a senha que você usa hoje.")
            houveErro = true
        }
        if (!RegrasDeConta.senhaValida(estado.novaSenha, estado.email, estado.nome)) {
            novo = novo.copy(erroNovaSenha = "A nova senha não cumpre os requisitos abaixo.")
            houveErro = true
        }
        if (estado.confirmacao != estado.novaSenha) {
            novo = novo.copy(erroConfirmacao = "As senhas não são iguais.")
            houveErro = true
        }
        if (houveErro) {
            _uiState.value = novo
            return
        }

        _uiState.update { it.copy(salvando = true) }
        viewModelScope.launch {
            val resultado = conta.alterarSenha(estado.senhaAtual, estado.novaSenha)
            _uiState.update {
                when (resultado) {
                    ResultadoAlteracaoSenha.Sucesso ->
                        it.copy(salvando = false, concluido = true, senhaAtual = "", novaSenha = "", confirmacao = "")
                    ResultadoAlteracaoSenha.SenhaAtualIncorreta ->
                        it.copy(salvando = false, senhaAtual = "", erroSenhaAtual = "Senha atual incorreta.")
                    ResultadoAlteracaoSenha.NovaIgualAAtual ->
                        it.copy(salvando = false, erroNovaSenha = "A nova senha precisa ser diferente da atual.")
                    ResultadoAlteracaoSenha.NovaNaoCumpreRequisitos ->
                        it.copy(salvando = false, erroNovaSenha = "A nova senha não cumpre os requisitos abaixo.")
                    is ResultadoAlteracaoSenha.Bloqueado -> it.copy(
                        salvando = false,
                        senhaAtual = "",
                        erroSenhaAtual = "Muitas tentativas. Tente de novo em " +
                            "${formatarEspera(resultado.segundosRestantes)}."
                    )
                    // Sessão encerrada durante a operação; a navegação já redireciona ao login.
                    ResultadoAlteracaoSenha.SemSessao -> it.copy(salvando = false)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AlterarSenhaViewModel(Repositorios.conta) }
        }
    }
}
