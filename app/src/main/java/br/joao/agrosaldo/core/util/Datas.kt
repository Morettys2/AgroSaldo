package br.joao.agrosaldo.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.format.TextStyle
import java.util.Locale

private val BRASIL: Locale = Locale.forLanguageTag("pt-BR")

/**
 * STRICT recusa datas impossíveis como 31/02.
 *
 * O modo padrão do Java (SMART) "conserta" 31/02/2026 para 28/02/2026 sem
 * avisar — o produtor digitaria uma data e o lançamento cairia em outra.
 *
 * Com STRICT o padrão precisa usar "uuuu" (ano) em vez de "yyyy" (ano da era):
 * "yyyy" em modo estrito exige também a era (d.C.), e toda data falharia.
 */
private val FORMATO_BR: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

private val FORMATO_CURTO: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

private val FORMATO_DATA_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu 'às' HH:mm")

/**
 * java.time está disponível nativamente porque o minSdk é 26.
 * Com minSdk 24 seria preciso habilitar desugaring no Gradle.
 */
fun LocalDate.formatarBr(): String = format(FORMATO_BR)

fun LocalDate.formatarCurto(): String = format(FORMATO_CURTO)

/**
 * "30/09/2026 às 19:40". Um Instant é um ponto no tempo sem fuso; para
 * mostrar hora é preciso dizer em qual fuso — por padrão, o do celular.
 */
fun Instant.formatarDataHoraBr(zona: ZoneId = ZoneId.systemDefault()): String =
    FORMATO_DATA_HORA.format(atZone(zona))

/** Retorna null se o texto não for uma data válida. A tela decide o que fazer com isso. */
fun String.paraLocalDateOuNulo(): LocalDate? = try {
    LocalDate.parse(this.trim(), FORMATO_BR)
} catch (e: DateTimeParseException) {
    null
}

/** "Setembro de 2026" — com a primeira letra maiúscula, que o Java não faz sozinho. */
fun YearMonth.rotulo(): String {
    val nomeDoMes = month.getDisplayName(TextStyle.FULL, BRASIL)
    return nomeDoMes.replaceFirstChar { it.uppercase(BRASIL) } + " de " + year
}

fun LocalDate.pertenceAo(mes: YearMonth): Boolean =
    YearMonth.from(this) == mes

/**
 * "30 segundos", "1 minuto", "2 minutos e 30 segundos". Arredonda para cima:
 * dizer "0 segundos" com o bloqueio ainda ativo confundiria a pessoa.
 */
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
