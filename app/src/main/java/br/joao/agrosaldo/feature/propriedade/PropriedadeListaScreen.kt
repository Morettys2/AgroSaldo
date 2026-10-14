package br.joao.agrosaldo.feature.propriedade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.BotaoCabecalho
import br.joao.agrosaldo.core.ui.CabecalhoSafra
import br.joao.agrosaldo.core.ui.Carregando
import br.joao.agrosaldo.core.ui.EstadoErro
import br.joao.agrosaldo.core.ui.EstadoVazio
import br.joao.agrosaldo.core.ui.LocalValoresOcultos
import br.joao.agrosaldo.core.ui.ValorGrande
import br.joao.agrosaldo.core.ui.valorEscondido
import br.joao.agrosaldo.core.util.emReal
import br.joao.agrosaldo.core.util.emRealCurto
import br.joao.agrosaldo.core.util.numeroBr
import br.joao.agrosaldo.core.util.rotulo
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro
import kotlin.math.abs

@Composable
fun PropriedadeListaRoute(
    aoAbrirNova: () -> Unit,
    aoAbrirPropriedade: (String) -> Unit,
    aoAbrirConta: () -> Unit,
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
        aoAbrirConta = aoAbrirConta,
        modifier = modifier
    )
}

/** Lista de propriedades com o saldo somado no cabeçalho e um cartão comparativo por propriedade. */
@Composable
fun PropriedadeListaScreen(
    uiState: PropriedadeListaUiState,
    aoVoltarMes: () -> Unit,
    aoAvancarMes: () -> Unit,
    aoAbrirNova: () -> Unit,
    aoAbrirPropriedade: (String) -> Unit,
    aoAbrirConta: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Espacos.grande),
        verticalArrangement = Arrangement.spacedBy(Espacos.medio)
    ) {
        item(key = "cabecalho") {
            Cabecalho(
                uiState = uiState,
                aoVoltarMes = aoVoltarMes,
                aoAvancarMes = aoAvancarMes,
                aoAbrirConta = aoAbrirConta
            )
        }

        item(key = "nova") {
            BotaoNova(aoClicar = aoAbrirNova, modifier = Modifier.padding(start = Espacos.margemDaTela, end = Espacos.margemDaTela, top = Espacos.pequeno))
        }

        when {
            uiState.carregando -> item(key = "carregando") { Carregando() }

            uiState.erro != null -> item(key = "erro") {
                EstadoErro(uiState.erro, Modifier.padding(horizontal = Espacos.margemDaTela))
            }

            uiState.itens.isEmpty() -> item(key = "vazio") {
                EstadoVazio(
                    texto = "Nenhuma propriedade cadastrada ainda. Toque em \"Nova propriedade\" para começar.",
                    modifier = Modifier.padding(horizontal = Espacos.margemDaTela)
                )
            }

            else -> {
                item(key = "titulo") {
                    Text(
                        text = "Comparativo do mês",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(start = Espacos.grande, end = Espacos.grande, top = Espacos.pequeno)
                            .semantics { heading() }
                    )
                }
                items(items = uiState.itens, key = { it.propriedade.id }) { item ->
                    CartaoPropriedade(
                        item = item,
                        aoClicar = { aoAbrirPropriedade(item.propriedade.id) },
                        modifier = Modifier.padding(horizontal = Espacos.margemDaTela)
                    )
                }
            }
        }
    }
}

@Composable
private fun Cabecalho(
    uiState: PropriedadeListaUiState,
    aoVoltarMes: () -> Unit,
    aoAvancarMes: () -> Unit,
    aoAbrirConta: () -> Unit
) {
    val oculto = LocalValoresOcultos.current
    val negativo = uiState.totalCentavos < 0

    CabecalhoSafra {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Sítios",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.agro.sobreCabecalho,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            BotaoCabecalho(icone = IconesAgro.Conta, descricao = "Abrir conta", aoClicar = aoAbrirConta)
        }

        if (uiState.itens.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Espacos.minimo)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Somando todos em " + uiState.mes.rotulo().lowercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.agro.sobreCabecalhoSecundario,
                        modifier = Modifier.weight(1f)
                    )
                    BotaoCabecalho(icone = IconesAgro.SetaEsquerda, descricao = "Mês anterior", aoClicar = aoVoltarMes)
                    Spacer(Modifier.size(8.dp))
                    BotaoCabecalho(icone = IconesAgro.SetaDireita, descricao = "Próximo mês", aoClicar = aoAvancarMes)
                }
                ValorGrande(
                    texto = uiState.totalCentavos.emReal(oculto),
                    estilo = MaterialTheme.typography.displayMedium,
                    cor = if (negativo && !oculto) MaterialTheme.agro.despesaNoCabecalho else MaterialTheme.agro.sobreCabecalho,
                    modifier = Modifier.valorEscondido(oculto)
                )
            }
        }
    }
}

@Composable
private fun BotaoNova(aoClicar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = aoClicar,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = Espacos.medio),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(IconesAgro.Mais, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("Nova propriedade", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CartaoPropriedade(
    item: PropriedadeComSaldo,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val oculto = LocalValoresOcultos.current
    val negativo = item.saldoCentavos < 0
    // Com valores ocultos, cor e sinal ficam neutros para não revelar saldo negativo.
    val cor = when {
        oculto -> MaterialTheme.colorScheme.secondary
        negativo -> MaterialTheme.agro.despesa
        else -> MaterialTheme.agro.receita
    }

    Surface(
        onClick = aoClicar,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Espacos.largo),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.agro.receitaContainer)
                ) {
                    Icon(
                        imageVector = IconesAgro.Sitio,
                        contentDescription = null,
                        tint = MaterialTheme.agro.sobreReceitaContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).padding(start = Espacos.medio),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.propriedade.nome,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.propriedade.cultura} · ${item.propriedade.areaHectares.numeroBr(agrupar = true)} ha",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    // "−" explícito em saldo negativo, para não depender só da cor.
                    text = (if (negativo && !oculto) "− " else "") + abs(item.saldoCentavos).emRealCurto(oculto),
                    style = MaterialTheme.typography.titleSmall,
                    color = cor,
                    modifier = Modifier.valorEscondido(oculto)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                // Com valores ocultos, fica só a trilha: o tamanho revelaria a proporção entre os saldos.
                if (!oculto) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.proporcao.coerceIn(0.03f, 1f))
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(cor)
                    )
                }
            }
        }
    }
}

// ---------- Previews ----------

@Preview(name = "Fonte 2x", showBackground = true, heightDp = 1300, fontScale = 2f)
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
                            nome = "Fazenda Santa Clara", cultura = "Milho",
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
            aoVoltarMes = {}, aoAvancarMes = {}, aoAbrirNova = {}, aoAbrirPropriedade = {}, aoAbrirConta = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun PreviewVazio() {
    AgroSaldoTheme {
        PropriedadeListaScreen(
            uiState = PropriedadeListaUiState(carregando = false),
            aoVoltarMes = {}, aoAvancarMes = {}, aoAbrirNova = {}, aoAbrirPropriedade = {}, aoAbrirConta = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun PreviewCarregando() {
    AgroSaldoTheme {
        PropriedadeListaScreen(
            uiState = PropriedadeListaUiState(carregando = true),
            aoVoltarMes = {}, aoAvancarMes = {}, aoAbrirNova = {}, aoAbrirPropriedade = {}, aoAbrirConta = {}
        )
    }
}
