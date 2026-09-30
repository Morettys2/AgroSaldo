package br.joao.agrosaldo.core.util

import java.text.NumberFormat
import java.util.Locale

private val BRASIL: Locale = Locale.forLanguageTag("pt-BR")

private val MOEDA: NumberFormat = NumberFormat.getCurrencyInstance(BRASIL)

private val MOEDA_CURTA: NumberFormat = NumberFormat.getCurrencyInstance(BRASIL).apply {
    maximumFractionDigits = 0
}

/**
 * Dinheiro circula em CENTAVOS (Long) no app inteiro.
 *
 * Double aqui seria bug garantido: 0.1 + 0.2 em ponto flutuante dá
 * 0.30000000000000004, e o saldo do mês não fecharia com a planilha do
 * produtor. A conversão pra decimal acontece só aqui, na hora de exibir.
 */
fun Long.emReal(): String = MOEDA.format(this / 100.0)

/** Versão sem centavos, para listas e resumos: R$ 1.250 em vez de R$ 1.250,00. */
fun Long.emRealCurto(): String = MOEDA_CURTA.format(this / 100.0)

/**
 * Converte o que o usuário digitou em centavos.
 *
 * O campo aceita só dígitos: digitar 125050 vira R$ 1.250,50. É o mesmo
 * comportamento de app de banco, e evita ter que tratar vírgula, ponto e
 * "R$" na entrada.
 */
fun String.digitosParaCentavos(): Long? {
    val digitos = filter { it.isDigit() }.take(9)
    if (digitos.isEmpty()) return null
    return digitos.toLongOrNull()
}
