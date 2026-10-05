package br.joao.agrosaldo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Tema do app. Envolve todas as telas.
 *
 * Segue o modo do celular: claro de dia, escuro se a pessoa escolheu. No sol
 * o claro lê melhor, e é o que o Android usa por padrão.
 *
 * Não usamos dynamic color (a cor do papel de parede) de propósito: a
 * identidade do app depende do verde, e deixar o sistema trocar isso
 * quebraria o significado de receita e despesa.
 */
@Composable
fun AgroSaldoTheme(
    escuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalCoresAgro provides if (escuro) CoresAgroEscuro else CoresAgroClaro,
        LocalCoresCategorias provides if (escuro) CoresCategoriasEscuro else CoresCategoriasClaro
    ) {
        MaterialTheme(
            colorScheme = if (escuro) EsquemaEscuro else EsquemaClaro,
            typography = TipografiaAgro,
            shapes = FormasAgro,
            content = content
        )
    }
}

/**
 * Atalho para as cores de domínio: `MaterialTheme.agro.despesa`.
 *
 * Pendurar no MaterialTheme deixa o uso igual ao das cores nativas — quem
 * escreve a tela não precisa saber que existe um CompositionLocal por baixo.
 */
val MaterialTheme.agro: CoresAgro
    @Composable
    @ReadOnlyComposable
    get() = LocalCoresAgro.current

/** `MaterialTheme.categorias.energia` — as cores de cada categoria. */
val MaterialTheme.categorias: CoresCategorias
    @Composable
    @ReadOnlyComposable
    get() = LocalCoresCategorias.current
