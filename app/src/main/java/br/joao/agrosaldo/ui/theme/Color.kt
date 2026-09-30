package br.joao.agrosaldo.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Paleta do AgroSaldo — os mesmos valores dos estilos do Figma.
// Verde de lavoura, vermelho de terra roxa, azul de água. Nada decorativo.

val Verde        = Color(0xFF3B6B47)
val VerdeCont    = Color(0xFFD3E3CF)
val SobreVerde   = Color(0xFF153021)

val Terra        = Color(0xFF9E4E28)
val TerraCont    = Color(0xFFF5E2D7)
val SobreTerra   = Color(0xFF4E2110)

val Agua         = Color(0xFF35637A)
val AguaCont     = Color(0xFFD8E6ED)
val SobreAgua    = Color(0xFF0F3244)

val Papel        = Color(0xFFEDEFE8)
val Superficie   = Color(0xFFFBFBF8)
val SuperficieC  = Color(0xFFF2F3EC)
val SuperficieCA = Color(0xFFE8EAE1)

val Tinta        = Color(0xFF22201A)
val TintaSec     = Color(0xFF6B675C)
val TintaApagada = Color(0xFF9A968A)

val Contorno     = Color(0xFFD6D5CA)
val ContornoSuave= Color(0xFFE7E7DE)

/**
 * Esquema de cores do Material 3.
 *
 * Repare que despesa NÃO está mapeada em `error`. Gastar dinheiro não é um erro
 * do sistema — é uma operação normal. Usar o papel de erro pra isso quebraria
 * o significado do design system e deixaria mensagens de falha sem cor própria.
 * As cores de dinheiro vivem em [CoresAgro], logo abaixo.
 */
val EsquemaClaro = lightColorScheme(
    primary              = Verde,
    onPrimary            = Color.White,
    primaryContainer     = VerdeCont,
    onPrimaryContainer   = SobreVerde,

    secondary            = Color(0xFF52634F),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFD5E8D0),
    onSecondaryContainer = Color(0xFF101F13),

    tertiary             = Agua,
    onTertiary           = Color.White,
    tertiaryContainer    = AguaCont,
    onTertiaryContainer  = SobreAgua,

    error                = Color(0xFFBA1A1A),
    onError              = Color.White,
    errorContainer       = Color(0xFFFFDAD6),
    onErrorContainer     = Color(0xFF410002),

    background           = Papel,
    onBackground         = Tinta,
    surface              = Superficie,
    onSurface            = Tinta,
    surfaceVariant       = SuperficieC,
    onSurfaceVariant     = TintaSec,

    surfaceContainerLowest  = Color.White,
    surfaceContainerLow     = Color(0xFFF7F8F1),
    surfaceContainer        = SuperficieC,
    surfaceContainerHigh    = SuperficieCA,
    surfaceContainerHighest = Color(0xFFE2E4DA),

    outline              = Contorno,
    outlineVariant       = ContornoSuave
)

/**
 * Cores que o Material 3 não tem papel para: entrada de dinheiro, saída de
 * dinheiro e clima. Em vez de torcer os papéis existentes, estendemos o tema.
 *
 * É o padrão recomendado quando o domínio pede uma cor que o design system
 * não prevê.
 */
@Immutable
data class CoresAgro(
    val receita: Color,
    val receitaContainer: Color,
    val sobreReceitaContainer: Color,
    val despesa: Color,
    val despesaContainer: Color,
    val sobreDespesaContainer: Color,
    val clima: Color,
    val climaContainer: Color,
    val sobreClimaContainer: Color,
    val tintaApagada: Color
)

val CoresAgroClaro = CoresAgro(
    receita               = Verde,
    receitaContainer      = VerdeCont,
    sobreReceitaContainer = SobreVerde,
    despesa               = Terra,
    despesaContainer      = TerraCont,
    sobreDespesaContainer = SobreTerra,
    clima                 = Agua,
    climaContainer        = AguaCont,
    sobreClimaContainer   = SobreAgua,
    tintaApagada          = TintaApagada
)

val LocalCoresAgro = staticCompositionLocalOf { CoresAgroClaro }
