package br.joao.agrosaldo.feature.lancamento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import br.joao.agrosaldo.core.ui.LinhaLancamento
import br.joao.agrosaldo.core.ui.SeletorMes
import br.joao.agrosaldo.core.ui.SeletorPropriedade
import br.joao.agrosaldo.core.util.emRealCurto
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.agro
import java.time.LocalDate

@Composable
fun LancamentoListaRoute(
    aoNovoLancamento: () -> Unit,
    aoEditarLancamento: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LancamentoListaViewModel = viewModel(factory = LancamentoListaViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LancamentoListaScreen(
        uiState = uiState,
        aoVoltarMes = viewModel::aoVoltarMes,
        aoAvancarMes = viewModel::aoAvancarMes,
        aoMudarFiltro = viewModel::aoMudarFiltro,
        aoSelecionarPropriedade = viewModel::aoSelecionarPropriedade,
        aoNovoLancamento = aoNovoLancamento,
        aoEditarLancamento = aoEditarLancamento,
        modifier = modifier
    )
}

@Composable
fun LancamentoListaScreen(
    uiState: LancamentoListaUiState,
    aoVoltarMes: () -> Unit,
    aoAvancarMes: () -> Unit,
    aoMudarFiltro: (FiltroLancamento) -> Unit,
    aoSelecionarPropriedade: (String) -> Unit,
    aoNovoLancamento: () -> Unit,
    aoEditarLancamento: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize()) {

            SeletorPropriedade(
                nome = uiState.nomePropriedade,
                propriedades = uiState.propriedades,
                aoSelecionar = aoSelecionarPropriedade,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            SeletorMes(
                mes = uiState.mes,
                aoVoltar = aoVoltarMes,
                aoAvancar = aoAvancarMes
            )

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChipFiltro("Tudo", uiState.filtro == FiltroLancamento.TUDO) {
                    aoMudarFiltro(FiltroLancamento.TUDO)
                }
                ChipFiltro("Despesas", uiState.filtro == FiltroLancamento.DESPESAS) {
                    aoMudarFiltro(FiltroLancamento.DESPESAS)
                }
                ChipFiltro("Receitas", uiState.filtro == FiltroLancamento.RECEITAS) {
                    aoMudarFiltro(FiltroLancamento.RECEITAS)
                }
            }

            if (uiState.temPropriedade) {
                ResumoDoMes(
                    quantidade = uiState.quantidade,
                    saldoCentavos = uiState.saldoCentavos,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.carregando ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    !uiState.temPropriedade ->
                        Aviso("Cadastre uma propriedade antes de lançar receitas e despesas.")

                    uiState.lancamentos.isEmpty() ->
                        Aviso("Nenhum lançamento neste mês.\nToque no botão para registrar o primeiro.")

                    else -> LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = uiState.lancamentos, key = { it.id }) { lancamento ->
                            LinhaLancamento(
                                lancamento = lancamento,
                                aoClicar = { aoEditarLancamento(lancamento.id) }
                            )
                        }
                    }
                }
            }
        }

        if (uiState.temPropriedade) {
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

@Composable
private fun ChipFiltro(rotulo: String, selecionado: Boolean, aoClicar: () -> Unit) {
    FilterChip(
        selected = selecionado,
        onClick = aoClicar,
        label = { Text(rotulo, style = MaterialTheme.typography.labelLarge) },
        shape = MaterialTheme.shapes.small
    )
}

@Composable
private fun ResumoDoMes(quantidade: Int, saldoCentavos: Long, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (quantidade == 1) "1 lançamento" else "$quantidade lançamentos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Saldo  " + saldoCentavos.emRealCurto(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (saldoCentavos < 0) MaterialTheme.agro.despesa
                else MaterialTheme.agro.receita
            )
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun LancamentoListaPreview() {
    AgroSaldoTheme {
        LancamentoListaScreen(
            uiState = LancamentoListaUiState(
                carregando = false,
                nomePropriedade = "Fazenda São João da Serra",
                temPropriedade = true,
                lancamentos = listOf(
                    Lancamento(
                        propriedadeId = "p1", tipo = TipoLancamento.RECEITA,
                        categoria = CategoriaLancamento.VENDA_SAFRA,
                        valorCentavos = 1_800_000L, data = LocalDate.now(),
                        descricao = "Venda de milho"
                    ),
                    Lancamento(
                        propriedadeId = "p1", tipo = TipoLancamento.DESPESA,
                        categoria = CategoriaLancamento.ENERGIA,
                        valorCentavos = 125_000L, data = LocalDate.now(),
                        descricao = "Conta do pivô"
                    )
                ),
                quantidade = 2,
                saldoCentavos = 1_675_000L,
                propriedades = listOf(
                    Propriedade(
                        nome = "Fazenda São João da Serra", cultura = "Milho",
                        areaHectares = 50.0, latitude = -21.7333, longitude = -46.9667
                    )
                )
            ),
            aoVoltarMes = {}, aoAvancarMes = {}, aoMudarFiltro = {},
            aoSelecionarPropriedade = {}, aoNovoLancamento = {}, aoEditarLancamento = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun LancamentoListaVazioPreview() {
    AgroSaldoTheme {
        LancamentoListaScreen(
            uiState = LancamentoListaUiState(
                carregando = false,
                nomePropriedade = "Sítio Boa Vista",
                temPropriedade = true
            ),
            aoVoltarMes = {}, aoAvancarMes = {}, aoMudarFiltro = {},
            aoSelecionarPropriedade = {}, aoNovoLancamento = {}, aoEditarLancamento = {}
        )
    }
}
