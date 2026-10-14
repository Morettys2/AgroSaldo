package br.joao.agrosaldo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Escala de formas:
 *   extraSmall  8dp  — marcadores, barras de gráfico
 *   small      14dp  — ícone de categoria, campo de texto
 *   medium     20dp  — cartões no cabeçalho
 *   large      28dp  — cartões do corpo da tela
 *   extraLarge 32dp  — cabeçalho, bottom sheet, diálogo
 * Botões, chips e barra inferior usam CircleShape, fora da escala.
 */
val FormasAgro = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
