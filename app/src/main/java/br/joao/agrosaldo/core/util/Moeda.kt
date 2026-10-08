package br.joao.agrosaldo.core.util

import java.text.NumberFormat
import java.util.Locale

private val BRASIL: Locale = Locale.forLanguageTag("pt-BR")

private val MOEDA: NumberFormat = NumberFormat.getCurrencyInstance(BRASIL)

private val MOEDA_CURTA: NumberFormat = NumberFormat.getCurrencyInstance(BRASIL).apply {
    maximumFractionDigits = 0
}

/**
 * Valores monetários são sempre centavos em Long; a conversão para decimal ocorre
 * só na formatação, para evitar erro de arredondamento de ponto flutuante.
 */
fun Long.emReal(): String = MOEDA.format(this / 100.0)

/** Sem casas decimais: "R$ 1.250". */
fun Long.emRealCurto(): String = MOEDA_CURTA.format(this / 100.0)

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
