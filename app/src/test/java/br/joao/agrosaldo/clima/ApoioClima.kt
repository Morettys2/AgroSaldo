package br.joao.agrosaldo.clima

import br.joao.agrosaldo.domain.clima.CondicaoTempo
import br.joao.agrosaldo.domain.clima.Previsao
import br.joao.agrosaldo.domain.clima.PrevisaoDia
import br.joao.agrosaldo.domain.clima.PrevisaoHora
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Fixtures dos testes de clima.
 *
 * [AGORA] = 01/10/2026 14:40 em Brasília (UTC-3), 17:40 UTC. A janela de 12 h vai de 14:00
 * até 01:59 do dia 2 (horário local).
 */
object ApoioClima {

    val AGORA: Instant = Instant.parse("2026-10-01T17:40:00Z")
    const val FUSO_BRASILIA = -3 * 3600
    val MEIA_NOITE: LocalDateTime = LocalDateTime.of(2026, 10, 1, 0, 0)

    /** Previsão com 48 h a partir de 01/10 00:00 local; horas fora de [chuvaPorHora] ficam secas (5%, 0 mm). */
    fun previsao(
        chuvaPorHora: Map<Int, Pair<Int, Double>> = emptyMap(),
        obtidaEm: Instant = AGORA,
        latitude: Double = -23.7,
        longitude: Double = -48.9
    ): Previsao = Previsao(
        latitude = latitude,
        longitude = longitude,
        obtidaEm = obtidaEm,
        fusoSegundos = FUSO_BRASILIA,
        temperaturaAtual = 26.4,
        condicaoAtual = CondicaoTempo.NUBLADO,
        dias = (0 until 7).map { d ->
            PrevisaoDia(
                data = LocalDate.of(2026, 10, 1).plusDays(d.toLong()),
                condicao = if (d == 0) CondicaoTempo.CHUVA else CondicaoTempo.CEU_LIMPO,
                temperaturaMaxima = 31.0 - d,
                temperaturaMinima = 18.0,
                chuvaMm = if (d == 0) 12.0 else 0.0,
                probabilidadeChuva = if (d == 0) 80 else 10
            )
        },
        horas = (0 until 48).map { h ->
            val (probabilidade, chuva) = chuvaPorHora[h] ?: (5 to 0.0)
            PrevisaoHora(MEIA_NOITE.plusHours(h.toLong()), probabilidade, chuva)
        }
    )

    /** JSON no formato da resposta real da Open-Meteo (7 dias; por padrão 48 horas secas). */
    fun jsonOpenMeteo(
        probabilidades: List<Int?> = List(48) { 5 },
        chuvas: List<Double?> = List(48) { 0.0 },
        latitude: Double = -23.7,
        longitude: Double = -48.9
    ): String {
        val horas = (0 until probabilidades.size).joinToString(",") { "\"${MEIA_NOITE.plusHours(it.toLong())}\"" }
        val dias = (0 until 7).joinToString(",") { "\"${LocalDate.of(2026, 10, 1).plusDays(it.toLong())}\"" }
        return """
        {"latitude":$latitude,"longitude":$longitude,"generationtime_ms":0.3,
         "utc_offset_seconds":$FUSO_BRASILIA,"timezone":"America/Sao_Paulo","timezone_abbreviation":"GMT-3",
         "elevation":812.0,
         "current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C","weather_code":"wmo code"},
         "current":{"time":"2026-10-01T14:30","interval":900,"temperature_2m":26.4,"weather_code":3},
         "hourly_units":{"time":"iso8601","precipitation_probability":"%","precipitation":"mm"},
         "hourly":{"time":[$horas],
                   "precipitation_probability":[${probabilidades.joinToString(",") { it?.toString() ?: "null" }}],
                   "precipitation":[${chuvas.joinToString(",") { it?.toString() ?: "null" }}]},
         "daily_units":{"time":"iso8601"},
         "daily":{"time":[$dias],
                  "weather_code":[61,3,0,1,2,80,95],
                  "temperature_2m_max":[31.2,29.8,30.0,32.1,31.0,28.4,null],
                  "temperature_2m_min":[18.4,17.9,18.0,19.2,20.0,19.1,null],
                  "precipitation_sum":[12.3,0.0,0.0,0.0,0.4,6.0,null],
                  "precipitation_probability_max":[80,20,5,0,15,65,null]}}
        """.trimIndent()
    }
}
