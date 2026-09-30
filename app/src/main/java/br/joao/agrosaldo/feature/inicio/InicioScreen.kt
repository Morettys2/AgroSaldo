package br.joao.agrosaldo.feature.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.LinhaLancamento
import br.joao.agrosaldo.core.ui.SeletorMes
import br.joao.agrosaldo.core.util.emReal
import br.joao.agrosaldo.core.util.emRealCurto
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.agro
import java.time.LocalDate

@Composable
fun InicioRoute(
    aoNovoLancamento: () -> Unit,
    aoEditarLancamento: (String) -> Unit,
    aoAbrirClima: () -> Unit,
    aoCadastrarPropriedade: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InicioViewModel = viewModel(factory = InicioViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    InicioScreen(
        uiState = uiState,
        aoVoltarMes = viewModel::aoVoltarMes,
        aoAvancarMes = viewModel::aoAvancarMes,
        aoSelecionarPropriedade = viewModel::aoSelecionarPropriedade,
        aoNovoLancamento = aoNovoLancamento,
        aoEditarLancamento = aoEditarLancamento,
        aoAbrirClima = aoAbrirClima,
        aoCadastrarPropriedade = aoCadastrarPropriedade,
        modifier = modifier
    )
}

@Composable
fun InicioScreen(
    uiState: InicioUiState,
    aoVoltarMes: () -> Unit,
    aoAvancarMes: () -> Unit,
    aoSelecionarPropriedade: (String) -> Unit,
    aoNovoLancamento: () -> Unit,
    aoEditarLancamento: (String) -> Unit,
    aoAbrirClima: () -> Unit,
    aoCadastrarPropriedade: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.carregando -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        !uiState.temPropriedade -> InicioVazio(
            aoCadastrarPropriedade = aoCadastrarPropriedade,
            modifier = modifier
        )

        else -> Box(modifier = modifier.fillMaxSize()) {

            // Tela inteira numa LazyColumn só. Uma Column com verticalScroll
            // também funcionaria, mas a Lazy só desenha o que está visível —
            // quando a lista de últimos lançamentos crescer, faz diferença.
            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SeletorPropriedade(
                        nome = uiState.nomePropriedade,
                        propriedades = uiState.propriedades,
                        aoSelecionar = aoSelecionarPropriedade,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    )
                }

                item {
                    SeletorMes(
                        mes = uiState.mes,
                        aoVoltar = aoVoltarMes,
                        aoAvancar = aoAvancarMes
                    )
                }

                item {
                    CartaoSaldo(
                        uiState = uiState,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                item {
                    CartaoChuva(
                        aoClicar = aoAbrirClima,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (uiState.gastosPorCategoria.isNotEmpty()) {
                    item { Secao("Pra onde foi o dinheiro") }
                    item {
                        CartaoCategorias(
                            gastos = uiState.gastosPorCategoria,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                item { Secao("Últimos lançamentos") }

                if (uiState.ultimosLancamentos.isEmpty()) {
                    item {
                        Text(
                            text = "Nenhum lançamento neste mês.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                } else {
                    items(items = uiState.ultimosLancamentos, key = { it.id }) { lancamento ->
                        LinhaLancamento(
                            lancamento = lancamento,
                            aoClicar = { aoEditarLancamento(lancamento.id) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = aoNovoLancamento,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = "Novo lançamento",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

/**
 * O cartão principal da tela. O saldo é o maior elemento dela — se algo
 * competir com ele em tamanho, a hierarquia quebrou.
 */
@Composable
private fun CartaoSaldo(uiState: InicioUiState, modifier: Modifier = Modifier) {
    val negativo = uiState.saldoCentavos < 0

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (negativo) MaterialTheme.agro.despesaContainer
            else MaterialTheme.agro.receitaContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        val corTexto = if (negativo) MaterialTheme.agro.sobreDespesaContainer
        else MaterialTheme.agro.sobreReceitaContainer

        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Saldo do mês",
                style = MaterialTheme.typography.labelLarge,
                color = corTexto
            )
            Text(
                text = uiState.saldoCentavos.emReal(),
                style = MaterialTheme.typography.displayLarge,
                color = corTexto
            )

            Spacer(Modifier.height(18.dp))

            // Barra proporcional de entradas contra saídas. Desenhada com dois
            // Box de peso diferente em vez de LinearProgressIndicator, porque
            // aqui as duas partes têm cor própria e significado próprio.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val entrada = uiState.proporcaoReceitas.coerceIn(0.02f, 0.98f)
                Box(
                    Modifier
                        .weight(entrada)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.agro.receita)
                )
                Box(
                    Modifier
                        .weight(1f - entrada)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.agro.despesa)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Entrou  " + uiState.receitasCentavos.emRealCurto(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = corTexto
                )
                Text(
                    text = "Saiu  " + uiState.despesasCentavos.emRealCurto(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = corTexto
                )
            }
        }
    }
}

/**
 * Por enquanto o aviso é fixo. Na fase 6 ele passa a vir do ViewModel,
 * alimentado pela Open-Meteo, e some quando não houver chuva prevista.
 */
@Composable
private fun CartaoChuva(aoClicar: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.agro.climaContainer),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = aoClicar)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.agro.clima
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Previsão de chuva",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.agro.sobreClimaContainer
                )
                Text(
                    text = "Toque para ver os próximos dias",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.agro.sobreClimaContainer
                )
            }
        }
    }
}

@Composable
private fun CartaoCategorias(gastos: List<GastoPorCategoria>, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            gastos.forEach { gasto ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(gasto.categoria.rotulo, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = gasto.totalCentavos.emRealCurto(),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    LinearProgressIndicator(
                        progress = { gasto.proporcao },
                        color = MaterialTheme.agro.despesa,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun Secao(titulo: String) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
    )
}

@Composable
private fun SeletorPropriedade(
    nome: String,
    propriedades: List<Propriedade>,
    aoSelecionar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var aberto by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { aberto = true }
        ) {
            Text(text = nome, style = MaterialTheme.typography.titleLarge)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Trocar propriedade",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            propriedades.forEach { propriedade ->
                DropdownMenuItem(
                    text = { Text(propriedade.nome) },
                    onClick = {
                        aoSelecionar(propriedade.id)
                        aberto = false
                    }
                )
            }
        }
    }
}

/** Tela 05 do Figma — primeiro acesso, antes de existir qualquer propriedade. */
@Composable
private fun InicioVazio(aoCadastrarPropriedade: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Grass,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Comece cadastrando um sítio",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Cada propriedade tem seus próprios lançamentos e sua própria previsão do tempo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = aoCadastrarPropriedade,
            shape = CircleShape,
            modifier = Modifier.height(56.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = "Cadastrar propriedade",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun InicioPreview() {
    AgroSaldoTheme {
        InicioScreen(
            uiState = InicioUiState(
                carregando = false,
                temPropriedade = true,
                nomePropriedade = "Fazenda São João da Serra",
                saldoCentavos = 1_247_000L,
                receitasCentavos = 1_800_000L,
                despesasCentavos = 553_000L,
                proporcaoReceitas = 0.76f,
                gastosPorCategoria = listOf(
                    GastoPorCategoria(CategoriaLancamento.ENERGIA, 125_000L, 1f),
                    GastoPorCategoria(CategoriaLancamento.ADUBO, 78_000L, 0.62f),
                    GastoPorCategoria(CategoriaLancamento.COMBUSTIVEL, 32_000L, 0.26f)
                ),
                ultimosLancamentos = listOf(
                    Lancamento(
                        propriedadeId = "p1", tipo = TipoLancamento.DESPESA,
                        categoria = CategoriaLancamento.COMBUSTIVEL,
                        valorCentavos = 32_000L, data = LocalDate.now(),
                        descricao = "Abastecimento do trator"
                    )
                )
            ),
            aoVoltarMes = {}, aoAvancarMes = {}, aoSelecionarPropriedade = {},
            aoNovoLancamento = {}, aoEditarLancamento = {}, aoAbrirClima = {},
            aoCadastrarPropriedade = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun InicioVazioPreview() {
    AgroSaldoTheme {
        InicioScreen(
            uiState = InicioUiState(carregando = false, temPropriedade = false),
            aoVoltarMes = {}, aoAvancarMes = {}, aoSelecionarPropriedade = {},
            aoNovoLancamento = {}, aoEditarLancamento = {}, aoAbrirClima = {},
            aoCadastrarPropriedade = {}
        )
    }
}
