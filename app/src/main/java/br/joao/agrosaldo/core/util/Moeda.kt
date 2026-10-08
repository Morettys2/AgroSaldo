package br.joao.agrosaldo.core.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private val BRASIL: Locale = Locale.forLanguageTag("pt-BR")

private val MOEDA: NumberFormat = NumberFormat.getCurrencyInstance(BRASIL)

private val MOEDA_CURTA: NumberFormat = NumberFormat.getCurrencyInstance(BRASIL).apply {
    maximumFractionDigits = 0
}

/**
 * Valores monetários são sempre centavos em Long; a conversão para decimal ocorre
 * só na formatação, via [BigDecimal] (exata, sem ponto flutuante).
 */
fun Long.emReal(): String = MOEDA.format(BigDecimal.valueOf(this, 2))

/**
 * Omite só centavos zerados: "R$ 1.250", mas "R$ 1.249,50". Nunca arredonda,
 * para o valor exibido sempre bater com a soma dos lançamentos.
 */
fun Long.emRealCurto(): String = if (this % 100 == 0L) MOEDA_CURTA.format(this / 100) else emReal()

/**
 * Converte a entrada do campo de valor em centavos: ignora não-dígitos e lê os
 * dígitos como centavos ("125050" = R$ 1.250,50). Limite de 9 dígitos; null se vazio.
 */
fun String.digitosParaCentavos(): Long? {
    val digitos = filter { it.isDigit() }.take(9)
    if (digitos.isEmpty()) return null
    return digitos.toLongOrNull()
}

/** Substituto de valores ocultos; tamanho fixo para não revelar a magnitude. */
const val VALOR_ESCONDIDO = "R$ ••••"

fun Long.emReal(oculto: Boolean): String = if (oculto) VALOR_ESCONDIDO else emReal()

fun Long.emRealCurto(oculto: Boolean): String = if (oculto) VALOR_ESCONDIDO else emRealCurto()
