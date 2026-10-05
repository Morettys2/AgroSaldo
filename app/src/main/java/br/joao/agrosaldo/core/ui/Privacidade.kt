package br.joao.agrosaldo.core.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * true quando o "olho" do Início está fechado. Quem mostra dinheiro lê
 * daqui e troca o valor por "R$ ••••" (veja emReal(oculto) em Moeda.kt).
 *
 * É um CompositionLocal para não ter que passar o mesmo Boolean por todas
 * as telas e componentes: o esqueleto do app (AppNavegacao) define uma
 * vez, e qualquer Text lá embaixo consegue ler.
 */
val LocalValoresOcultos = compositionLocalOf { false }

/**
 * Com o valor escondido, o leitor de tela leria "R$ ponto ponto ponto ponto".
 * Isto troca pela frase que faz sentido.
 */
fun Modifier.valorEscondido(oculto: Boolean): Modifier =
    if (oculto) semantics { contentDescription = "Valor escondido" } else this

/** Para textos como "Entrou  R$ ••••": o leitor de tela diz "Entrou: valor escondido". */
fun Modifier.textoComValor(rotulo: String, oculto: Boolean): Modifier =
    if (oculto) semantics { contentDescription = "$rotulo: valor escondido" } else this
