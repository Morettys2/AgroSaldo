package br.joao.agrosaldo.domain.repository

import kotlinx.coroutines.flow.Flow

/** O que as telas precisam saber de quem está conectado, sem expor a conta inteira. */
interface UsuarioLogadoRepository {

    /** Nome do usuário conectado; null sem sessão ou enquanto a conta não foi lida. */
    fun observarNome(): Flow<String?>
}
