package br.joao.agrosaldo.data.sessao

import br.joao.agrosaldo.domain.repository.BloqueioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fake em memória para testes. */
class BloqueioMemoria(inicial: Boolean = false) : BloqueioRepository {
    private val _ativo = MutableStateFlow(inicial)
    override val ativo: StateFlow<Boolean> = _ativo.asStateFlow()
    override fun definir(ativo: Boolean) {
        _ativo.value = ativo
    }
}
