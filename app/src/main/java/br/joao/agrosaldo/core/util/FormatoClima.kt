package br.joao.agrosaldo.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val BRASIL_CLIMA: Locale = Locale.forLanguageTag("pt-BR")
private val FORMATO_HORA_MINUTO: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** "6 mm", "2,5 mm", "12 mm": uma casa decimal abaixo de 10 mm, inteiro a partir daí. Negativo vira 0. */
fun Double.emMilimetros(): String {
    val valor = coerceAtLeast(0.0)
    if (valor >= 10.0) return "${valor.roundToInt()} mm"
    val umaCasa = (valor * 10).roundToInt() / 10.0
    val texto = if (umaCasa % 1.0 == 0.0) umaCasa.toInt().toString() else umaCasa.toString().replace('.', ',')
    return "$texto mm"
}

/** "31°". */
fun Double.emGraus(): String = "${roundToInt()}°"

/** "18h". */
fun LocalDateTime.horaCurta(): String = "${hour}h"

/** "14:05", no fuso informado (padrão: o do sistema). */
fun Instant.horaMinuto(zona: ZoneId = ZoneId.systemDefault()): String = FORMATO_HORA_MINUTO.format(atZone(zona))

/** "Hoje", "Amanhã", "Sexta". */
fun LocalDate.rotuloDoDia(hoje: LocalDate): String = when (this) {
    hoje -> "Hoje"
    hoje.plusDays(1) -> "Amanhã"
    else -> dayOfWeek.getDisplayName(TextStyle.FULL, BRASIL_CLIMA)
        .substringBefore("-") // "sexta-feira" → "sexta"
        .replaceFirstChar { it.uppercase(BRASIL_CLIMA) }
}
