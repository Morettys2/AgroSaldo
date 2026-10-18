package br.joao.agrosaldo.domain

import br.joao.agrosaldo.domain.model.InstanteMonotonico

/**
 * Relógio que não muda quando a pessoa altera a data e a hora do aparelho (no Android,
 * `SystemClock.elapsedRealtime`). Serve para medir duração: bloqueio por senhas erradas,
 * tempo em segundo plano e validade do "Desfazer".
 *
 * Para a hora do calendário use [java.time.Clock], que também é injetado.
 */
interface RelogioMonotonico {

    /** Milissegundos desde o boot. Só serve para subtrair de outra leitura do mesmo processo. */
    fun milissegundos(): Long

    /**
     * Leitura comparável com instantes gravados em disco por outros processos (inclui o boot),
     * ou null se o sistema não informa o boot.
     */
    fun instante(): InstanteMonotonico?
}

/** Para quem não precisa da comparação entre processos: sem boot conhecido. */
object RelogioMonotonicoIndisponivel : RelogioMonotonico {
    private const val NANOS_POR_MS = 1_000_000L

    override fun milissegundos(): Long = System.nanoTime() / NANOS_POR_MS

    override fun instante(): InstanteMonotonico? = null
}
