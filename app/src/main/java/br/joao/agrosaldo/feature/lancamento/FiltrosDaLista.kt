package br.joao.agrosaldo.feature.lancamento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.core.ui.ChipSafra
import br.joao.agrosaldo.core.ui.LocalValoresOcultos
import br.joao.agrosaldo.core.ui.icone
import br.joao.agrosaldo.core.util.emRealComSinal
import br.joao.agrosaldo.core.util.rotulo
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro

/*
 * Filtros da lista de lançamentos: tipo, categoria e o total do que está filtrado.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Filtros(
    uiState: LancamentoListaUiState,
    aoMudarFiltro: (FiltroLancamento) -> Unit,
    aoMudarCategoria: (CategoriaLancamento) -> Unit,
    aoLimparFiltros: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // FlowRow em vez de rolagem horizontal: com fonte grande, nenhum chip fica fora da tela.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espacos.pequeno),
            verticalArrangement = Arrangement.spacedBy(Espacos.pequeno)
        ) {
            ChipSafra("Tudo", uiState.filtro == FiltroLancamento.TUDO, { aoMudarFiltro(FiltroLancamento.TUDO) })
            ChipSafra("Despesas", uiState.filtro == FiltroLancamento.DESPESAS, { aoMudarFiltro(FiltroLancamento.DESPESAS) })
            ChipSafra("Receitas", uiState.filtro == FiltroLancamento.RECEITAS, { aoMudarFiltro(FiltroLancamento.RECEITAS) })
            if (uiState.categoriasDoMes.isNotEmpty()) {
                FiltroDeCategoria(
                    categorias = uiState.categoriasDoMes,
                    escolhida = uiState.categoria,
                    aoEscolher = aoMudarCategoria
                )
            }
        }

        if (uiState.temFiltro || uiState.buscando) {
            ResumoDoFiltro(
                quantidade = uiState.quantidade,
                totalCentavos = uiState.totalFiltradoCentavos,
                mostrarLimpar = uiState.temFiltro,
                aoLimpar = aoLimparFiltros
            )
        }
    }
}

/** Chip com menu das categorias disponíveis; exibe o nome da categoria selecionada. */
@Composable
internal fun FiltroDeCategoria(
    categorias: List<CategoriaLancamento>,
    escolhida: CategoriaLancamento?,
    aoEscolher: (CategoriaLancamento) -> Unit
) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        ChipSafra(
            rotulo = escolhida?.rotulo ?: "Categoria",
            selecionado = escolhida != null,
            aoClicar = { aberto = true },
            iconeFinal = IconesAgro.SetaBaixo
        )
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            if (escolhida != null) {
                DropdownMenuItem(
                    text = { Text("Todas as categorias") },
                    onClick = {
                        aoEscolher(escolhida) // reenviar a selecionada desmarca (toggle)
                        aberto = false
                    }
                )
            }
            categorias.forEach { categoria ->
                DropdownMenuItem(
                    text = { Text(categoria.rotulo) },
                    leadingIcon = { Icon(categoria.icone(), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = if (categoria == escolhida) {
                        { Icon(IconesAgro.Confirmado, contentDescription = "Marcada", modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        if (categoria != escolhida) aoEscolher(categoria)
                        aberto = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun ResumoDoFiltro(quantidade: Int, totalCentavos: Long, mostrarLimpar: Boolean, aoLimpar: () -> Unit) {
    val oculto = LocalValoresOcultos.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        val contagem = if (quantidade == 1) "1 lançamento" else "$quantidade lançamentos"
        val total = totalCentavos.emRealComSinal(oculto)
        Text(
            text = "$contagem · $total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .then(if (oculto) Modifier.semantics { contentDescription = "$contagem, valor escondido" } else Modifier)
        )
        if (mostrarLimpar) {
            TextButton(onClick = aoLimpar) { Text("Limpar", style = MaterialTheme.typography.labelLarge) }
        }
    }
}
