package br.joao.agrosaldo.core.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val SIMBOLOS_BR = DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"))

/**
 * Formata em pt-BR sem notação científica nem ",0" supérfluo ("12,5", "-23,5", "18"),
 * com até 8 casas decimais.
 *
 * @param agrupar separador de milhar ("12.000"); evitar em campos editáveis.
 */
fun Double.numeroBr(agrupar: Boolean = false): String {
    // DecimalFormat não é thread-safe; por isso uma instância por chamada.
    val formato = DecimalFormat(if (agrupar) "#,##0.########" else "0.########", SIMBOLOS_BR)
    return formato.format(this)
}
