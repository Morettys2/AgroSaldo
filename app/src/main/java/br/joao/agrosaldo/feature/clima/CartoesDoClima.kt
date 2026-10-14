package br.joao.agrosaldo.feature.clima

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.core.ui.BotaoTonal
import br.joao.agrosaldo.core.ui.CartaoSafra
import br.joao.agrosaldo.core.ui.TituloDeCartao
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro

/*
 * Cartões do corpo do Clima: recomendação do pivô, próximos dias e permissão de notificação.
 */

/** Recomendação de irrigação calculada pelo [ClimaViewModel]. */
@Composable
internal fun CartaoRecomendacao(uiState: ClimaUiState, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.agro.cartaoClima,
        contentColor = MaterialTheme.agro.sobreCartaoClima,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Espacos.largo),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.agro.cartaoClimaElevado)
            ) {
                Icon(
                    imageVector = if (uiState.vaiChover) IconesAgro.Chuva else IconesAgro.Confirmado,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = uiState.recomendacaoTitulo,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = uiState.recomendacaoTexto,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.agro.sobreCartaoClimaSecundario
                )
            }
        }
    }
}

@Composable
internal fun CartaoDias(dias: List<DiaUi>, modifier: Modifier = Modifier) {
    CartaoSafra(modifier = modifier, preenchimento = 0.dp) {
        TituloDeCartao(
            titulo = "Próximos 7 dias",
            modifier = Modifier.padding(start = Espacos.margemDaTela, end = Espacos.margemDaTela, top = 18.dp, bottom = Espacos.minimo)
        )
        Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = Espacos.pequeno)) {
            dias.forEachIndexed { indice, dia ->
                if (indice > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinhaDia(dia)
            }
        }
    }
}

@Composable
internal fun LinhaDia(dia: DiaUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacos.medio),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = Espacos.medio)
            // Leitor de tela anuncia a linha como um único item.
            .semantics(mergeDescendants = true) {}
    ) {
        Text(text = dia.rotulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(56.dp)) {
            Text(
                text = "${dia.probabilidade}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.agro.clima
            )
            Text(
                text = dia.chuva,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.agro.tintaApagada
            )
        }
        Icon(
            imageVector = dia.condicao.icone(),
            contentDescription = dia.condicao.rotulo,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = "${dia.minima}  ${dia.maxima}",
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp)
        )
    }
}

@Composable
internal fun CartaoPermissao(aoPermitir: () -> Unit, modifier: Modifier = Modifier) {
    CartaoSafra(modifier = modifier) {
        Text(
            text = "Receba o aviso de chuva",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Para avisar quando a chuva chegar perto, o app precisa da sua permissão para mandar notificações.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Espacos.medio))
        BotaoTonal(texto = "Permitir notificações", aoClicar = aoPermitir, icone = IconesAgro.Sino)
    }
}

// ---------- previews ----------
