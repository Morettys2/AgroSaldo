package br.joao.agrosaldo.data.local

/**
 * "Tudo ou nada" no banco: ou todas as operações do bloco acontecem, ou
 * nenhuma. Excluir uma propriedade apaga ela e os lançamentos dela — se o
 * celular desligar no meio, não pode sobrar metade.
 *
 * É uma interface, e não o `withTransaction` do Room direto, para os
 * repositórios continuarem testáveis no computador: nos testes entra a
 * [DIRETA], que só executa o bloco.
 */
interface Transacao {
    suspend fun <R> executar(bloco: suspend () -> R): R

    companion object {
        /** Sem banco de verdade (testes): só executa. */
        val DIRETA: Transacao = object : Transacao {
            override suspend fun <R> executar(bloco: suspend () -> R): R = bloco()
        }
    }
}
