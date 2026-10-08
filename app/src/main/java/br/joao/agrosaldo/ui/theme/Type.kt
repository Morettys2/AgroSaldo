package br.joao.agrosaldo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.joao.agrosaldo.R

/**
 * Plus Jakarta Sans empacotada em res/font (SIL OFL; licença em assets/licencas), sem
 * downloadable fonts para funcionar offline. Um arquivo estático por peso, gerado da
 * fonte variável, para compatibilidade com API 26+.
 */
val PlusJakartaSans = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold)
)

/** OpenType "tnum": algarismos tabulares, para alinhar valores em colunas e evitar saltos ao mudar. */
private const val NUMEROS_ALINHADOS = "tnum"

private val Fonte = PlusJakartaSans

// Os estilos display são reservados a valores monetários e devem ser o maior texto da tela.
val TipografiaAgro = Typography(

    // Saldo do mês, valor do lançamento.
    displayLarge = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = NUMEROS_ALINHADOS
    ),

    // Total consolidado, temperatura, saldo do ano.
    displayMedium = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = NUMEROS_ALINHADOS
    ),

    // Saldo no cabeçalho de Lançamentos.
    displaySmall = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = NUMEROS_ALINHADOS
    ),

    // Título de tela no cabeçalho.
    headlineLarge = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp
    ),

    // Títulos das telas de entrada e consentimento.
    headlineMedium = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    ),

    // Frase de destaque (resumo do clima).
    headlineSmall = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),

    // Título da barra superior.
    titleLarge = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),

    // Título de cartão.
    titleMedium = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),

    // Valor numa linha de lista, nome do lançamento.
    titleSmall = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontFeatureSettings = NUMEROS_ALINHADOS
    ),

    // Texto padrão: campo de formulário, parágrafos.
    bodyLarge = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),

    // Texto de apoio dentro de cartões.
    bodyMedium = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),

    // Linha de apoio de itens de lista.
    bodySmall = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),

    // Botões e chips.
    labelLarge = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),

    // Rótulos de seção.
    labelMedium = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),

    // Legenda, texto da barra de navegação.
    labelSmall = TextStyle(
        fontFamily = Fonte,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)
