package br.joao.agrosaldo.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Quem está conectado agora.
 *
 * É um StateFlow porque várias partes do app reagem à mesma informação: a
 * navegação troca de tela quando ela muda, e os repositórios passam a
 * mostrar só os dados do novo usuário.
 */
interface SessaoRepository {

    /** Id do usuário conectado, ou null se ninguém estiver. */
    val usuarioId: StateFlow<String?>

    fun iniciar(usuarioId: String)

    fun encerrar()
}
