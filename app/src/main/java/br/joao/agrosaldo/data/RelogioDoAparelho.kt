package br.joao.agrosaldo.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * O relógio do app: hora e fuso do aparelho **no momento da leitura**. Um `Clock.systemDefaultZone()`
 * guardaria o fuso de quando foi criado, e quem viaja com o app aberto veria o dia errado.
 */
object RelogioDoAparelho : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()
}
