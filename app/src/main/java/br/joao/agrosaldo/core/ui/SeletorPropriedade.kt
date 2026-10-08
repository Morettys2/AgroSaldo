package br.joao.agrosaldo.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.domain.model.Propriedade

/**
 * Nome da propriedade selecionada com dropdown para trocá-la. Com uma única
 * propriedade, o dropdown fica desabilitado e o ícone é omitido.
 *
 * @param aoSelecionar recebe o id da propriedade escolhida.
 */
@Composable
fun SeletorPropriedade(
    nome: String,
    propriedades: List<Propriedade>,
    aoSelecionar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var aberto by remember { mutableStateOf(false) }
    val podeTrocar = propriedades.size > 1

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(enabled = podeTrocar, role = Role.DropdownList, onClickLabel = "Trocar propriedade") {
                    aberto = true
                }
        ) {
            Text(
                text = nome.ifBlank { "Nenhuma propriedade" },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (podeTrocar) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    // A ação já é anunciada pelo onClickLabel do Row.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            propriedades.forEach { propriedade ->
                DropdownMenuItem(
                    text = { Text(propriedade.nome) },
                    onClick = {
                        aoSelecionar(propriedade.id)
                        aberto = false
                    }
                )
            }
        }
    }
}
