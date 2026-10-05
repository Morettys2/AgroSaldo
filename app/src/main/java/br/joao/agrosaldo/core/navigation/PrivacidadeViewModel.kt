package br.joao.agrosaldo.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.domain.repository.PrivacidadeRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Liga o "olho" do Início às telas que mostram dinheiro.
 *
 * Fica no esqueleto do app (e não no Início) porque o efeito vale para
 * todas as abas: esconder no Início esconde também em Lançamentos e no
 * Histórico.
 */
class PrivacidadeViewModel(private val privacidade: PrivacidadeRepository) : ViewModel() {

    val valoresOcultos: StateFlow<Boolean> = privacidade.valoresOcultos

    fun aoAlternar() = privacidade.definir(!privacidade.valoresOcultos.value)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PrivacidadeViewModel(Repositorios.privacidade) }
        }
    }
}
