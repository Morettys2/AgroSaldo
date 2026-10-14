package br.joao.agrosaldo.feature.lancamento

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro

/*
 * Teclado numérico do formulário de lançamento: o valor é digitado como numa
 * calculadora de feira, em centavos ("1", "2", "5" = R$ 1,25).
 */

/** Teclado 1–9, 00, 0 e ⌫ (toque apaga um dígito; long press zera o valor). */
@Composable
internal fun TecladoNumerico(aoTeclar: (String) -> Unit, aoApagar: () -> Unit, aoLimpar: () -> Unit) {
    val linhas = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(verticalArrangement = Arrangement.spacedBy(Espacos.minimo)) {
        linhas.forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                linha.forEach { tecla -> TeclaDigito(tecla, aoTeclar) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TeclaDigito("00", aoTeclar)
            TeclaDigito("0", aoTeclar)
            TeclaApagar(aoApagar = aoApagar, aoLimpar = aoLimpar)
        }
    }
}

@Composable
internal fun RowScope.TeclaDigito(tecla: String, aoTeclar: (String) -> Unit) {
    Surface(
        onClick = { aoTeclar(tecla) },
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 52.dp)
            .semantics { contentDescription = if (tecla == "00") "Zero zero" else tecla }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(tecla, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RowScope.TeclaApagar(aoApagar: () -> Unit, aoLimpar: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Apagar o último número",
                onLongClickLabel = "Apagar o valor inteiro",
                onLongClick = aoLimpar,
                onClick = aoApagar
            )
            .semantics { contentDescription = "Apagar" }
    ) {
        Icon(
            imageVector = IconesAgro.Apagar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(26.dp)
        )
    }
}
