package br.joao.agrosaldo.data.sessao

import br.joao.agrosaldo.domain.repository.SessaoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sessão que some ao fechar o app. Usada nos testes. */
class SessaoMemoria(inicial: String? = null) : SessaoRepository {

    private val _usuarioId = MutableStateFlow(inicial)
    override val usuarioId: StateFlow<String?> = _usuarioId.asStateFlow()

    override fun iniciar(usuarioId: String) {
        _usuarioId.value = usuarioId
    }

    override fun encerrar() {
        _usuarioId.value = null
    }
}
