package br.joao.agrosaldo.ui.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * As três prévias que toda tela precisa (CLAUDE.md, regra 13): claro, escuro e fonte 2×.
 *
 * O [AgroSaldoTheme] segue `isSystemInDarkTheme()`, então a prévia escura só liga o modo noturno do
 * `uiMode`. As alturas dão espaço para a tela inteira aparecer, inclusive com a fonte dobrada.
 */
@Preview(name = "Claro", showBackground = true, heightDp = 900)
@Preview(
    name = "Escuro",
    showBackground = true,
    heightDp = 900,
    uiMode =
    Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(name = "Fonte 2x", showBackground = true, heightDp = 1800, fontScale = 2f)
annotation class PreviasDaTela
