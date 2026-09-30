package br.joao.agrosaldo.feature.propriedade

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.SeletorMes
import br.joao.agrosaldo.core.util.emReal
import br.joao.agrosaldo.core.util.emRealCurto
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.agro

/**
 * VIEW — parte que conhece o ViewModel.
 *
 * collectAsStateWithLifecycle para de coletar quando o app vai para segundo
 * plano. Com collectAsState comum, a tela continuaria processando
 * atualizações com o celular no bolso.
 */
@Composable
fun PropriedadeListaRoute(
    aoAbrirNova: () -> Unit,
    aoAbrirPropriedade: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PropriedadeListaViewModel = viewModel(factory = PropriedadeListaViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PropriedadeListaScreen(
        uiState = uiState,
        aoVoltarMes = viewModel::aoVoltarMes,
        aoAvancarMes = viewModel::aoAvancarMes,
        aoAbrirNova = aoAbrirNova,
        aoAbrirPropriedade = aoAbrirPropriedade,
        modifier = modifier
    )
}

/** VIEW — parte stateless. Não sabe que ViewModel existe. */
@Composable
fun PropriedadeListaScreen(
    uiState: PropriedadeListaUiState,
    aoVoltarMes: () -> Unit,
    aoAvancarMes: () -> Unit,
    aoAbrirNova: () -> Unit,
    aoAbrirPropriedade: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.carregando ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))

            uiState.erro != null ->
                Text(
                    text = uiState.erro,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )

            uiState.itens.isEmpty() ->
                Text(
                    text = "Nenhuma propriedade cadastrada.\nToque no botão para começar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )

            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SeletorMes(mes = uiState.mes, aoVoltar = aoVoltarMes, aoAvancar = aoAvancarMes)
                }

                item {
                    CartaoTotal(
                        totalCentavos = uiState.totalCentavos,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                item {
                    Text(
                        text = "Comparativo do mês",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                    )
                }

                // A key faz o Compose reaproveitar o item certo quando a lista
                // muda. Sem ela, apagar um item pode redesenhar todos os outros.
                items(items = uiState.itens, key = { it.propriedade.id }) { item ->
                    CartaoPropriedade(
                        item = item,
                        aoClicar = { aoAbrirPropriedade(item.propriedade.id) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = aoAbrirNova,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = "Nova propriedade",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun CartaoTotal(totalCentavos: Long, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Somando todas as propriedades",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = totalCentavos.emReal(),
                style = MaterialTheme.typography.displayMedium,
                color = if (totalCentavos < 0) MaterialTheme.agro.despesa else MaterialTheme.agro.receita
            )
        }
    }
}

@Composable
private fun CartaoPropriedade(
    item: PropriedadeComSaldo,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val negativo = item.saldoCentavos < 0
    val cor = if (negativo) MaterialTheme.agro.despesa else MaterialTheme.agro.receita

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth().clickable(onClick = aoClicar)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.agro.receitaContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Grass,
                            contentDescription = null,
                            tint = MaterialTheme.agro.sobreReceitaContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(item.propriedade.nome, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${item.propriedade.cultura} · ${formatarArea(item.propriedade.areaHectares)} ha",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = item.saldoCentavos.emRealCurto(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cor
                )
            }

            LinearProgressIndicator(
                progress = { item.proporcao },
                color = cor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 50.0 vira "50", 12.5 vira "12,5". Sem casa decimal quando ela não existe. */
private fun formatarArea(area: Double): String =
    if (area % 1.0 == 0.0) area.toLong().toString()
    else area.toString().replace('.', ',')

// ---------- Previews ----------

@Preview(showBackground = true, heightDp = 650)
@Composable
private fun PreviewComDados() {
    AgroSaldoTheme {
        PropriedadeListaScreen(
            uiState = PropriedadeListaUiState(
                carregando = false,
                totalCentavos = 1_418_000L,
                itens = listOf(
                    PropriedadeComSaldo(
                        propriedade = Propriedade(
                            nome = "Fazenda São João da Serra", cultura = "Milho",
                            areaHectares = 50.0, latitude = -21.7333, longitude = -46.9667
                        ),
                        saldoCentavos = 1_247_000L,
                        proporcao = 1f
                    ),
                    PropriedadeComSaldo(
                        propriedade = Propriedade(
                            nome = "Sítio Boa Vista", cultura = "Soja",
                            areaHectares = 18.0, latitude = -21.8012, longitude = -46.9021
                        ),
                        saldoCentavos = 171_000L,
                        proporcao = 0.14f
                    )
                )
            ),
            aoVoltarMes = {}, aoAvancarMes = {}, aoAbrirNova = {}, aoAbrirPropriedade = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun PreviewVazio() {
    AgroSaldoTheme {
        PropriedadeListaScreen(
            uiState = PropriedadeListaUiState(carregando = false),
            aoVoltarMes = {}, aoAvancarMes = {}, aoAbrirNova = {}, aoAbrirPropriedade = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun PreviewCarregando() {
    AgroSaldoTheme {
        PropriedadeListaScreen(
            uiState = PropriedadeListaUiState(carregando = true),
            aoVoltarMes = {}, aoAvancarMes = {}, aoAbrirNova = {}, aoAbrirPropriedade = {}
        )
    }
}
