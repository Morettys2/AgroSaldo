package br.joao.agrosaldo.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro

private data class Aba(val rota: String, val rotulo: String, val icone: ImageVector)

private val ABAS = listOf(
    Aba(Rotas.INICIO, "Início", IconesAgro.Inicio),
    Aba(Rotas.LANCAMENTOS, "Lançamentos", IconesAgro.Lista),
    Aba(Rotas.CLIMA, "Clima", IconesAgro.Nuvem),
    Aba(Rotas.PROPRIEDADES, "Sítios", IconesAgro.Sitio)
)

/**
 * Barra inferior flutuante em pílula, com o botão de novo lançamento no centro.
 * Rótulos usam autoSize para não truncar em telas estreitas.
 */
@Composable
internal fun BarraInferior(
    rotaAtual: NavDestination?,
    aoSelecionar: (String) -> Unit,
    aoNovoLancamento: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Espacos.medio, end = Espacos.medio, top = 6.dp, bottom = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 8.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.agro.cabecalho)
                .padding(6.dp)
                .selectableGroup()
        ) {
            ABAS.forEachIndexed { indice, aba ->
                // Botão "+" no centro da barra.
                if (indice == ABAS.size / 2) BotaoNovoLancamento(aoNovoLancamento)
                ItemDaBarra(
                    aba = aba,
                    selecionada = rotaAtual?.hierarchy?.any { it.route == aba.rota } == true,
                    aoClicar = { aoSelecionar(aba.rota) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.ItemDaBarra(aba: Aba, selecionada: Boolean, aoClicar: () -> Unit) {
    val corDoIcone = if (selecionada) MaterialTheme.agro.destaque else MaterialTheme.agro.sobreCabecalho
    val corDoNome = if (selecionada) MaterialTheme.agro.destaque else MaterialTheme.agro.sobreCabecalhoSecundario
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(if (selecionada) MaterialTheme.agro.cabecalhoElevado else Color.Transparent)
            .selectable(selected = selecionada, role = Role.Tab, onClick = aoClicar)
    ) {
        Icon(aba.icone, contentDescription = null, tint = corDoIcone, modifier = Modifier.size(22.dp))
        BasicText(
            text = aba.rotulo,
            style = MaterialTheme.typography.labelSmall.copy(color = corDoNome),
            maxLines = 1,
            // 11 sp é o mínimo legível; abaixo disso o nome da aba some para quem usa fonte grande.
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 12.sp),
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun BotaoNovoLancamento(aoClicar: () -> Unit) {
    Surface(
        onClick = aoClicar,
        shape = CircleShape,
        color = MaterialTheme.agro.destaque,
        contentColor = MaterialTheme.agro.sobreDestaque,
        modifier = Modifier
            .width(60.dp)
            .heightIn(min = 52.dp)
            .semantics { contentDescription = "Novo lançamento" }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(IconesAgro.Mais, contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}
