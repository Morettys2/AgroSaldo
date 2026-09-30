package br.joao.agrosaldo.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.domain.conta.ContaService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Diz à navegação se há alguém conectado.
 *
 * É a única fonte de verdade de "logado ou não" para o app inteiro. Login,
 * cadastro, sair e excluir conta só mexem na sessão; a navegação observa
 * este [logado] e troca de tela sozinha. Nenhuma tela precisa saber para
 * onde ir depois de entrar ou sair.
 */
class SessaoViewModel(
    private val conta: ContaService
) : ViewModel() {

    /**
     * stateIn transforma o Flow em StateFlow, que sempre tem um valor atual.
     * O valor inicial vem direto da sessão gravada, então a primeira tela já
     * nasce certa — o app não mostra o login por um instante antes do início.
     */
    val logado: StateFlow<Boolean> = conta.usuarioLogadoId
        .map { id -> id != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, conta.estaLogado())

    init {
        viewModelScope.launch { conta.validarSessao() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SessaoViewModel(Repositorios.conta) }
        }
    }
}
