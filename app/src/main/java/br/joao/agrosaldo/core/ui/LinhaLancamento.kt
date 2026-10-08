package br.joao.agrosaldo.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.core.util.emRealCurto
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.agro
import java.time.LocalDate

/**
 * Linha de lançamento para uso dentro de um [CartaoSafra] sem preenchimento: a margem
 * fica na linha para que a área de toque ocupe toda a largura.
 *
 * O sinal "+"/"−" acompanha a cor para não depender só dela (acessibilidade).
 *
 * @param apoio linha secundária; omitida se vazia.
 */
@Composable
fun LinhaLancamento(
    lancamento: Lancamento,
    apoio: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    margem: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
) {
    val oculto = LocalValoresOcultos.current
    val cor = if (lancamento.ehReceita) MaterialTheme.agro.receita else MaterialTheme.agro.despesa
    val sinal = if (lancamento.ehReceita) "+ " else "− "

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Abrir lançamento", onClick = aoClicar)
            .padding(margem),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconeCategoria(lancamento.categoria)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = lancamento.categoria.rotulo,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (apoio.isNotBlank()) {
                Text(
                    text = apoio,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = sinal + lancamento.valorCentavos.emRealCurto(oculto),
            style = MaterialTheme.typography.titleSmall,
            color = cor,
            modifier = Modifier.valorEscondido(oculto)
        )
    }
}

/** "Ontem · Venda de milho", ou só o dia se não houver descrição. */
fun apoioComData(dia: String, descricao: String): String =
    if (descricao.isBlank()) dia else "$dia · $descricao"

@Preview(showBackground = true)
@Composable
private fun LinhaLancamentoPreview() {
    AgroSaldoTheme {
        CartaoSafra(preenchimento = 0.dp, modifier = Modifier.padding(16.dp)) {
            LinhaLancamento(
                lancamento = Lancamento(
                    propriedadeId = "p1",
                    tipo = TipoLancamento.RECEITA,
                    categoria = CategoriaLancamento.VENDA_SAFRA,
                    valorCentavos = 1_800_000L,
                    data = LocalDate.now(),
                    descricao = "Venda de milho"
                ),
                apoio = "Ontem · Venda de milho",
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
                apoio = "2 out · Conta do pivô",
                aoClicar = {}
            )
        }
    }
}
