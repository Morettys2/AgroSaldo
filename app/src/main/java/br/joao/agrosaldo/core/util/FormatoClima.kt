package br.joao.agrosaldo.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** A partir daqui o volume aparece inteiro. */
private const val LIMITE_PARA_INTEIRO_MM = 10.0

/** Uma casa decimal = décimos de milímetro. */
private const val PASSOS_POR_MM = 10.0

private val BRASIL_CLIMA: Locale = Locale.forLanguageTag("pt-BR")
private val FORMATO_HORA_MINUTO: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** "6 mm", "2,5 mm", "12 mm": uma casa decimal abaixo de 10 mm, inteiro a partir daí. Negativo vira 0. */
fun Double.emMilimetros(): String {
    val valor = coerceAtLeast(0.0)
    if (valor >= LIMITE_PARA_INTEIRO_MM) return "${valor.roundToInt()} mm"
    val umaCasa = (valor * PASSOS_POR_MM).roundToInt() / PASSOS_POR_MM
    val texto = if (umaCasa % 1.0 == 0.0) umaCasa.toInt().toString() else umaCasa.toString().replace('.', ',')
    return "$texto mm"
}

/** "31°". */
fun Double.emGraus(): String = "${roundToInt()}°"

/**
 * Quando algo aconteceu, relativo a [agora] no fuso [zona]: "às 14:05" hoje, "ontem às 14:05"
 * ou "em 29/09 às 14:05".
 */
fun Instant.diaEHora(agora: Instant, zona: ZoneId = ZoneId.systemDefault()): String {
    val momento = atZone(zona)
    val hora = FORMATO_HORA_MINUTO.format(momento)
    val hoje = agora.atZone(zona).toLocalDate()
    return when (val dia = momento.toLocalDate()) {
        hoje -> "às $hora"
        hoje.minusDays(1) -> "ontem às $hora"
        else -> "em ${dia.formatarCurto()} às $hora"
    }
}

/** "Hoje", "Amanhã", "Sexta". */
fun LocalDate.rotuloDoDia(hoje: LocalDate): String = when (this) {
    hoje -> "Hoje"
    hoje.plusDays(1) -> "Amanhã"
    else -> dayOfWeek.getDisplayName(TextStyle.FULL, BRASIL_CLIMA)
        .substringBefore("-") // "sexta-feira" → "sexta"
        .replaceFirstChar { it.uppercase(BRASIL_CLIMA) }
}
