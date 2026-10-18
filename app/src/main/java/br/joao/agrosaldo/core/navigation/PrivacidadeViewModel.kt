package br.joao.agrosaldo.core.navigation

import androidx.lifecycle.ViewModel
import br.joao.agrosaldo.domain.repository.PrivacidadeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado global de ocultação de valores monetários. Escopo da navegação (e não do
 * Início) porque o efeito vale para todas as telas que exibem valores.
 */
@HiltViewModel
class PrivacidadeViewModel @Inject constructor(private val privacidade: PrivacidadeRepository) : ViewModel() {

    val valoresOcultos: StateFlow<Boolean> = privacidade.valoresOcultos

    fun aoAlternar() = privacidade.definir(!privacidade.valoresOcultos.value)
}
