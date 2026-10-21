package br.joao.agrosaldo.data.local

/**
 * Executa o bloco sem transação. Só para testes JVM, onde não há banco de verdade;
 * fica em testFixtures para não ir no APK (em produção é sempre a [TransacaoRoom]).
 */
object TransacaoDireta : Transacao {
    override suspend fun <R> executar(bloco: suspend () -> R): R = bloco()
}
