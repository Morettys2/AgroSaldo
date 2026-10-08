package br.joao.agrosaldo.data.local

/**
 * Executa o bloco atomicamente (tudo ou nada). Abstrai o `withTransaction` do Room
 * para que os repositórios sejam testáveis na JVM com [DIRETA].
 */
interface Transacao {
    suspend fun <R> executar(bloco: suspend () -> R): R

    companion object {
        /** Executa o bloco sem transação (somente testes). */
        val DIRETA: Transacao = object : Transacao {
            override suspend fun <R> executar(bloco: suspend () -> R): R = bloco()
        }
    }
}
