package br.joao.agrosaldo.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.ui.theme.CorCategoria
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.categorias

/*
 * Ícone e cor de cada categoria, na camada de UI porque o domínio não depende de Compose.
 * Os `when` são exaustivos (sem `else`) para que uma categoria nova gere erro de compilação.
 */

fun CategoriaLancamento.icone(): ImageVector = when (this) {
    CategoriaLancamento.ENERGIA -> IconesAgro.Energia
    CategoriaLancamento.DEFENSIVOS -> IconesAgro.Defensivos
    CategoriaLancamento.SEMENTES -> IconesAgro.Sementes
    CategoriaLancamento.ADUBO -> IconesAgro.Adubo
    CategoriaLancamento.COMBUSTIVEL -> IconesAgro.Combustivel
    CategoriaLancamento.MAO_DE_OBRA -> IconesAgro.MaoDeObra
    CategoriaLancamento.MANUTENCAO -> IconesAgro.Manutencao
    CategoriaLancamento.OUTRAS_DESPESAS -> IconesAgro.Outras
    CategoriaLancamento.VENDA_SAFRA -> IconesAgro.VendaSafra
    CategoriaLancamento.ARRENDAMENTO -> IconesAgro.Arrendamento
    CategoriaLancamento.OUTROS -> IconesAgro.Outras
}

@Composable
@ReadOnlyComposable
fun CategoriaLancamento.cor(): CorCategoria {
    val cores = MaterialTheme.categorias
    return when (this) {
        CategoriaLancamento.ENERGIA -> cores.energia
        CategoriaLancamento.DEFENSIVOS -> cores.defensivos
        CategoriaLancamento.SEMENTES -> cores.sementes
        CategoriaLancamento.ADUBO -> cores.adubo
        CategoriaLancamento.COMBUSTIVEL -> cores.combustivel
        CategoriaLancamento.MAO_DE_OBRA -> cores.maoDeObra
        CategoriaLancamento.MANUTENCAO -> cores.manutencao
        CategoriaLancamento.OUTRAS_DESPESAS -> cores.neutra
        // Receita principal reutiliza o verde de sementes.
        CategoriaLancamento.VENDA_SAFRA -> cores.sementes
        CategoriaLancamento.ARRENDAMENTO -> cores.arrendamento
        CategoriaLancamento.OUTROS -> cores.neutra
    }
}

/** Ícone da categoria sobre fundo na cor dela. */
@Composable
fun IconeCategoria(
    categoria: CategoriaLancamento,
    modifier: Modifier = Modifier,
    tamanho: Dp = 44.dp
) {
    val cor = categoria.cor()
    Surface(
        shape = MaterialTheme.shapes.small,
        color = cor.fundo,
        contentColor = cor.conteudo,
        modifier = modifier.size(tamanho)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = categoria.icone(),
                contentDescription = null,
                modifier = Modifier.size(tamanho / 2)
            )
        }
    }
}
