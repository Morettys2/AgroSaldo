package br.joao.agrosaldo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Tema do app. Envolve todas as telas.
 *
 * Não usamos dynamic color (a cor do papel de parede do usuário) de propósito:
 * a identidade do app depende do verde de lavoura e do vermelho de terra,
 * e deixar o sistema trocar isso quebraria o significado de receita e despesa.
 *
 * Só tema claro por enquanto. O app é usado no sol, no meio da lavoura —
 * tela escura ali não se lê. Tema escuro entra depois, se houver tempo.
 */
@Composable
fun AgroSaldoTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCoresAgro provides CoresAgroClaro) {
        MaterialTheme(
            colorScheme = EsquemaClaro,
            typography = TipografiaAgro,
            shapes = FormasAgro,
            content = content
        )
    }
}

/**
 * Atalho para as cores de domínio: `MaterialTheme.agro.despesa`.
 *
 * Ler o CompositionLocal direto em cada tela funcionaria, mas pendurar no
 * MaterialTheme deixa o uso igual ao das cores nativas — quem escreve a tela
 * não precisa saber que existe um CompositionLocal por baixo.
 */
val MaterialTheme.agro: CoresAgro
    @Composable
    @ReadOnlyComposable
    get() = LocalCoresAgro.current
