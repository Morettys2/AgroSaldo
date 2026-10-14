package br.joao.agrosaldo.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.di.dependencias
import br.joao.agrosaldo.domain.repository.PrivacidadeRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado global de ocultação de valores monetários. Escopo da navegação (e não do
 * Início) porque o efeito vale para todas as telas que exibem valores.
 */
class PrivacidadeViewModel(private val privacidade: PrivacidadeRepository) : ViewModel() {

    val valoresOcultos: StateFlow<Boolean> = privacidade.valoresOcultos

    fun aoAlternar() = privacidade.definir(!privacidade.valoresOcultos.value)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PrivacidadeViewModel(dependencias.privacidade) }
        }
    }
}
