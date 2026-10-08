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

/** Teto do campo de valor: 9 dígitos de centavos, R$ 9.999.999,99. */
const val DIGITOS_MAXIMOS_DO_VALOR = 9

/**
 * Converte a entrada do campo de valor em centavos: ignora não-dígitos e lê os
 * dígitos como centavos ("125050" = R$ 1.250,50). null se vazio ou acima de
 * [DIGITOS_MAXIMOS_DO_VALOR] (zeros à esquerda não contam): cortar os dígitos
 * excedentes gravaria um valor 10 vezes menor sem aviso.
 */
fun String.digitosParaCentavos(): Long? {
    val digitos = filter { it.isDigit() }
    if (digitos.isEmpty()) return null
    val significativos = digitos.trimStart('0')
    if (significativos.length > DIGITOS_MAXIMOS_DO_VALOR) return null
    return significativos.toLongOrNull() ?: 0L
}

/** Substituto de valores ocultos; tamanho fixo para não revelar a magnitude. */
const val VALOR_ESCONDIDO = "R$ ••••"

fun Long.emReal(oculto: Boolean): String = if (oculto) VALOR_ESCONDIDO else emReal()

fun Long.emRealCurto(oculto: Boolean): String = if (oculto) VALOR_ESCONDIDO else emRealCurto()
