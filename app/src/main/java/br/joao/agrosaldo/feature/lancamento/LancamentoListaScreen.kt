package br.joao.agrosaldo.feature.lancamento

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.joao.agrosaldo.core.ui.Carregando
import br.joao.agrosaldo.core.ui.EstadoErro
import br.joao.agrosaldo.core.ui.EstadoVazio
import br.joao.agrosaldo.core.ui.LinhaLancamento
import br.joao.agrosaldo.core.util.rotulo
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun LancamentoListaRoute(
    aoEditarLancamento: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LancamentoListaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Recalcula rótulos relativos ("Hoje"/"Ontem") ao retomar, caso tenha virado o dia.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.aoVoltarParaATela() }

    LancamentoListaScreen(
        uiState = uiState,
        aoSelecionarMes = viewModel::aoSelecionarMes,
        aoMudarFiltro = viewModel::aoMudarFiltro,
        aoMudarCategoria = viewModel::aoMudarCategoria,
        aoLimparFiltros = viewModel::aoLimparFiltros,
        aoBuscar = viewModel::aoBuscar,
        aoFecharBusca = viewModel::aoFecharBusca,
        aoSelecionarPropriedade = viewModel::aoSelecionarPropriedade,
        aoEditarLancamento = aoEditarLancamento,
        modifier = modifier
    )
}

/**
 * Lista de lançamentos do mês, agrupada por dia, com seletor de mês, resumo do saldo,
 * filtros e busca (a busca abrange todos os meses).
 */
@Composable
fun LancamentoListaScreen(
    uiState: LancamentoListaUiState,
    aoSelecionarMes: (YearMonth) -> Unit,
    aoMudarFiltro: (FiltroLancamento) -> Unit,
    aoMudarCategoria: (CategoriaLancamento) -> Unit,
    aoLimparFiltros: () -> Unit,
    aoBuscar: (String) -> Unit,
    aoFecharBusca: () -> Unit,
    aoSelecionarPropriedade: (String) -> Unit,
    aoEditarLancamento: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Estado de UI separado de uiState.buscando: o campo pode estar aberto e vazio.
    var buscaAberta by rememberSaveable { mutableStateOf(uiState.buscando) }
    // Pedido de foco único, consumido pelo campo; evita reabrir o IME ao recompor/rolar.
    var focarBusca by rememberSaveable { mutableStateOf(false) }
    val fecharBusca = {
        buscaAberta = false
        aoFecharBusca()
    }
    // Mudança externa de mês: o ViewModel já limpou a busca; fecha o campo.
    LaunchedEffect(uiState.mes) {
        if (!uiState.buscando) buscaAberta = false
    }
    BackHandler(enabled = buscaAberta) { fecharBusca() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Espacos.grande)
    ) {
        item(key = "cabecalho") {
            Cabecalho(
                uiState = uiState,
                buscaAberta = buscaAberta,
                focarBusca = focarBusca,
                aoFocarBusca = { focarBusca = false },
                aoAbrirBusca = {
                    buscaAberta = true
                    focarBusca = true
                },
                aoFecharBusca = fecharBusca,
                aoBuscar = aoBuscar,
                aoSelecionarMes = aoSelecionarMes,
                aoSelecionarPropriedade = aoSelecionarPropriedade
            )
        }

        when {
            uiState.carregando -> item(key = "carregando") { Carregando() }

            !uiState.temPropriedade -> item(key = "sem_propriedade") {
                EstadoVazio(
                    texto = "Cadastre uma propriedade na aba Sítios antes de lançar receitas e despesas.",
                    modifier = Modifier.padding(horizontal = Espacos.margemDaTela)
                )
            }

            else -> {
                uiState.erro?.let { erro ->
                    item(key = "erro") {
                        EstadoErro(erro, Modifier.padding(start = Espacos.margemDaTela, end = Espacos.margemDaTela, top = Espacos.padrao))
                    }
                }

                item(key = "filtros") {
                    Filtros(
                        uiState = uiState,
                        aoMudarFiltro = aoMudarFiltro,
                        aoMudarCategoria = aoMudarCategoria,
                        aoLimparFiltros = aoLimparFiltros,
                        modifier = Modifier.padding(
                            start = Espacos.margemDaTela,
                            end = Espacos.margemDaTela,
                            top = 18.dp,
                            bottom = Espacos.padrao
                        )
                    )
                }

                if (uiState.grupos.isEmpty()) {
                    item(key = "vazio") {
                        EstadoVazio(
                            modifier = Modifier.padding(horizontal = Espacos.margemDaTela),
                            texto = when {
                                uiState.buscando -> "Nada encontrado para “${uiState.busca.trim()}”."
                                uiState.temFiltro -> "Nenhum lançamento com esse filtro neste mês."
                                else -> "Nenhum lançamento neste mês.\nToque no + verde-limão lá embaixo para lançar."
                            },
                            acao = if (uiState.temFiltro) "Limpar filtros" else null,
                            aoClicarAcao = aoLimparFiltros
                        )
                    }
                } else {
                    itensDaLista(uiState.grupos, aoEditarLancamento)
                }
            }
        }
    }
}

/**
 * Emite títulos de dia e linhas como itens separados da LazyColumn, desenhados como um
 * único cartão: [ItemDoCartao] arredonda o topo do primeiro item e a base do último.
 */
private fun LazyListScope.itensDaLista(grupos: List<GrupoDoDia>, aoEditarLancamento: (String) -> Unit) {
    val total = grupos.sumOf { it.lancamentos.size + 1 }
    var posicao = 0
    grupos.forEachIndexed { indiceDoGrupo, grupo ->
        val posicaoDoTitulo = posicao++
        item(key = "dia-${grupo.data}") {
            ItemDoCartao(posicaoDoTitulo, total) {
                if (indiceDoGrupo > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = Espacos.margemDaTela)
                    )
                }
                Text(
                    text = grupo.rotulo,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, top = if (indiceDoGrupo == 0) 16.dp else 12.dp, bottom = 2.dp)
                        .semantics { heading() }
                )
            }
        }
        grupo.lancamentos.forEach { lancamento ->
            val posicaoDaLinha = posicao++
            item(key = lancamento.id) {
                ItemDoCartao(posicaoDaLinha, total) {
                    LinhaLancamento(
                        lancamento = lancamento,
                        // A data já aparece no título do grupo.
                        apoio = lancamento.descricao,
                        aoClicar = { aoEditarLancamento(lancamento.id) },
                        margem = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 10.dp,
                            bottom = if (posicaoDaLinha == total - 1) 16.dp else 10.dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemDoCartao(posicao: Int, total: Int, conteudo: @Composable () -> Unit) {
    val raio = 28.dp
    val forma: Shape = when {
        total == 1 -> RoundedCornerShape(raio)
        posicao == 0 -> RoundedCornerShape(topStart = raio, topEnd = raio)
        posicao == total - 1 -> RoundedCornerShape(bottomStart = raio, bottomEnd = raio)
        else -> RectangleShape
    }
    Column(
        modifier = Modifier
            .padding(horizontal = Espacos.margemDaTela)
            .fillMaxWidth()
            .clip(forma)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        conteudo()
    }
}

// ---------- prévias ----------

private val PROPRIEDADE_DE_EXEMPLO = Propriedade(
    nome = "Fazenda Santa Clara",
    cultura = "Milho",
    areaHectares = 50.0,
    latitude = -21.7333,
    longitude = -46.9667
)

private fun estadoDeExemplo(): LancamentoListaUiState {
    val hoje = LocalDate.now()
    val lancamentos = listOf(
        Lancamento(
            propriedadeId = "p1",
            tipo = TipoLancamento.DESPESA,
            categoria = CategoriaLancamento.ADUBO,
            valorCentavos = 78_000L,
            data = hoje
        ),
        Lancamento(
            propriedadeId = "p1",
            tipo = TipoLancamento.RECEITA,
            categoria = CategoriaLancamento.VENDA_SAFRA,
            valorCentavos = 1_800_000L,
            data = hoje.minusDays(1),
            descricao = "Venda de milho"
        ),
        Lancamento(
            propriedadeId = "p1",
            tipo = TipoLancamento.DESPESA,
            categoria = CategoriaLancamento.ENERGIA,
            valorCentavos = 125_000L,
            data = hoje.minusDays(2),
            descricao = "Conta do pivô"
        )
    )
    return LancamentoListaUiState(
        carregando = false,
        nomePropriedade = PROPRIEDADE_DE_EXEMPLO.nome,
        temPropriedade = true,
        lancamentos = lancamentos,
        grupos = listOf(
            GrupoDoDia(hoje, "Hoje", lancamentos.take(1)),
            GrupoDoDia(hoje.minusDays(1), "Ontem", lancamentos.drop(1).take(1)),
            GrupoDoDia(hoje.minusDays(2), "2 de outubro", lancamentos.drop(2))
        ),
        categoriasDoMes = listOf(CategoriaLancamento.ENERGIA, CategoriaLancamento.ADUBO, CategoriaLancamento.VENDA_SAFRA),
        quantidade = 3,
        saldoCentavos = 1_597_000L,
        receitasCentavos = 1_800_000L,
        despesasCentavos = 203_000L,
        meses = LancamentoListaViewModel.mesesDoCabecalho(YearMonth.now(), hoje),
        propriedades = listOf(PROPRIEDADE_DE_EXEMPLO)
    )
}

@Composable
private fun ListaDeExemplo(uiState: LancamentoListaUiState) {
    LancamentoListaScreen(
        uiState = uiState,
        aoSelecionarMes = {}, aoMudarFiltro = {}, aoMudarCategoria = {}, aoLimparFiltros = {},
        aoBuscar = {}, aoFecharBusca = {}, aoSelecionarPropriedade = {}, aoEditarLancamento = {},
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    )
}

@Preview(name = "Fonte 2x", showBackground = true, heightDp = 1800, fontScale = 2f)
@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LancamentoListaPreview() {
    AgroSaldoTheme { ListaDeExemplo(estadoDeExemplo()) }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LancamentoListaEscuroPreview() {
    AgroSaldoTheme(escuro = true) { ListaDeExemplo(estadoDeExemplo()) }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun LancamentoListaVazioPreview() {
    AgroSaldoTheme {
        ListaDeExemplo(
            LancamentoListaUiState(
                carregando = false,
                nomePropriedade = "Sítio Boa Vista",
                temPropriedade = true,
                meses = LancamentoListaViewModel.mesesDoCabecalho(YearMonth.now(), LocalDate.now())
            )
        )
    }
}
