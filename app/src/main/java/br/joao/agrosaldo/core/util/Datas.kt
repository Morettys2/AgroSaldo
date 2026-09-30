package br.joao.agrosaldo.core.util

import java.time.LocalDate
import java.time.YearMonth
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

/**
 * java.time está disponível nativamente porque o minSdk é 26.
 * Com minSdk 24 seria preciso habilitar desugaring no Gradle.
 */
fun LocalDate.formatarBr(): String = format(FORMATO_BR)

fun LocalDate.formatarCurto(): String = format(FORMATO_CURTO)

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
