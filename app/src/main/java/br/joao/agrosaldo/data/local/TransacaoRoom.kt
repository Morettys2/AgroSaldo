package br.joao.agrosaldo.data.local

import androidx.room.withTransaction

/** [Transacao] sobre o `withTransaction` do Room: tudo ou nada, no banco real. */
class TransacaoRoom(private val banco: AgroSaldoDatabase) : Transacao {
    override suspend fun <R> executar(bloco: suspend () -> R): R = banco.withTransaction { bloco() }
}
