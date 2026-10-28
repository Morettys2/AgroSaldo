package br.joao.agrosaldo.feature.historico

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.joao.agrosaldo.core.ui.Carregando
import br.joao.agrosaldo.core.ui.EstadoErro
import br.joao.agrosaldo.core.ui.EstadoVazio
import br.joao.agrosaldo.core.ui.LocalValoresOcultos
import br.joao.agrosaldo.core.ui.SeletorPropriedade
import br.joao.agrosaldo.core.ui.ValorGrande
import br.joao.agrosaldo.core.ui.textoComValor
import br.joao.agrosaldo.core.ui.valorEscondido
import br.joao.agrosaldo.core.util.emReal
import br.joao.agrosaldo.core.util.emRealCurto
import br.joao.agrosaldo.core.util.nomeDoMes
import br.joao.agrosaldo.core.util.siglaDoMes
import br.joao.agrosaldo.domain.financeiro.GastoPorCategoria
import br.joao.agrosaldo.domain.financeiro.MesDoAno
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.agro
import java.time.YearMonth

@Composable
fun HistoricoRoute(aoAbrirMes: () -> Unit, modifier: Modifier = Modifier, viewModel: HistoricoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HistoricoScreen(
        uiState = uiState,
        aoVoltarAno = viewModel::aoVoltarAno,
        aoAvancarAno = viewModel::aoAvancarAno,
        aoSelecionarPropriedade = viewModel::aoSelecionarPropriedade,
        aoAbrirMes = { mes ->
            viewModel.aoAbrirMes(mes)
            aoAbrirMes()
        },
        modifier = modifier
    )
}

@Composable
fun HistoricoScreen(
    uiState: HistoricoUiState,
    aoVoltarAno: () -> Unit,
    aoAvancarAno: () -> Unit,
    aoSelecionarPropriedade: (String) -> Unit,
    aoAbrirMes: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.carregando) {
        Carregando(modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Espacos.enorme),
        verticalArrangement = Arrangement.spacedBy(Espacos.medio)
    ) {
        item {
            SeletorPropriedade(
                nome = uiState.nomePropriedade,
                propriedades = uiState.propriedades,
                aoSelecionar = aoSelecionarPropriedade,
                noCabecalho = false,
                modifier = Modifier.padding(start = Espacos.margemDaTela, end = Espacos.margemDaTela, top = Espacos.minimo)
            )
        }

        uiState.erro?.let { erro ->
            item { EstadoErro(erro, Modifier.padding(horizontal = Espacos.margemDaTela)) }
        }

        item {
            SeletorAno(
                ano = uiState.ano,
                podeAvancar = uiState.podeAvancar,
                aoVoltar = aoVoltarAno,
                aoAvancar = aoAvancarAno
            )
        }

        if (!uiState.temMovimento) {
            item {
                EstadoVazio(
                    texto = "Nenhum lançamento em ${uiState.ano}.",
                    modifier = Modifier.padding(horizontal = Espacos.margemDaTela)
                )
            }
            return@LazyColumn
        }

        item { CartaoDoAno(uiState, Modifier.padding(horizontal = Espacos.margemDaTela)) }

        item {
            CartaoGrafico(
                meses = uiState.meses,
                maiorValor = uiState.maiorValorMensal,
                modifier = Modifier.padding(horizontal = Espacos.margemDaTela)
            )
        }

        if (uiState.melhorMes != null) {
            item {
                Destaques(
                    melhor = uiState.melhorMes,
                    pior = uiState.piorMes,
                    modifier = Modifier.padding(horizontal = Espacos.margemDaTela)
                )
            }
        }

        if (uiState.gastosDoAno.isNotEmpty()) {
            item { Secao("Maiores gastos do ano") }
            item { CartaoGastos(uiState.gastosDoAno, Modifier.padding(horizontal = Espacos.margemDaTela)) }
        }

        item { Secao("Mês a mês · toque para ver os lançamentos") }

        items(items = uiState.mesesNaLista, key = { it.mes.toString() }) { mes ->
            LinhaMes(mes = mes, aoClicar = { aoAbrirMes(mes.mes) })
        }
    }
}

@Composable
private fun SeletorAno(ano: Int, podeAvancar: Boolean, aoVoltar: () -> Unit, aoAvancar: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Espacos.minimo),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = aoVoltar) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Ano anterior")
        }
        Text(text = ano.toString(), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = aoAvancar, enabled = podeAvancar) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Próximo ano")
        }
    }
}

@Composable
private fun CartaoDoAno(uiState: HistoricoUiState, modifier: Modifier = Modifier) {
    val oculto = LocalValoresOcultos.current
    val negativo = uiState.saldoCentavos < 0

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.agro.cabecalho),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Espacos.grande)) {
            Text(
                "Saldo de ${uiState.ano}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.agro.sobreCabecalhoSecundario
            )
            ValorGrande(
                texto = uiState.saldoCentavos.emReal(oculto),
                estilo = MaterialTheme.typography.displayMedium,
                cor = if (negativo && !oculto) MaterialTheme.agro.despesaNoCabecalho else MaterialTheme.agro.sobreCabecalho,
                modifier = Modifier.valorEscondido(oculto)
            )
            Spacer(Modifier.height(Espacos.medio))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Entrou  " + uiState.receitasCentavos.emRealCurto(oculto),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.agro.receitaNoCabecalho,
                    modifier = Modifier.textoComValor("Entrou", oculto)
                )
                Text(
                    "Saiu  " + uiState.despesasCentavos.emRealCurto(oculto),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.agro.despesaNoCabecalho,
                    modifier = Modifier.textoComValor("Saiu", oculto)
                )
            }
        }
    }
}

/**
 * Gráfico de barras receita/despesa por mês, desenhado em Canvas e escalado por [maiorValor].
 * Para acessibilidade, o contentDescription traz o resumo textual dos meses com movimento.
 */
@Composable
private fun CartaoGrafico(meses: List<MesDoAno>, maiorValor: Long, modifier: Modifier = Modifier) {
    val oculto = LocalValoresOcultos.current
    val corReceita = MaterialTheme.agro.receita
    val corDespesa = MaterialTheme.agro.despesa
    val corBase = MaterialTheme.colorScheme.outlineVariant

    val descricao = if (oculto) {
        "Gráfico do ano, com os valores escondidos."
    } else {
        meses.filter { it.temMovimento }.joinToString(". ") {
            "${it.mes.nomeDoMes()}: entrou ${it.receitasCentavos.emRealCurto()}, saiu ${it.despesasCentavos.emRealCurto()}"
        }
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Espacos.padrao), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Espacos.padrao)) {
                Legenda("Entrou", corReceita)
                Legenda("Saiu", corDespesa)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .semantics { contentDescription = "Gráfico do ano. $descricao" }
            ) {
                val larguraMes = size.width / meses.size
                val larguraBarra = larguraMes * 0.32f
                val folga = larguraMes * 0.06f
                val canto = CornerRadius(3.dp.toPx())
                // Altura mínima para valores pequenos não desaparecerem.
                val alturaMinima = 2.dp.toPx()

                fun altura(valor: Long): Float = when {
                    valor <= 0L || maiorValor <= 0L -> 0f
                    else -> (valor.toFloat() / maiorValor * size.height).coerceAtLeast(alturaMinima)
                }

                // Com valores ocultos, as barras somem: a altura revelaria a proporção entre os meses.
                if (!oculto) {
                    meses.forEachIndexed { indice, mes ->
                        val inicio = indice * larguraMes + (larguraMes - 2 * larguraBarra - folga) / 2
                        val opacidade = if (mes.futuro) 0.4f else 1f

                        val alturaReceita = altura(mes.receitasCentavos)
                        drawRoundRect(
                            color = corReceita.copy(alpha = opacidade),
                            topLeft = Offset(inicio, size.height - alturaReceita),
                            size = Size(larguraBarra, alturaReceita),
                            cornerRadius = canto
                        )
                        val alturaDespesa = altura(mes.despesasCentavos)
                        drawRoundRect(
                            color = corDespesa.copy(alpha = opacidade),
                            topLeft = Offset(inicio + larguraBarra + folga, size.height - alturaDespesa),
                            size = Size(larguraBarra, alturaDespesa),
                            cornerRadius = canto
                        )
                    }
                }

                drawLine(
                    color = corBase,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            Row(Modifier.fillMaxWidth()) {
                meses.forEach { mes ->
                    Text(
                        text = mes.mes.siglaDoMes(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Legenda(texto: String, cor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = CircleShape, color = cor, modifier = Modifier.size(10.dp)) {}
        Text(texto, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Destaques(melhor: MesDoAno, pior: MesDoAno?, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Espacos.medio)) {
        Destaque("Melhor mês", melhor, Modifier.weight(1f))
        if (pior != null) Destaque("Mês mais fraco", pior, Modifier.weight(1f))
    }
}

@Composable
private fun Destaque(titulo: String, mes: MesDoAno, modifier: Modifier = Modifier) {
    val oculto = LocalValoresOcultos.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(mes.mes.nomeDoMes(), style = MaterialTheme.typography.titleMedium)
            Text(
                text = mes.saldoCentavos.emRealCurto(oculto),
                style = MaterialTheme.typography.bodyMedium,
                color = corDoSaldo(mes.saldoCentavos, oculto),
                modifier = Modifier.valorEscondido(oculto)
            )
        }
    }
}

@Composable
private fun CartaoGastos(gastos: List<GastoPorCategoria>, modifier: Modifier = Modifier) {
    val oculto = LocalValoresOcultos.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Espacos.largo), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            gastos.forEach { gasto ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(gasto.categoria.rotulo, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            gasto.totalCentavos.emRealCurto(oculto),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.valorEscondido(oculto)
                        )
                    }
                    LinearProgressIndicator(
                        // Com valores ocultos, fica só a trilha.
                        progress = { if (oculto) 0f else gasto.proporcao },
                        color = MaterialTheme.agro.despesa,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun LinhaMes(mes: MesDoAno, aoClicar: () -> Unit) {
    val oculto = LocalValoresOcultos.current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Ver os lançamentos do mês", onClick = aoClicar)
                .padding(horizontal = Espacos.margemDaTela, vertical = Espacos.medio)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(mes.mes.nomeDoMes(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (mes.temMovimento) {
                        "Entrou ${mes.receitasCentavos.emRealCurto(oculto)} · Saiu ${mes.despesasCentavos.emRealCurto(oculto)}"
                    } else {
                        "Sem lançamentos"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.agro.tintaApagada,
                    modifier = if (oculto && mes.temMovimento) {
                        Modifier.semantics { contentDescription = "Entrou e saiu: valores escondidos" }
                    } else {
                        Modifier
                    }
                )
            }
            if (mes.temMovimento) {
                Text(
                    text = mes.saldoCentavos.emRealCurto(oculto),
                    style = MaterialTheme.typography.bodyLarge,
                    color = corDoSaldo(mes.saldoCentavos, oculto),
                    modifier = Modifier.valorEscondido(oculto)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.agro.tintaApagada
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = Espacos.margemDaTela)
        )
    }
}

@Composable
private fun Secao(titulo: String) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Espacos.margemDaTela, end = Espacos.margemDaTela, top = Espacos.medio)
    )
}

@Preview(name = "Fonte 2x", showBackground = true, heightDp = 2800, fontScale = 2f)
@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun HistoricoPreview() {
    val ano = 2026
    val receitas = listOf(0L, 0L, 1_200_000L, 0L, 3_500_000L, 900_000L, 0L, 0L, 1_800_000L, 0L, 0L, 0L)
    val despesas = listOf(180_000L, 210_000L, 260_000L, 190_000L, 420_000L, 240_000L, 200_000L, 230_000L, 235_000L, 0L, 0L, 0L)
    val meses = (1..12).map {
        MesDoAno(YearMonth.of(ano, it), receitas[it - 1], despesas[it - 1], futuro = it > 9)
    }
    AgroSaldoTheme {
        HistoricoScreen(
            uiState = HistoricoUiState(
                carregando = false,
                temPropriedade = true,
                nomePropriedade = "Fazenda Modelo",
                ano = ano,
                meses = meses,
                receitasCentavos = receitas.sum(),
                despesasCentavos = despesas.sum(),
                maiorValorMensal = 3_500_000L,
                melhorMes = meses[4],
                piorMes = meses[3],
                gastosDoAno = listOf(
                    GastoPorCategoria(CategoriaLancamento.ENERGIA, 1_250_000L, 1f),
                    GastoPorCategoria(CategoriaLancamento.ADUBO, 640_000L, 0.51f),
                    GastoPorCategoria(CategoriaLancamento.COMBUSTIVEL, 280_000L, 0.22f)
                )
            ),
            aoVoltarAno = {},
            aoAvancarAno = {},
            aoSelecionarPropriedade = {},
            aoAbrirMes = {}
        )
    }
}

/** Cor pelo sinal do saldo; neutra com valores ocultos, para a cor não revelar o sinal. */
@Composable
private fun corDoSaldo(saldoCentavos: Long, oculto: Boolean): Color = when {
    oculto -> MaterialTheme.colorScheme.onSurface
    saldoCentavos < 0 -> MaterialTheme.agro.despesa
    else -> MaterialTheme.agro.receita
}
