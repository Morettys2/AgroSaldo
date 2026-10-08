package br.joao.agrosaldo.core.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val SIMBOLOS_BR = DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"))

/**
 * Número do jeito que o brasileiro escreve: "12,5", "-21,7333", "18".
 *
 * O toString() do Double escreve "1.0E7" para números grandes e "1.0E-4"
 * para pequenos, e sempre com ponto. Aqui sai por extenso, com vírgula e
 * sem o ",0" que ninguém digitou.
 *
 * @param agrupar "12.000" em vez de "12000" — bom para ler, ruim para um
 *   campo que a pessoa vai editar (o ponto viraria vírgula decimal).
 */
fun Double.numeroBr(agrupar: Boolean = false): String {
    // DecimalFormat não é thread-safe: um novo a cada chamada é barato e seguro.
    val formato = DecimalFormat(if (agrupar) "#,##0.########" else "0.########", SIMBOLOS_BR)
    return formato.format(this)
}
