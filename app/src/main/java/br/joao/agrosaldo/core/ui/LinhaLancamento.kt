package br.joao.agrosaldo.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.core.util.emRealCurto
import br.joao.agrosaldo.core.util.formatarCurto
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.agro
import java.time.LocalDate

/**
 * Cartão de um lançamento na lista.
 *
 * Usado em Início e em Lançamentos, por isso mora em core/ui.
 *
 * A cor sozinha nunca carrega o significado: além do verde ou do vermelho,
 * o valor traz "+" ou "−" e o ícone aponta para cima ou para baixo. Quem for
 * daltônico, ou estiver no sol forte do meio da lavoura, continua entendendo.
 */
@Composable
fun LinhaLancamento(
    lancamento: Lancamento,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cor = if (lancamento.ehReceita) MaterialTheme.agro.receita else MaterialTheme.agro.despesa
    val corFundo = if (lancamento.ehReceita) {
        MaterialTheme.agro.receitaContainer
    } else {
        MaterialTheme.agro.despesaContainer
    }
    val icone = if (lancamento.ehReceita) {
        Icons.AutoMirrored.Filled.TrendingUp
    } else {
        Icons.AutoMirrored.Filled.TrendingDown
    }
    val sinal = if (lancamento.ehReceita) "+ " else "− "

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = aoClicar)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = corFundo,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icone,
                        contentDescription = null,
                        tint = cor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = lancamento.categoria.rotulo,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = buildString {
                        append(lancamento.data.formatarCurto())
                        if (lancamento.descricao.isNotBlank()) {
                            append(" · ")
                            append(lancamento.descricao)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = sinal + lancamento.valorCentavos.emRealCurto(),
                style = MaterialTheme.typography.bodyLarge,
                color = cor
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LinhaLancamentoPreview() {
    AgroSaldoTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LinhaLancamento(
                lancamento = Lancamento(
                    propriedadeId = "p1",
                    tipo = TipoLancamento.RECEITA,
                    categoria = CategoriaLancamento.VENDA_SAFRA,
                    valorCentavos = 1_800_000L,
                    data = LocalDate.now(),
                    descricao = "Venda de milho"
                ),
                aoClicar = {}
            )
            LinhaLancamento(
                lancamento = Lancamento(
                    propriedadeId = "p1",
                    tipo = TipoLancamento.DESPESA,
                    categoria = CategoriaLancamento.ENERGIA,
                    valorCentavos = 125_000L,
                    data = LocalDate.now(),
                    descricao = "Conta do pivô"
                ),
                aoClicar = {}
            )
        }
    }
}
