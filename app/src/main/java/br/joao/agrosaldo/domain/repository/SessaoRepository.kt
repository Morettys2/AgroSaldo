package br.joao.agrosaldo.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Sessão local. A navegação e os repositórios observam [usuarioId] para
 * reagir a login/logout e filtrar dados pelo dono.
 */
interface SessaoRepository {

    /** Id do usuário conectado, ou null sem sessão. */
    val usuarioId: StateFlow<String?>

    fun iniciar(usuarioId: String)

    fun encerrar()
}
