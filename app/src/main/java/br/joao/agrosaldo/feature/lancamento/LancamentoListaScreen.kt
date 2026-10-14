package br.joao.agrosaldo.feature.lancamento

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.BotaoCabecalho
import br.joao.agrosaldo.core.ui.CabecalhoSafra
import br.joao.agrosaldo.core.ui.Carregando
import br.joao.agrosaldo.core.ui.CartaoSafra
import br.joao.agrosaldo.core.ui.ChipSafra
import br.joao.agrosaldo.core.ui.Espacos.margemDaTela
import br.joao.agrosaldo.core.ui.EstadoErro
import br.joao.agrosaldo.core.ui.EstadoVazio
import br.joao.agrosaldo.core.ui.LinhaLancamento
import br.joao.agrosaldo.core.ui.LocalValoresOcultos
import br.joao.agrosaldo.core.ui.SeletorPropriedade
import br.joao.agrosaldo.core.ui.ValorGrande
import br.joao.agrosaldo.core.ui.icone
import br.joao.agrosaldo.core.ui.sangrarNasLaterais
import br.joao.agrosaldo.core.ui.valorEscondido
import br.joao.agrosaldo.core.util.emRealComSinal
import br.joao.agrosaldo.core.util.emRealCurto
import br.joao.agrosaldo.core.util.rotulo
import br.joao.agrosaldo.core.util.siglaDoMes
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

@Composable
fun LancamentoListaRoute(
    aoEditarLancamento: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LancamentoListaViewModel = viewModel(factory = LancamentoListaViewModel.Factory)
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
                        modifier = Modifier.padding(start = Espacos.margemDaTela, end = Espacos.margemDaTela, top = 18.dp, bottom = Espacos.padrao)
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

@Composable
private fun Cabecalho(
    uiState: LancamentoListaUiState,
    buscaAberta: Boolean,
    focarBusca: Boolean,
    aoFocarBusca: () -> Unit,
    aoAbrirBusca: () -> Unit,
    aoFecharBusca: () -> Unit,
    aoBuscar: (String) -> Unit,
    aoSelecionarMes: (YearMonth) -> Unit,
    aoSelecionarPropriedade: (String) -> Unit
) {
    CabecalhoSafra {
        if (buscaAberta) {
            CampoDeBusca(
                texto = uiState.busca,
                focar = focarBusca,
                aoFocar = aoFocarBusca,
                aoMudar = aoBuscar,
                aoFechar = aoFecharBusca
            )
            Text(
                text = when {
                    !uiState.buscando -> "Procure pelo nome da categoria ou pela descrição."
                    uiState.quantidade == 1 -> "1 resultado em todos os meses"
                    else -> "${uiState.quantidade} resultados em todos os meses"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.agro.sobreCabecalhoSecundario,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Lançamentos",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.agro.sobreCabecalho,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                BotaoCabecalho(
                    icone = IconesAgro.Busca,
                    descricao = "Buscar lançamento",
                    aoClicar = aoAbrirBusca,
                    habilitado = uiState.temPropriedade
                )
            }
            if (uiState.propriedades.size > 1) {
                SeletorPropriedade(
                    nome = uiState.nomePropriedade,
                    propriedades = uiState.propriedades,
                    aoSelecionar = aoSelecionarPropriedade
                )
            }
            PilulasDeMes(meses = uiState.meses, selecionado = uiState.mes, aoSelecionar = aoSelecionarMes)
            if (uiState.temPropriedade) ResumoDoMes(uiState)
        }
    }
}

@Composable
private fun CampoDeBusca(
    texto: String,
    focar: Boolean,
    aoFocar: () -> Unit,
    aoMudar: (String) -> Unit,
    aoFechar: () -> Unit
) {
    val foco = remember { FocusRequester() }
    val gerenteDeFoco = LocalFocusManager.current
    LaunchedEffect(focar) {
        if (focar) {
            foco.requestFocus()
            aoFocar()
        }
    }

    TextField(
        value = texto,
        onValueChange = aoMudar,
        singleLine = true,
        placeholder = { Text("Buscar lançamento", style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(IconesAgro.Busca, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            IconButton(onClick = aoFechar) {
                Icon(IconesAgro.Fechar, contentDescription = "Fechar busca", modifier = Modifier.size(20.dp))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { gerenteDeFoco.clearFocus() }),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.agro.cabecalhoElevado,
            unfocusedContainerColor = MaterialTheme.agro.cabecalhoElevado,
            focusedTextColor = MaterialTheme.agro.sobreCabecalho,
            unfocusedTextColor = MaterialTheme.agro.sobreCabecalho,
            focusedPlaceholderColor = MaterialTheme.agro.sobreCabecalhoSecundario,
            unfocusedPlaceholderColor = MaterialTheme.agro.sobreCabecalhoSecundario,
            focusedLeadingIconColor = MaterialTheme.agro.sobreCabecalho,
            unfocusedLeadingIconColor = MaterialTheme.agro.sobreCabecalho,
            focusedTrailingIconColor = MaterialTheme.agro.sobreCabecalho,
            unfocusedTrailingIconColor = MaterialTheme.agro.sobreCabecalho,
            cursorColor = MaterialTheme.agro.destaque,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(foco)
    )
}

/** Seletor horizontal de meses. O selecionado e os janeiros exibem o ano ("Out 2026"). */
@Composable
private fun PilulasDeMes(meses: List<YearMonth>, selecionado: YearMonth, aoSelecionar: (YearMonth) -> Unit) {
    val indice = meses.indexOf(selecionado).coerceAtLeast(0)
    // Mantém dois meses anteriores visíveis à esquerda do selecionado.
    val estado = rememberLazyListState(initialFirstVisibleItemIndex = (indice - 2).coerceAtLeast(0))
    LaunchedEffect(indice) { estado.animateScrollToItem((indice - 2).coerceAtLeast(0)) }

    LazyRow(
        state = estado,
        horizontalArrangement = Arrangement.spacedBy(Espacos.pequeno),
        // Ocupa a largura total (anula a margem do cabeçalho) e devolve a margem como contentPadding.
        contentPadding = PaddingValues(horizontal = Espacos.margemDaTela),
        modifier = Modifier
            .fillMaxWidth()
            .sangrarNasLaterais(Espacos.margemDaTela)
    ) {
        items(items = meses, key = { it.toString() }) { mes ->
            val ativo = mes == selecionado
            val texto = if (ativo || mes.month == Month.JANUARY) "${mes.siglaDoMes()} ${mes.year}" else mes.siglaDoMes()
            Surface(
                onClick = { aoSelecionar(mes) },
                shape = CircleShape,
                color = if (ativo) MaterialTheme.agro.destaque else MaterialTheme.agro.cabecalhoElevado,
                contentColor = if (ativo) MaterialTheme.agro.sobreDestaque else MaterialTheme.agro.sobreCabecalho,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .semantics {
                        selected = ativo
                        contentDescription = mes.rotulo()
                    }
            ) {
                Box(Modifier.padding(horizontal = Espacos.padrao, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(texto, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ResumoDoMes(uiState: LancamentoListaUiState) {
    val oculto = LocalValoresOcultos.current
    Row(verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(
                text = "Saldo do mês",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.agro.sobreCabecalhoSecundario
            )
            ValorGrande(
                texto = uiState.saldoCentavos.emRealCurto(oculto),
                estilo = MaterialTheme.typography.displaySmall,
                cor = if (uiState.saldoCentavos < 0 && !oculto) {
                    MaterialTheme.agro.despesaNoCabecalho
                } else {
                    MaterialTheme.agro.sobreCabecalho
                },
                tamanhoMinimo = 18.sp,
                modifier = Modifier.valorEscondido(oculto)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "+ " + uiState.receitasCentavos.emRealCurto(oculto),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.agro.receitaNoCabecalho,
                modifier = Modifier.semantics {
                    contentDescription = if (oculto) "Entrou: valor escondido" else "Entrou " + uiState.receitasCentavos.emRealCurto()
                }
            )
            Text(
                text = "− " + uiState.despesasCentavos.emRealCurto(oculto),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.agro.despesaNoCabecalho,
                modifier = Modifier.semantics {
                    contentDescription = if (oculto) "Saiu: valor escondido" else "Saiu " + uiState.despesasCentavos.emRealCurto()
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filtros(
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
private fun FiltroDeCategoria(
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
private fun ResumoDoFiltro(quantidade: Int, totalCentavos: Long, mostrarLimpar: Boolean, aoLimpar: () -> Unit) {
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

/**
 * Emite títulos de dia e linhas como itens separados da LazyColumn, desenhados como um
 * único cartão: [ItemDoCartao] arredonda o topo do primeiro item e a base do último.
 */
private fun LazyListScope.itensDaLista(
    grupos: List<GrupoDoDia>,
    aoEditarLancamento: (String) -> Unit
) {
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
    nome = "Fazenda Santa Clara", cultura = "Milho",
    areaHectares = 50.0, latitude = -21.7333, longitude = -46.9667
)

private fun estadoDeExemplo(): LancamentoListaUiState {
    val hoje = LocalDate.now()
    val lancamentos = listOf(
        Lancamento(
            propriedadeId = "p1", tipo = TipoLancamento.DESPESA,
            categoria = CategoriaLancamento.ADUBO,
            valorCentavos = 78_000L, data = hoje
        ),
        Lancamento(
            propriedadeId = "p1", tipo = TipoLancamento.RECEITA,
            categoria = CategoriaLancamento.VENDA_SAFRA,
            valorCentavos = 1_800_000L, data = hoje.minusDays(1),
            descricao = "Venda de milho"
        ),
        Lancamento(
            propriedadeId = "p1", tipo = TipoLancamento.DESPESA,
            categoria = CategoriaLancamento.ENERGIA,
            valorCentavos = 125_000L, data = hoje.minusDays(2),
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
