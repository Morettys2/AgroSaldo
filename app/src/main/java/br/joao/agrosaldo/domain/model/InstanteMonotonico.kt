package br.joao.agrosaldo.domain.model

/**
 * Instante no relógio monotônico do aparelho (no Android, `SystemClock.elapsedRealtime`).
 *
 * Não muda quando o usuário altera data/hora do sistema, mas recomeça do zero a cada
 * reinício; por isso só é comparável com outro instante do mesmo [boot].
 *
 * @property boot identificador do boot (no Android, `Settings.Global.BOOT_COUNT`).
 * @property desdeOBootMs milissegundos desde o boot.
 */
data class InstanteMonotonico(val boot: Int, val desdeOBootMs: Long) {

    fun mais(ms: Long): InstanteMonotonico = copy(desdeOBootMs = desdeOBootMs + ms)
}
