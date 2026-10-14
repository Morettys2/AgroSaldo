package br.joao.agrosaldo.feature.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.BotaoCabecalho
import br.joao.agrosaldo.core.ui.CabecalhoSafra
import br.joao.agrosaldo.core.ui.CartaoNoCabecalho
import br.joao.agrosaldo.core.ui.CartaoSafra
import br.joao.agrosaldo.core.ui.LinhaLancamento
import br.joao.agrosaldo.core.ui.LocalValoresOcultos
import br.joao.agrosaldo.core.ui.SeletorPropriedadeCabecalho
import br.joao.agrosaldo.core.ui.TituloDeCartao
import br.joao.agrosaldo.core.ui.ValorGrande
import br.joao.agrosaldo.core.ui.apoioComData
import br.joao.agrosaldo.core.ui.valorEscondido
import br.joao.agrosaldo.core.util.emReal
import br.joao.agrosaldo.core.util.emRealCurto
import br.joao.agrosaldo.core.util.nomeDoMes
import br.joao.agrosaldo.core.util.rotulo
import br.joao.agrosaldo.core.util.rotuloRelativoCurto
import br.joao.agrosaldo.domain.financeiro.GastoPorCategoria
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun InicioRoute(
    aoNovoLancamento: (TipoLancamento) -> Unit,
    aoEditarLancamento: (String) -> Unit,
    aoAbrirClima: () -> Unit,
    aoAbrirHistorico: () -> Unit,
    aoAbrirLancamentos: () -> Unit,
    aoAbrirConta: () -> Unit,
    aoAlternarValores: () -> Unit,
    aoCadastrarPropriedade: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InicioViewModel = viewModel(factory = InicioViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Recalcula textos dependentes do relógio ("Hoje"/"Ontem", chuva) ao retomar.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.aoVoltarParaATela() }

    InicioScreen(
        uiState = uiState,
        aoVoltarMes = viewModel::aoVoltarMes,
        aoAvancarMes = viewModel::aoAvancarMes,
        aoSelecionarPropriedade = viewModel::aoSelecionarPropriedade,
        aoNovoLancamento = aoNovoLancamento,
        aoEditarLancamento = aoEditarLancamento,
        aoAbrirClima = aoAbrirClima,
        aoAbrirHistorico = aoAbrirHistorico,
        aoAbrirLancamentos = aoAbrirLancamentos,
        aoAbrirConta = aoAbrirConta,
        aoAlternarValores = aoAlternarValores,
        aoCadastrarPropriedade = aoCadastrarPropriedade,
        modifier = modifier
    )
}

/**
 * Tela inicial: saldo do mês no cabeçalho, atalhos, cartão de chuva, maiores gastos e
 * últimos lançamentos. Os atalhos "Despesa"/"Receita" chamam [aoNovoLancamento] com o
 * tipo pré-selecionado; o botão genérico de novo lançamento fica na barra inferior.
 */
@Composable
fun InicioScreen(
    uiState: InicioUiState,
    aoVoltarMes: () -> Unit,
    aoAvancarMes: () -> Unit,
    aoSelecionarPropriedade: (String) -> Unit,
    aoNovoLancamento: (TipoLancamento) -> Unit,
    aoEditarLancamento: (String) -> Unit,
    aoAbrirClima: () -> Unit,
    aoAbrirHistorico: () -> Unit,
    aoAbrirLancamentos: () -> Unit,
    aoAbrirConta: () -> Unit,
    aoAlternarValores: () -> Unit,
    aoCadastrarPropriedade: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        // Cabeçalho exibido já no carregamento para manter os ícones claros da status bar.
        uiState.carregando -> Column(modifier.fillMaxSize()) {
            CabecalhoSafra { Spacer(Modifier.height(48.dp)) }
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        !uiState.temPropriedade -> InicioVazio(
            aoCadastrarPropriedade = aoCadastrarPropriedade,
            modifier = modifier
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "cabecalho") {
                Cabecalho(
                    uiState = uiState,
                    aoVoltarMes = aoVoltarMes,
                    aoAvancarMes = aoAvancarMes,
                    aoSelecionarPropriedade = aoSelecionarPropriedade,
                    aoAbrirConta = aoAbrirConta,
                    aoAlternarValores = aoAlternarValores
                )
            }

            item(key = "atalhos") {
                Atalhos(
                    aoNovaDespesa = { aoNovoLancamento(TipoLancamento.DESPESA) },
                    aoNovaReceita = { aoNovoLancamento(TipoLancamento.RECEITA) },
                    aoAbrirHistorico = aoAbrirHistorico
                )
            }

            item(key = "chuva") {
                CartaoChuva(
                    chuva = uiState.chuva,
                    aoClicar = aoAbrirClima,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            if (uiState.gastosPorCategoria.isNotEmpty()) {
                item(key = "gastos") {
                    CartaoGastos(
                        gastos = uiState.gastosPorCategoria,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            item(key = "ultimos") {
                CartaoUltimos(
                    lancamentos = uiState.ultimosLancamentos,
                    hoje = uiState.hoje,
                    aoEditarLancamento = aoEditarLancamento,
                    aoVerTodos = aoAbrirLancamentos,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun Cabecalho(
    uiState: InicioUiState,
    aoVoltarMes: () -> Unit,
    aoAvancarMes: () -> Unit,
    aoSelecionarPropriedade: (String) -> Unit,
    aoAbrirConta: () -> Unit,
    aoAlternarValores: () -> Unit
) {
    val oculto = LocalValoresOcultos.current
    val saldoNegativo = uiState.saldoCentavos < 0

    CabecalhoSafra {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BotaoConta(iniciais = uiState.iniciais, aoClicar = aoAbrirConta)
            SeletorPropriedadeCabecalho(
                nome = uiState.nomePropriedade,
                propriedades = uiState.propriedades,
                aoSelecionar = aoSelecionarPropriedade,
                modifier = Modifier.weight(1f)
            )
            BotaoCabecalho(
                icone = if (oculto) IconesAgro.OlhoFechado else IconesAgro.Olho,
                descricao = if (oculto) "Mostrar valores" else "Esconder valores",
                aoClicar = aoAlternarValores
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Saldo de " + rotuloDoMes(uiState.mes, uiState.hoje),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.agro.sobreCabecalhoSecundario,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                // Botões de mês agrupados longe do botão de ocultar valores, evitando toque acidental.
                BotaoMes(IconesAgro.SetaEsquerda, "Mês anterior", aoVoltarMes)
                Spacer(Modifier.size(8.dp))
                BotaoMes(IconesAgro.SetaDireita, "Próximo mês", aoAvancarMes)
            }
            ValorGrande(
                texto = uiState.saldoCentavos.emReal(oculto),
                estilo = MaterialTheme.typography.displayLarge,
                cor = if (saldoNegativo && !oculto) {
                    MaterialTheme.agro.despesaNoCabecalho
                } else {
                    MaterialTheme.agro.sobreCabecalho
                },
                modifier = Modifier.valorEscondido(oculto)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CartaoNoCabecalho(
                rotulo = "Entrou",
                valor = uiState.receitasCentavos.emRealCurto(oculto),
                corDoValor = MaterialTheme.agro.receitaNoCabecalho,
                oculto = oculto,
                modifier = Modifier.weight(1f)
            )
            CartaoNoCabecalho(
                rotulo = "Saiu",
                valor = uiState.despesasCentavos.emRealCurto(oculto),
                corDoValor = MaterialTheme.agro.despesaNoCabecalho,
                oculto = oculto,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** "outubro", ou "outubro de 2025" quando fora do ano corrente. */
private fun rotuloDoMes(mes: YearMonth, hoje: LocalDate): String =
    if (mes.year == hoje.year) mes.nomeDoMes().lowercase() else mes.rotulo().lowercase()

/** Botão circular com as iniciais do usuário; abre a tela de conta. */
@Composable
private fun BotaoConta(iniciais: String, aoClicar: () -> Unit) {
    Surface(
        onClick = aoClicar,
        shape = CircleShape,
        color = MaterialTheme.agro.destaque,
        contentColor = MaterialTheme.agro.sobreDestaque,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = "Abrir conta" }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = iniciais.ifBlank { "?" },
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun BotaoMes(icone: ImageVector, descricao: String, aoClicar: () -> Unit) {
    BotaoCabecalho(icone = icone, descricao = descricao, aoClicar = aoClicar)
}

/** Atalhos Despesa/Receita/Histórico, com rolagem horizontal para fontes grandes. */
@Composable
private fun Atalhos(
    aoNovaDespesa: () -> Unit,
    aoNovaReceita: () -> Unit,
    aoAbrirHistorico: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Atalho(
            rotulo = "Despesa",
            descricao = "Nova despesa",
            icone = IconesAgro.Mais,
            corDoIcone = MaterialTheme.agro.despesa,
            aoClicar = aoNovaDespesa
        )
        Atalho(
            rotulo = "Receita",
            descricao = "Nova receita",
            icone = IconesAgro.Mais,
            corDoIcone = MaterialTheme.agro.receita,
            aoClicar = aoNovaReceita
        )
        Atalho(
            rotulo = "Histórico",
            descricao = "Histórico do ano",
            icone = IconesAgro.Historico,
            corDoIcone = MaterialTheme.colorScheme.onSurface,
            aoClicar = aoAbrirHistorico
        )
    }
}

@Composable
private fun Atalho(
    rotulo: String,
    descricao: String,
    icone: ImageVector,
    corDoIcone: Color,
    aoClicar: () -> Unit
) {
    Surface(
        onClick = aoClicar,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = descricao }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icone, contentDescription = null, tint = corDoIcone, modifier = Modifier.size(18.dp))
            Text(rotulo, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Cartão de previsão de chuva; com [chuva] nulo (sem cache), vira atalho para a tela Clima. */
@Composable
private fun CartaoChuva(chuva: CartaoChuvaUi?, aoClicar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = aoClicar,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.agro.cartaoClima,
        contentColor = MaterialTheme.agro.sobreCartaoClima,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = chuva?.rotulo ?: "Previsão do tempo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.agro.sobreCartaoClimaSecundario
                )
                Text(
                    text = chuva?.frase ?: "Veja a chuva dos próximos dias.",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = chuva?.detalhe ?: "Toque para abrir o clima",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.agro.sobreCartaoClimaSecundario
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.agro.cartaoClimaElevado)
            ) {
                Icon(
                    imageVector = if (chuva?.vaiChover == false) IconesAgro.Nuvem else IconesAgro.Chuva,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/** Maiores categorias de despesa do mês, com barras proporcionais. */
@Composable
private fun CartaoGastos(gastos: List<GastoPorCategoria>, modifier: Modifier = Modifier) {
    val oculto = LocalValoresOcultos.current
    CartaoSafra(modifier = modifier) {
        TituloDeCartao("Pra onde foi o dinheiro")
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            gastos.forEach { gasto ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    // Agrupa para o TalkBack ler "Energia elétrica, R$ 1.250" como um item.
                    modifier = Modifier.semantics(mergeDescendants = true) {}
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = gasto.categoria.rotulo,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = gasto.totalCentavos.emRealCurto(oculto),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.valorEscondido(oculto)
                        )
                    }
                    Barra(proporcao = gasto.proporcao, oculto = oculto)
                }
            }
        }
    }
}

/**
 * Barra de proporção feita com dois Box, em vez de LinearProgressIndicator (M3 desenha
 * vão e marcador de fim na trilha). Com [oculto], fica só a trilha: o tamanho revelaria
 * a proporção entre os valores.
 */
@Composable
private fun Barra(proporcao: Float, oculto: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (!oculto) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(proporcao.coerceIn(0.03f, 1f))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
            )
        }
    }
}

@Composable
private fun CartaoUltimos(
    lancamentos: List<Lancamento>,
    hoje: LocalDate,
    aoEditarLancamento: (String) -> Unit,
    aoVerTodos: () -> Unit,
    modifier: Modifier = Modifier
) {
    CartaoSafra(modifier = modifier, preenchimento = 0.dp) {
        TituloDeCartao(
            titulo = "Últimos lançamentos",
            acao = if (lancamentos.isEmpty()) null else "Ver todos",
            aoClicarAcao = aoVerTodos,
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 2.dp)
        )
        if (lancamentos.isEmpty()) {
            Text(
                text = "Nenhum lançamento neste mês. Toque no + verde-limão lá embaixo para lançar o primeiro.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp)
            )
        } else {
            lancamentos.forEach { lancamento ->
                LinhaLancamento(
                    lancamento = lancamento,
                    apoio = apoioComData(lancamento.data.rotuloRelativoCurto(hoje), lancamento.descricao),
                    aoClicar = { aoEditarLancamento(lancamento.id) }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Estado vazio: nenhuma propriedade cadastrada. */
@Composable
private fun InicioVazio(aoCadastrarPropriedade: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        CabecalhoSafra {
            Text(
                text = "Bem-vindo ao AgroSaldo",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.agro.sobreCabecalho,
                modifier = Modifier.semantics { heading() }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = IconesAgro.Sitio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Comece cadastrando um sítio",
                style = MaterialTheme.typography.headlineSmall,
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.agro.destaque,
                    contentColor = MaterialTheme.agro.sobreDestaque
                ),
                modifier = Modifier.heightIn(min = 56.dp)
            ) {
                Icon(IconesAgro.Mais, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = "Cadastrar propriedade",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

// ---------- prévias ----------

private val ESTADO_DE_EXEMPLO = InicioUiState(
    carregando = false,
    temPropriedade = true,
    nomePropriedade = "Fazenda Santa Clara",
    iniciais = "FM",
    saldoCentavos = 1_565_000L,
    receitasCentavos = 1_800_000L,
    despesasCentavos = 235_000L,
    proporcaoReceitas = 0.88f,
    gastosPorCategoria = listOf(
        GastoPorCategoria(CategoriaLancamento.ENERGIA, 125_000L, 1f),
        GastoPorCategoria(CategoriaLancamento.ADUBO, 78_000L, 0.62f),
        GastoPorCategoria(CategoriaLancamento.COMBUSTIVEL, 32_000L, 0.26f)
    ),
    chuva = CartaoChuvaUi(
        rotulo = "Previsão · próximas 12 h",
        frase = "Chuva às 18h. Dá para adiar o pivô.",
        detalhe = "92% de chance · 5 mm",
        vaiChover = true
    ),
    ultimosLancamentos = listOf(
        Lancamento(
            propriedadeId = "p1", tipo = TipoLancamento.RECEITA,
            categoria = CategoriaLancamento.VENDA_SAFRA,
            valorCentavos = 1_800_000L, data = LocalDate.now().minusDays(1),
            descricao = "Venda de milho"
        ),
        Lancamento(
            propriedadeId = "p1", tipo = TipoLancamento.DESPESA,
            categoria = CategoriaLancamento.ENERGIA,
            valorCentavos = 125_000L, data = LocalDate.now().minusDays(2),
            descricao = "Conta do pivô"
        )
    )
)

@Composable
private fun InicioDeExemplo(uiState: InicioUiState) {
    InicioScreen(
        uiState = uiState,
        aoVoltarMes = {}, aoAvancarMes = {}, aoSelecionarPropriedade = {},
        aoNovoLancamento = {}, aoEditarLancamento = {}, aoAbrirClima = {}, aoAbrirHistorico = {},
        aoAbrirLancamentos = {}, aoAbrirConta = {}, aoAlternarValores = {}, aoCadastrarPropriedade = {},
        modifier = Modifier.background(MaterialTheme.colorScheme.background)
    )
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun InicioPreview() {
    AgroSaldoTheme { InicioDeExemplo(ESTADO_DE_EXEMPLO) }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun InicioEscuroPreview() {
    AgroSaldoTheme(escuro = true) { InicioDeExemplo(ESTADO_DE_EXEMPLO) }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun InicioVazioPreview() {
    AgroSaldoTheme { InicioDeExemplo(InicioUiState(carregando = false, temPropriedade = false)) }
}
