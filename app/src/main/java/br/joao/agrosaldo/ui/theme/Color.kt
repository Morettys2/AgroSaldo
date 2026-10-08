package br.joao.agrosaldo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Paleta do app. Verde = receita/marca, Terra = despesa, Agua = clima, VerdeNoite = cabeçalho.
// Invariante: todo par texto/fundo tem contraste >= 4,5:1 (WCAG AA).

// Tema claro
private val Verde          = Color(0xFF2C6A43)
private val VerdeCont      = Color(0xFFDCEADC)
private val SobreVerde     = Color(0xFF12301E)

private val Terra          = Color(0xFFA9481F)
private val TerraCont      = Color(0xFFF6E4DA)
private val SobreTerra     = Color(0xFF4E2110)

private val Agua           = Color(0xFF2A6683)
private val AguaCont       = Color(0xFFE0ECF2)
private val SobreAgua      = Color(0xFF0F3244)
private val AguaClara      = Color(0xFFCFE2EC)
private val AguaMedia      = Color(0xFF3E7C9A)

private val VerdeNoite     = Color(0xFF163A2A)
private val VerdeNoiteAlto = Color(0xFF24503B)
private val TextoNoite     = Color(0xFFB9CCBF)
private val Lima           = Color(0xFFD4E86A)
private val Salmao         = Color(0xFFFFD2BC)

private val Papel          = Color(0xFFF1F2EC)
private val Branco         = Color(0xFFFFFFFF)
private val Neutro         = Color(0xFFE7E9E1)
private val NeutroAlto     = Color(0xFFDDE0D6)
private val Tinta          = Color(0xFF18201B)
private val TintaSec       = Color(0xFF5A635D)
private val TintaApagada   = Color(0xFF626B64)
private val Contorno       = Color(0xFFD5D8CE)
private val ContornoSuave  = Color(0xFFEEF0E8)

// Tema escuro: tons mais claros e menos saturados (evita vibração sobre fundo escuro).
private val VerdeE         = Color(0xFF8FD1A3)
private val VerdeContE     = Color(0xFF1E4D31)
private val SobreVerdeE    = Color(0xFFBDEFC9)
private val NaVerdeE       = Color(0xFF0B3A1F)

private val TerraE         = Color(0xFFFFB596)
private val TerraContE     = Color(0xFF5A2410)
private val SobreTerraE    = Color(0xFFFFDBCC)

private val AguaE          = Color(0xFF8CCDEB)
private val AguaContE      = Color(0xFF12445A)
private val SobreAguaE     = Color(0xFFC5E7F6)

private val VerdeNoiteE    = Color(0xFF1E4A35)
private val VerdeNoiteAltoE= Color(0xFF2B5C44)

private val FundoE         = Color(0xFF111410)
private val CartaoE        = Color(0xFF1C1F1A)
private val NeutroE        = Color(0xFF282C25)
private val NeutroAltoE    = Color(0xFF32372F)
private val TintaE         = Color(0xFFE4E6DE)
private val TintaSecE      = Color(0xFFB5BCAF)
private val TintaApagadaE  = Color(0xFF9AA294)
private val ContornoE      = Color(0xFF474D43)
private val ContornoSuaveE = Color(0xFF2B2F28)

/**
 * Esquemas Material 3. Despesa não usa `error` (não é estado de erro); cores de
 * receita/despesa ficam em [CoresAgro]. Fundo de tela = `background`, cartões =
 * `surface`, chips neutros = `surfaceContainer`.
 */
val EsquemaClaro = lightColorScheme(
    primary              = Verde,
    onPrimary            = Branco,
    primaryContainer     = VerdeCont,
    onPrimaryContainer   = SobreVerde,

    secondary            = VerdeNoite,
    onSecondary          = Branco,
    secondaryContainer   = VerdeCont,
    onSecondaryContainer = SobreVerde,

    tertiary             = Agua,
    onTertiary           = Branco,
    tertiaryContainer    = AguaCont,
    onTertiaryContainer  = SobreAgua,

    error                = Color(0xFFBA1A1A),
    onError              = Branco,
    errorContainer       = Color(0xFFFFDAD6),
    onErrorContainer     = Color(0xFF410002),

    background           = Papel,
    onBackground         = Tinta,
    surface              = Branco,
    onSurface            = Tinta,
    surfaceVariant       = Neutro,
    onSurfaceVariant     = TintaSec,
    inverseSurface       = VerdeNoite,
    inverseOnSurface     = Branco,
    inversePrimary       = Lima,

    surfaceContainerLowest  = Branco,
    surfaceContainerLow     = Color(0xFFF7F8F3),
    surfaceContainer        = Neutro,
    surfaceContainerHigh    = NeutroAlto,
    surfaceContainerHighest = Color(0xFFD3D7CC),

    outline              = Contorno,
    outlineVariant       = ContornoSuave
)

val EsquemaEscuro = darkColorScheme(
    primary              = VerdeE,
    onPrimary            = NaVerdeE,
    primaryContainer     = VerdeContE,
    onPrimaryContainer   = SobreVerdeE,

    secondary            = TintaE,
    onSecondary          = FundoE,
    secondaryContainer   = VerdeContE,
    onSecondaryContainer = SobreVerdeE,

    tertiary             = AguaE,
    onTertiary           = Color(0xFF00344A),
    tertiaryContainer    = AguaContE,
    onTertiaryContainer  = SobreAguaE,

    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),

    background           = FundoE,
    onBackground         = TintaE,
    surface              = CartaoE,
    onSurface            = TintaE,
    surfaceVariant       = NeutroE,
    onSurfaceVariant     = TintaSecE,
    inverseSurface       = TintaE,
    inverseOnSurface     = FundoE,
    inversePrimary       = Verde,

    surfaceContainerLowest  = Color(0xFF0D0F0C),
    surfaceContainerLow     = Color(0xFF181B16),
    surfaceContainer        = NeutroE,
    surfaceContainerHigh    = NeutroAltoE,
    surfaceContainerHighest = Color(0xFF3C4238),

    outline              = ContornoE,
    outlineVariant       = ContornoSuaveE
)

/** Papéis de cor sem equivalente no Material 3 (receita, despesa, clima, cabeçalho). Acesso via `MaterialTheme.agro`. */
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
    val tintaApagada: Color,

    /** Fundo do cabeçalho das telas principais. */
    val cabecalho: Color,
    /** Cartões e botões sobre o cabeçalho. */
    val cabecalhoElevado: Color,
    val sobreCabecalho: Color,
    val sobreCabecalhoSecundario: Color,
    /** Item ativo da barra inferior e botão de novo lançamento. */
    val destaque: Color,
    val sobreDestaque: Color,
    /** Receita/despesa sobre o cabeçalho. */
    val receitaNoCabecalho: Color,
    val despesaNoCabecalho: Color,

    /** Cartão de clima do Início (fundo preenchido, texto claro). */
    val cartaoClima: Color,
    val cartaoClimaElevado: Color,
    val sobreCartaoClima: Color,
    val sobreCartaoClimaSecundario: Color
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
    tintaApagada          = TintaApagada,

    cabecalho                = VerdeNoite,
    cabecalhoElevado         = VerdeNoiteAlto,
    sobreCabecalho           = Branco,
    sobreCabecalhoSecundario = TextoNoite,
    destaque                 = Lima,
    sobreDestaque            = VerdeNoite,
    receitaNoCabecalho       = Lima,
    despesaNoCabecalho       = Salmao,

    cartaoClima                = Agua,
    cartaoClimaElevado         = AguaMedia,
    sobreCartaoClima           = Branco,
    sobreCartaoClimaSecundario = AguaClara
)

val CoresAgroEscuro = CoresAgro(
    receita               = VerdeE,
    receitaContainer      = VerdeContE,
    sobreReceitaContainer = SobreVerdeE,
    despesa               = TerraE,
    despesaContainer      = TerraContE,
    sobreDespesaContainer = SobreTerraE,
    clima                 = AguaE,
    climaContainer        = AguaContE,
    sobreClimaContainer   = SobreAguaE,
    tintaApagada          = TintaApagadaE,

    cabecalho                = VerdeNoiteE,
    cabecalhoElevado         = VerdeNoiteAltoE,
    sobreCabecalho           = Branco,
    sobreCabecalhoSecundario = TextoNoite,
    destaque                 = Lima,
    sobreDestaque            = VerdeNoite,
    receitaNoCabecalho       = Lima,
    despesaNoCabecalho       = Salmao,

    cartaoClima                = AguaContE,
    cartaoClimaElevado         = Color(0xFF1B5670),
    sobreCartaoClima           = Branco,
    sobreCartaoClimaSecundario = SobreAguaE
)

val LocalCoresAgro = staticCompositionLocalOf { CoresAgroClaro }

/** Cor de categoria: [conteudo] para o ícone, [fundo] para o contêiner. */
@Immutable
data class CorCategoria(val conteudo: Color, val fundo: Color)

/** Cores por categoria (lista e gráfico). Sempre acompanhadas de ícone e nome, nunca como único sinal. */
@Immutable
data class CoresCategorias(
    val energia: CorCategoria,
    val defensivos: CorCategoria,
    val sementes: CorCategoria,
    val adubo: CorCategoria,
    val combustivel: CorCategoria,
    val maoDeObra: CorCategoria,
    val manutencao: CorCategoria,
    val arrendamento: CorCategoria,
    val neutra: CorCategoria
)

val CoresCategoriasClaro = CoresCategorias(
    energia      = CorCategoria(Color(0xFF8A5A00), Color(0xFFF7EBD2)),
    defensivos   = CorCategoria(Agua, AguaCont),
    sementes     = CorCategoria(Verde, VerdeCont),
    adubo        = CorCategoria(Color(0xFF4E6B1F), Color(0xFFE8EFD9)),
    combustivel  = CorCategoria(Terra, TerraCont),
    maoDeObra    = CorCategoria(Color(0xFF5B3F86), Color(0xFFECE6F3)),
    manutencao   = CorCategoria(Color(0xFF3D433A), Neutro),
    arrendamento = CorCategoria(Color(0xFF7A4E2D), Color(0xFFF1E6DC)),
    neutra       = CorCategoria(Color(0xFF3D433A), Neutro)
)

val CoresCategoriasEscuro = CoresCategorias(
    energia      = CorCategoria(Color(0xFFF0C46A), Color(0xFF3D2E0A)),
    defensivos   = CorCategoria(AguaE, Color(0xFF12384A)),
    sementes     = CorCategoria(VerdeE, Color(0xFF1E3D2A)),
    adubo        = CorCategoria(Color(0xFFBCD68A), Color(0xFF2C3A17)),
    combustivel  = CorCategoria(TerraE, Color(0xFF4A2112)),
    maoDeObra    = CorCategoria(Color(0xFFCDB8EE), Color(0xFF33274A)),
    manutencao   = CorCategoria(Color(0xFFC4CABD), NeutroAltoE),
    arrendamento = CorCategoria(Color(0xFFE7C3A3), Color(0xFF3F2C1D)),
    neutra       = CorCategoria(Color(0xFFC4CABD), NeutroAltoE)
)

val LocalCoresCategorias = staticCompositionLocalOf { CoresCategoriasClaro }
