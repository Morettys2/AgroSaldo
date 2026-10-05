package br.joao.agrosaldo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Escala de formas em cinco níveis.
 *
 * Variar o arredondamento com intenção cria hierarquia: o que é maior e
 * mais importante é mais redondo. Se tudo tem o mesmo raio, a tela fica chapada.
 *
 * Onde cada um é usado no AgroSaldo (direção "Safra"):
 *   extraSmall  8dp  — marca pequena, barrinha de gráfico
 *   small      14dp  — ícone de categoria, campo de texto
 *   medium     20dp  — cartões dentro do cabeçalho, campos grandes
 *   large      28dp  — cartões da tela (clima, gastos, lista)
 *   extraLarge 32dp  — fundo do cabeçalho, folha de baixo, diálogo
 *
 * Botões, chips e a barra de navegação usam CircleShape (pílula), fora da escala.
 */
val FormasAgro = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(14.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
