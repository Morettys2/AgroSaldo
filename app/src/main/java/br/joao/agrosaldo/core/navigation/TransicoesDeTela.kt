package br.joao.agrosaldo.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

/*
 * Transições entre telas do NavHost. O padrão do Navigation Compose é um crossfade de
 * 700 ms em que a tela antiga e a nova ficam visíveis juntas ("fantasma"). Aqui:
 *  - entre abas da barra inferior não há animação (troca instantânea, sem sobreposição);
 *  - nas demais telas (formulários, conta, login) a nova desliza por cima e a antiga some
 *    rápido, para nunca haver duas telas legíveis ao mesmo tempo.
 */

private const val DURACAO_DESLIZE_MS = 250
private const val DURACAO_SAIDA_MS = 90
private const val DURACAO_VOLTA_MS = 150

/** Quanto da largura a tela de baixo se move enquanto a de cima desliza (1/4 dá profundidade). */
private const val FRACAO_DA_TELA_DE_BAIXO = 4

/** Regra pura (testável em JVM): trocar de aba não anima. */
internal fun trocaEntreAbas(origem: String?, destino: String?): Boolean = origem in Rotas.ABAS && destino in Rotas.ABAS

private val AnimatedContentTransitionScope<NavBackStackEntry>.ehTrocaDeAba: Boolean
    get() = trocaEntreAbas(initialState.destination.route, targetState.destination.route)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.entrada(): EnterTransition = if (ehTrocaDeAba) {
    EnterTransition.None
} else {
    slideIntoContainer(SlideDirection.Start, tween<IntOffset>(DURACAO_DESLIZE_MS))
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.saida(): ExitTransition = if (ehTrocaDeAba) {
    ExitTransition.None
} else {
    // Some em 90 ms enquanto a nova ainda está entrando: sem texto das duas telas ao mesmo tempo.
    fadeOut(tween(DURACAO_SAIDA_MS)) + slideOutOfContainer(
        SlideDirection.Start,
        tween<IntOffset>(DURACAO_DESLIZE_MS),
        targetOffset = { it / FRACAO_DA_TELA_DE_BAIXO }
    )
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.entradaAoVoltar(): EnterTransition = if (ehTrocaDeAba) {
    EnterTransition.None
} else {
    // Só aparece depois que a tela que está saindo já sumiu (delay = duração da saída).
    fadeIn(tween(DURACAO_VOLTA_MS, delayMillis = DURACAO_SAIDA_MS)) + slideIntoContainer(
        SlideDirection.End,
        tween<IntOffset>(DURACAO_DESLIZE_MS),
        initialOffset = { it / FRACAO_DA_TELA_DE_BAIXO }
    )
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.saidaAoVoltar(): ExitTransition = if (ehTrocaDeAba) {
    ExitTransition.None
} else {
    fadeOut(tween(DURACAO_SAIDA_MS)) + slideOutOfContainer(SlideDirection.End, tween<IntOffset>(DURACAO_DESLIZE_MS))
}
