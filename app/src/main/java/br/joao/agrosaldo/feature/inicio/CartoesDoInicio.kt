package br.joao.agrosaldo.feature.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.core.ui.CartaoSafra
import br.joao.agrosaldo.core.ui.LinhaLancamento
import br.joao.agrosaldo.core.ui.LocalValoresOcultos
import br.joao.agrosaldo.core.ui.TituloDeCartao
import br.joao.agrosaldo.core.ui.apoioComData
import br.joao.agrosaldo.core.ui.valorEscondido
import br.joao.agrosaldo.core.util.emRealCurto
import br.joao.agrosaldo.core.util.rotulo
import br.joao.agrosaldo.core.util.rotuloRelativoCurto
import br.joao.agrosaldo.domain.financeiro.GastoPorCategoria
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro
import java.time.LocalDate

/*
 * Cartões do corpo do Início: atalhos, chuva, maiores gastos e últimos lançamentos.
 */

/** Atalhos Despesa/Receita/Histórico, com rolagem horizontal para fontes grandes. */
@Composable
internal fun Atalhos(aoNovaDespesa: () -> Unit, aoNovaReceita: () -> Unit, aoAbrirHistorico: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Espacos.margemDaTela),
        horizontalArrangement = Arrangement.spacedBy(Espacos.pequeno)
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
internal fun Atalho(rotulo: String, descricao: String, icone: ImageVector, corDoIcone: Color, aoClicar: () -> Unit) {
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
            modifier = Modifier.padding(horizontal = 18.dp, vertical = Espacos.medio),
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
internal fun CartaoChuva(chuva: CartaoChuvaUi?, aoClicar: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = aoClicar,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.agro.cartaoClima,
        contentColor = MaterialTheme.agro.sobreCartaoClima,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Espacos.largo),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacos.padrao)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Espacos.minimo)
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
internal fun CartaoGastos(gastos: List<GastoPorCategoria>, modifier: Modifier = Modifier) {
    val oculto = LocalValoresOcultos.current
    CartaoSafra(modifier = modifier) {
        TituloDeCartao("Pra onde foi o dinheiro")
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(Espacos.medio)) {
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
internal fun Barra(proporcao: Float, oculto: Boolean) {
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
internal fun CartaoUltimos(
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
            modifier = Modifier.padding(start = Espacos.margemDaTela, end = Espacos.medio, top = Espacos.medio, bottom = 2.dp)
        )
        if (lancamentos.isEmpty()) {
            Text(
                text = "Nenhum lançamento neste mês. Toque no + verde-limão lá embaixo para lançar o primeiro.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Espacos.margemDaTela,
                    end = Espacos.margemDaTela,
                    top = Espacos.minimo,
                    bottom = Espacos.largo
                )
            )
        } else {
            lancamentos.forEach { lancamento ->
                LinhaLancamento(
                    lancamento = lancamento,
                    apoio = apoioComData(lancamento.data.rotuloRelativoCurto(hoje), lancamento.descricao),
                    aoClicar = { aoEditarLancamento(lancamento.id) }
                )
            }
            Spacer(Modifier.height(Espacos.pequeno))
        }
    }
}
