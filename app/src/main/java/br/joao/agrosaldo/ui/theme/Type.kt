package br.joao.agrosaldo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Escala tipográfica do Figma traduzida para os papéis do Material 3.
//
// A regra que sustenta o layout: o valor em dinheiro é sempre o maior
// elemento da tela. Se algo competir com ele, a hierarquia quebrou.
//
// FontFamily.Default é a Roboto do sistema. Para usar a mesma fonte do Figma,
// adicione as Downloadable Fonts do Google e troque só esta linha.
private val Fonte = FontFamily.Default

val TipografiaAgro = Typography(

    // Saldo do mês, valor do lançamento. O herói da tela.
    displayLarge = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.9).sp
    ),

    // Total consolidado, temperatura.
    displayMedium = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.7).sp
    ),

    // Títulos de tela grandes: "Bem-vindo de volta", "Seus dados são seus".
    headlineMedium = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.3).sp
    ),

    // Título da barra superior.
    titleLarge = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),

    // Título de cartão.
    titleMedium = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),

    // Texto padrão: nome do lançamento, campo de formulário.
    bodyLarge = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),

    // Texto de apoio dentro de cartões.
    bodyMedium = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),

    // Rótulos de seção, chips, botões.
    labelLarge = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),

    // Data, legenda, texto da barra de navegação.
    labelSmall = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
)
