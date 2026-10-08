package br.joao.agrosaldo.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** Ocultação global de valores monetários na UI. Persistida entre sessões. */
interface PrivacidadeRepository {
    val valoresOcultos: StateFlow<Boolean>
    fun definir(ocultos: Boolean)
}
