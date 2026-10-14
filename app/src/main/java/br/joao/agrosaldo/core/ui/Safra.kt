package br.joao.agrosaldo.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro

/*
 * Componentes visuais compartilhados das telas principais: cabeçalho escuro e seus
 * controles, cartões do corpo e chips.
 */

/**
 * Cabeçalho escuro das abas, com cantos inferiores arredondados. O fundo se estende
 * sob a status bar; statusBarsPadding afasta apenas o conteúdo.
 */
@Composable
fun CabecalhoSafra(
    modifier: Modifier = Modifier,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(MaterialTheme.agro.cabecalho)
            .statusBarsPadding()
            .padding(start = Espacos.margemDaTela, end = Espacos.margemDaTela, top = Espacos.medio, bottom = Espacos.grande),
        verticalArrangement = Arrangement.spacedBy(Espacos.largo),
        content = conteudo
    )
}

/**
 * Estende o conteúdo por [margem] em cada lado, anulando o padding do pai (ex.: linha
 * rolável de borda a borda). Equivale a uma margem negativa, que o Compose não suporta.
 */
fun Modifier.sangrarNasLaterais(margem: Dp): Modifier = layout { medida, limites ->
    val extra = margem.roundToPx() * 2
    val desenhado = medida.measure(limites.offset(horizontal = extra))
    layout(width = desenhado.width - extra, height = desenhado.height) {
        desenhado.place(x = -extra / 2, y = 0)
    }
}

/** Botão circular de 48dp para o cabeçalho. */
@Composable
fun BotaoCabecalho(
    icone: ImageVector,
    descricao: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    Surface(
        onClick = aoClicar,
        enabled = habilitado,
        shape = CircleShape,
        color = MaterialTheme.agro.cabecalhoElevado,
        contentColor = MaterialTheme.agro.sobreCabecalho,
        modifier = modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icone, contentDescription = descricao, modifier = Modifier.size(22.dp))
        }
    }
}

/** Rótulo e valor no cabeçalho, mesclados em um único nó de acessibilidade. */
@Composable
fun CartaoNoCabecalho(
    rotulo: String,
    valor: String,
    corDoValor: Color,
    modifier: Modifier = Modifier,
    oculto: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.agro.cabecalhoElevado)
            .padding(horizontal = 14.dp, vertical = Espacos.medio)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.agro.sobreCabecalhoSecundario)
        Text(
            text = valor,
            style = MaterialTheme.typography.titleMedium,
            color = corDoValor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.valorEscondido(oculto)
        )
    }
}

/**
 * Valor monetário em destaque que reduz a fonte até [tamanhoMinimo] para caber em uma
 * linha, evitando truncar o número (o que exibiria um valor errado).
 */
@Composable
fun ValorGrande(
    texto: String,
    estilo: TextStyle,
    cor: Color,
    modifier: Modifier = Modifier,
    tamanhoMinimo: TextUnit = 20.sp,
    alinhamento: TextAlign = TextAlign.Start
) {
    BasicText(
        text = texto,
        style = estilo.copy(color = cor, textAlign = alinhamento),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = tamanhoMinimo,
            // Fallback para estilo sem fontSize (Unspecified quebraria o autoSize).
            maxFontSize = estilo.fontSize.takeIf { it.isSpecified } ?: 34.sp
        ),
        modifier = modifier
    )
}

/** Cartão de superfície do corpo das telas (shapes.large). */
@Composable
fun CartaoSafra(
    modifier: Modifier = Modifier,
    preenchimento: Dp = 20.dp,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(preenchimento), content = conteudo)
    }
}

/** Título de cartão com ação textual opcional à direita. Marcado como heading para navegação por acessibilidade. */
@Composable
fun TituloDeCartao(
    titulo: String,
    modifier: Modifier = Modifier,
    acao: String? = null,
    aoClicarAcao: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        if (acao != null) {
            Text(
                text = acao,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = aoClicarAcao)
                    .padding(horizontal = Espacos.pequeno, vertical = 14.dp)
            )
        }
    }
}

/** FilterChip do app. O ícone de confirmação indica seleção sem depender só da cor. */
@Composable
fun ChipSafra(
    rotulo: String,
    selecionado: Boolean,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    iconeFinal: ImageVector? = null
) {
    FilterChip(
        selected = selecionado,
        onClick = aoClicar,
        label = { Text(rotulo, style = MaterialTheme.typography.labelLarge, maxLines = 1) },
        leadingIcon = if (selecionado) {
            { Icon(IconesAgro.Confirmado, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
        trailingIcon = iconeFinal?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) } },
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.secondary,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondary
        ),
        modifier = modifier.heightIn(min = 40.dp)
    )
}
