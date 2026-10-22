package br.joao.agrosaldo.data.local

/**
 * Executa um bloco de forma atômica (tudo ou nada). Abstrai o `withTransaction` do Room: os
 * repositórios dependem desta interface, e não do banco.
 */
interface Transacao {
    suspend fun <R> executar(bloco: suspend () -> R): R
}
