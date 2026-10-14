package br.joao.agrosaldo.core.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * true quando os valores monetários devem ser ocultados (ver `emReal(oculto)`).
 * Fornecido por AppNavegacao.
 */
val LocalValoresOcultos = compositionLocalOf { false }

/** Substitui a leitura de "R$ ••••" pelo TalkBack por "Valor escondido". */
fun Modifier.valorEscondido(oculto: Boolean): Modifier = if (oculto) semantics { contentDescription = "Valor escondido" } else this

/** Variante com rótulo: o TalkBack lê "[rotulo]: valor escondido". */
fun Modifier.textoComValor(rotulo: String, oculto: Boolean): Modifier =
    if (oculto) semantics { contentDescription = "$rotulo: valor escondido" } else this
