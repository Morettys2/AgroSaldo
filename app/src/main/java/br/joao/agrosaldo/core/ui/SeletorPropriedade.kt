package br.joao.agrosaldo.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.core.util.TextosComuns
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro

/**
 * Nome da propriedade selecionada, com um menu para trocar. Com uma só propriedade, o
 * menu fica desligado e a seta some.
 *
 * Antes eram dois componentes parecidos (um para o cabeçalho escuro e outro para o corpo
 * das telas), com área de toque e acessibilidade diferentes. Agora é um só; [noCabecalho]
 * troca apenas as cores.
 *
 * @param aoSelecionar recebe o id da propriedade escolhida.
 * @param noCabecalho true sobre o cabeçalho escuro (Início, Lançamentos, Clima); false no
 *   fundo claro (Histórico).
 */
@Composable
fun SeletorPropriedade(
    nome: String,
    propriedades: List<Propriedade>,
    aoSelecionar: (String) -> Unit,
    modifier: Modifier = Modifier,
    noCabecalho: Boolean = true
) {
    var aberto by remember { mutableStateOf(false) }
    val podeTrocar = propriedades.size > 1
    val corDoRotulo = if (noCabecalho) MaterialTheme.agro.sobreCabecalhoSecundario else MaterialTheme.colorScheme.onSurfaceVariant
    val corDoNome = if (noCabecalho) MaterialTheme.agro.sobreCabecalho else MaterialTheme.colorScheme.onBackground

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .heightIn(min = Espacos.alvoDeToque)
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = podeTrocar, role = Role.DropdownList, onClickLabel = "Trocar propriedade") {
                    aberto = true
                }
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Propriedade", style = MaterialTheme.typography.labelSmall, color = corDoRotulo)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nome.ifBlank { TextosComuns.SEM_PROPRIEDADE },
                    style = MaterialTheme.typography.titleMedium,
                    color = corDoNome,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (podeTrocar) {
                    Icon(
                        imageVector = IconesAgro.SetaBaixo,
                        // A ação já é anunciada pelo onClickLabel.
                        contentDescription = null,
                        tint = corDoNome,
                        modifier = Modifier.padding(start = Espacos.minimo).size(18.dp)
                    )
                }
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
