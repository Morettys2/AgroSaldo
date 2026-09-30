package br.joao.agrosaldo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Escala de formas em cinco níveis, do Material 3 Expressive.
 *
 * A ideia é a mesma da escala tipográfica: variar o arredondamento com
 * intenção cria hierarquia. Se tudo tem o mesmo raio, a tela fica chapada.
 *
 * Onde cada um é usado no AgroSaldo:
 *   extraSmall  8dp  — ícone dentro de avatar, marca do app
 *   small      12dp  — campo de texto, chip, avatar de lançamento
 *   medium     16dp  — cartão de lista
 *   large      20dp  — cartão de destaque, cartão de clima
 *   extraLarge 28dp  — cartão do saldo, folha de baixo, diálogo
 *
 * Botões e o FAB estendido usam CircleShape (pílula), fora da escala.
 */
val FormasAgro = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
