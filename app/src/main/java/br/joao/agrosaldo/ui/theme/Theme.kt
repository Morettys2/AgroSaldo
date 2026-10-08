package br.joao.agrosaldo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Tema raiz: Material 3 mais as extensões [CoresAgro] e [CoresCategorias].
 * Segue o modo claro/escuro do sistema. Dynamic color é desativado de propósito: as
 * cores de receita/despesa têm significado e não podem vir do papel de parede.
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

/** Acesso a [CoresAgro] com a mesma sintaxe das cores nativas: `MaterialTheme.agro.despesa`. */
val MaterialTheme.agro: CoresAgro
    @Composable
    @ReadOnlyComposable
    get() = LocalCoresAgro.current

/** Acesso a [CoresCategorias]: `MaterialTheme.categorias.energia`. */
val MaterialTheme.categorias: CoresCategorias
    @Composable
    @ReadOnlyComposable
    get() = LocalCoresCategorias.current
