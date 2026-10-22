package br.joao.agrosaldo.data.local

import androidx.room.withTransaction

/** Implementação de [Transacao] com o `withTransaction` do Room: o bloco inteiro roda numa transação do SQLite. */
class TransacaoRoom(private val banco: AgroSaldoDatabase) : Transacao {
    override suspend fun <R> executar(bloco: suspend () -> R): R = banco.withTransaction { bloco() }
}
