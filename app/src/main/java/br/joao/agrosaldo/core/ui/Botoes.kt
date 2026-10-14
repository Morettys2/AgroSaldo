package br.joao.agrosaldo.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro

/*
 * Botões do app. Antes eram uns 12 refeitos à mão em cada tela, com `.height(56.dp)` fixo
 * (cortava o texto com a fonte do sistema grande) e alturas 48/52/56/60 misturadas.
 *
 * Todos usam altura MÍNIMA (`heightIn`), então crescem quando o texto quebra em duas linhas.
 */

/** Tamanho do botão: [GRANDE] para a ação da tela, [COMPACTO] para ações dentro de cartões. */
enum class TamanhoBotao { GRANDE, COMPACTO }

/**
 * Ação principal da tela (Entrar, Salvar ajustes…), na cor primária.
 *
 * @param carregando troca o texto por um indicador e desliga o botão (ex.: login em andamento).
 */
@Composable
fun BotaoPrincipal(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    carregando: Boolean = false,
    icone: ImageVector? = null,
    tamanho: TamanhoBotao = TamanhoBotao.GRANDE
) {
    Button(
        onClick = aoClicar,
        enabled = habilitado && !carregando,
        shape = CircleShape,
        modifier = modifier.heightIn(min = alturaMinima(tamanho))
    ) {
        ConteudoDoBotao(texto, icone, carregando, tamanho)
    }
}

/**
 * Ação de destaque, no verde-limão (salvar lançamento, cadastrar a primeira propriedade).
 * Um por tela, no máximo: é o botão que o olho acha primeiro.
 */
@Composable
fun BotaoDestaque(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    carregando: Boolean = false,
    icone: ImageVector? = null,
    tamanho: TamanhoBotao = TamanhoBotao.GRANDE
) {
    Button(
        onClick = aoClicar,
        enabled = habilitado && !carregando,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.agro.destaque,
            contentColor = MaterialTheme.agro.sobreDestaque
        ),
        modifier = modifier.heightIn(min = alturaMinima(tamanho))
    ) {
        ConteudoDoBotao(texto, icone, carregando, tamanho)
    }
}

/**
 * Ação de apoio, em verde cheio de segundo plano (ex.: "Permitir notificações").
 * Normalmente [TamanhoBotao.COMPACTO], dentro de um cartão.
 */
@Composable
fun BotaoTonal(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    icone: ImageVector? = null,
    tamanho: TamanhoBotao = TamanhoBotao.COMPACTO
) {
    Button(
        onClick = aoClicar,
        enabled = habilitado,
        shape = CircleShape,
        colors = coresTonais(),
        modifier = modifier.heightIn(min = alturaMinima(tamanho))
    ) {
        ConteudoDoBotao(texto, icone, carregando = false, tamanho = tamanho)
    }
}

/**
 * Ação secundária, só com contorno (Sair da conta, Usar minha localização).
 *
 * @param perigo pinta texto e borda na cor de despesa, para ações que apagam algo. É cor
 *   de despesa, não de erro: excluir é uma ação legítima, não uma falha.
 */
@Composable
fun BotaoSecundario(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    carregando: Boolean = false,
    icone: ImageVector? = null,
    perigo: Boolean = false,
    tamanho: TamanhoBotao = TamanhoBotao.GRANDE
) {
    val cor = if (perigo) MaterialTheme.agro.despesa else Color.Unspecified
    OutlinedButton(
        onClick = aoClicar,
        enabled = habilitado && !carregando,
        shape = CircleShape,
        border = if (perigo) BorderStroke(1.dp, MaterialTheme.agro.despesa) else ButtonDefaults.outlinedButtonBorder(habilitado && !carregando),
        colors = if (perigo) ButtonDefaults.outlinedButtonColors(contentColor = cor) else ButtonDefaults.outlinedButtonColors(),
        modifier = modifier.heightIn(min = alturaMinima(tamanho))
    ) {
        ConteudoDoBotao(texto, icone, carregando, tamanho)
    }
}

@Composable
private fun coresTonais(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.secondary,
    contentColor = MaterialTheme.colorScheme.onSecondary
)

private fun alturaMinima(tamanho: TamanhoBotao) = when (tamanho) {
    TamanhoBotao.GRANDE -> Espacos.alturaDoBotao
    TamanhoBotao.COMPACTO -> Espacos.alvoDeToque
}

@Composable
private fun RowScope.ConteudoDoBotao(
    texto: String,
    icone: ImageVector?,
    carregando: Boolean,
    tamanho: TamanhoBotao
) {
    val estilo = when (tamanho) {
        TamanhoBotao.GRANDE -> MaterialTheme.typography.titleMedium
        TamanhoBotao.COMPACTO -> MaterialTheme.typography.labelLarge
    }
    if (carregando) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = LocalContentColor.current,
            modifier = Modifier.size(22.dp)
        )
        return
    }
    if (icone != null) {
        Icon(icone, contentDescription = null, modifier = Modifier.size(20.dp))
    }
    Text(
        text = texto,
        style = estilo,
        textAlign = TextAlign.Center,
        modifier = if (icone != null) Modifier.padding(start = Espacos.pequeno) else Modifier
    )
}

@Preview(name = "Botões", showBackground = true)
@Preview(name = "Botões · fonte 2x", showBackground = true, fontScale = 2f)
@Composable
private fun BotoesPreview() {
    AgroSaldoTheme {
        Column(
            modifier = Modifier.padding(Espacos.largo),
            verticalArrangement = Arrangement.spacedBy(Espacos.medio)
        ) {
            BotaoPrincipal("Entrar", aoClicar = {})
            BotaoPrincipal("Entrar", aoClicar = {}, carregando = true)
            BotaoDestaque("Salvar despesa", aoClicar = {}, icone = IconesAgro.Mais)
            BotaoTonal("Permitir notificações", aoClicar = {}, icone = IconesAgro.Sino)
            BotaoSecundario("Sair da conta", aoClicar = {})
            BotaoSecundario("Excluir propriedade", aoClicar = {}, perigo = true)
        }
    }
}
