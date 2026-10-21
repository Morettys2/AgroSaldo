package br.joao.agrosaldo.domain

import br.joao.agrosaldo.domain.model.InstanteMonotonico

/**
 * Relógio monotônico para testes que não precisam da comparação entre processos: sem boot
 * conhecido. Em produção é sempre o `RelogioMonotonicoDoAndroid`.
 */
object RelogioMonotonicoIndisponivel : RelogioMonotonico {
    private const val NANOS_POR_MS = 1_000_000L

    override fun milissegundos(): Long = System.nanoTime() / NANOS_POR_MS

    override fun instante(): InstanteMonotonico? = null
}
