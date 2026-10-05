package br.joao.agrosaldo.data.preferencias

import br.joao.agrosaldo.domain.repository.PrivacidadeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Versão para os testes: o mesmo contrato, sem gravar nada. */
class PrivacidadeMemoria(inicial: Boolean = false) : PrivacidadeRepository {
    private val _valoresOcultos = MutableStateFlow(inicial)
    override val valoresOcultos: StateFlow<Boolean> = _valoresOcultos.asStateFlow()

    override fun definir(ocultos: Boolean) {
        _valoresOcultos.value = ocultos
    }
}
