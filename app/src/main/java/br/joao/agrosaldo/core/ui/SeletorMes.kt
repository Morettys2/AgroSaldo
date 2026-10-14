package br.joao.agrosaldo.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.core.util.rotulo
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import java.time.YearMonth

/** Navegação entre meses (RF10), usada no Início e em Lançamentos. */
@Composable
fun SeletorMes(
    mes: YearMonth,
    aoVoltar: () -> Unit,
    aoAvancar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Espacos.minimo),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = aoVoltar) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Mês anterior",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = mes.rotulo(),
            style = MaterialTheme.typography.labelLarge
        )

        IconButton(onClick = aoAvancar) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Próximo mês",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SeletorMesPreview() {
    AgroSaldoTheme {
        SeletorMes(mes = YearMonth.now(), aoVoltar = {}, aoAvancar = {})
    }
}
