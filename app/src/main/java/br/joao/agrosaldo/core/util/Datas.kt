package br.joao.agrosaldo.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.format.TextStyle
import java.util.Locale

private val BRASIL: Locale = Locale.forLanguageTag("pt-BR")

/**
 * STRICT rejeita datas inexistentes (31/02); SMART as ajustaria silenciosamente.
 * Em STRICT o ano deve ser "uuuu": "yyyy" exigiria também o campo de era.
 */
private val FORMATO_BR: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

private val FORMATO_CURTO: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

private val FORMATO_DATA_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu 'às' HH:mm")

// java.time sem desugaring depende de minSdk >= 26.
fun LocalDate.formatarBr(): String = format(FORMATO_BR)

fun LocalDate.formatarCurto(): String = format(FORMATO_CURTO)

/** "30/09/2026 às 19:40", no fuso informado (padrão: o do sistema). */
fun Instant.formatarDataHoraBr(zona: ZoneId = ZoneId.systemDefault()): String =
    FORMATO_DATA_HORA.format(atZone(zona))

/** Parse de "dd/MM/aaaa" estrito; null se inválido. */
fun String.paraLocalDateOuNulo(): LocalDate? = try {
    LocalDate.parse(this.trim(), FORMATO_BR)
} catch (e: DateTimeParseException) {
    null
}

/** "Setembro de 2026" (o nome do mês em pt-BR vem em minúscula do java.time). */
fun YearMonth.rotulo(): String {
    val nomeDoMes = month.getDisplayName(TextStyle.FULL, BRASIL)
    return nomeDoMes.replaceFirstChar { it.uppercase(BRASIL) } + " de " + year
}

fun LocalDate.pertenceAo(mes: YearMonth): Boolean =
    YearMonth.from(this) == mes

/**
 * Conversões para o DatePicker do Material 3, que usa epoch millis em UTC.
 * Usar o fuso local deslocaria a data em um dia em fusos negativos.
 */
fun LocalDate.emMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.paraLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** "Jan", "Fev"... (eixo do gráfico do histórico). */
fun YearMonth.siglaDoMes(): String =
    month.getDisplayName(TextStyle.SHORT, BRASIL).take(3).replaceFirstChar { it.uppercase(BRASIL) }

/** "Hoje", "Ontem", "Amanhã", "2 de outubro" ou, em outro ano, "2 de outubro de 2025". */
fun LocalDate.rotuloRelativo(hoje: LocalDate): String = when (this) {
    hoje -> "Hoje"
    hoje.minusDays(1) -> "Ontem"
    hoje.plusDays(1) -> "Amanhã"
    else -> {
        val mes = month.getDisplayName(TextStyle.FULL, BRASIL)
        if (year == hoje.year) "$dayOfMonth de $mes" else "$dayOfMonth de $mes de $year"
    }
}

/** "Hoje", "Ontem", "Amanhã", "2 out" ou "2 out 2025". */
fun LocalDate.rotuloRelativoCurto(hoje: LocalDate): String = when (this) {
    hoje -> "Hoje"
    hoje.minusDays(1) -> "Ontem"
    hoje.plusDays(1) -> "Amanhã"
    else -> if (year == hoje.year) diaEMesCurto() else "${diaEMesCurto()} $year"
}

/** Campo de data do formulário: "Hoje, 4 out", "Amanhã, 5 out", "28 set" ou "28 set 2025". */
fun LocalDate.rotuloDeData(hoje: LocalDate): String = when (this) {
    hoje, hoje.minusDays(1), hoje.plusDays(1) -> "${rotuloRelativoCurto(hoje)}, ${diaEMesCurto()}"
    else -> rotuloRelativoCurto(hoje)
}

/** "4 out". take(3) remove o ponto que algumas versões do JDK/ICU incluem ("out."). */
private fun LocalDate.diaEMesCurto(): String =
    "$dayOfMonth " + month.getDisplayName(TextStyle.SHORT, BRASIL).take(3).lowercase(BRASIL)

fun YearMonth.nomeDoMes(): String =
    month.getDisplayName(TextStyle.FULL, BRASIL).replaceFirstChar { it.uppercase(BRASIL) }

/** "30 segundos", "1 minuto", "2 minutos e 30 segundos". Mínimo de 1 s, nunca "0 segundos". */
fun formatarEspera(segundos: Long): String {
    val total = segundos.coerceAtLeast(1)
    val minutos = total / 60
    val resto = total % 60
    fun plural(n: Long, singular: String, plural: String) = "$n " + if (n == 1L) singular else plural
    return when {
        minutos == 0L -> plural(resto, "segundo", "segundos")
        resto == 0L -> plural(minutos, "minuto", "minutos")
        else -> plural(minutos, "minuto", "minutos") + " e " + plural(resto, "segundo", "segundos")
    }
}
