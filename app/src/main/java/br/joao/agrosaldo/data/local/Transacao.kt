package br.joao.agrosaldo.data.local

/**
 * Executa o bloco atomicamente (tudo ou nada). Abstrai o `withTransaction` do Room
 * para que os repositórios sejam testáveis na JVM (com a `TransacaoDireta` dos testes).
 */
interface Transacao {
    suspend fun <R> executar(bloco: suspend () -> R): R
}
