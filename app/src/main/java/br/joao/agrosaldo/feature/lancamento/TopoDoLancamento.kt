package br.joao.agrosaldo.feature.lancamento

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.joao.agrosaldo.core.ui.BotaoCabecalho
import br.joao.agrosaldo.core.ui.cor
import br.joao.agrosaldo.core.ui.icone
import br.joao.agrosaldo.core.util.emReal
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro

/*
 * Topo do formulário de lançamento: tipo (despesa/receita), valor e o menu da edição.
 */

/**
 * Cabeçalho com seletor de tipo e valor.
 *
 * @param reduzido oculta a legenda e reduz o valor (tela compacta ou IME aberto).
 */
@Composable
internal fun Topo(
    uiState: LancamentoFormUiState,
    reduzido: Boolean,
    aoFechar: () -> Unit,
    aoMudarTipo: (TipoLancamento) -> Unit,
    aoLancarDeNovo: () -> Unit,
    aoExcluir: () -> Unit
) {
    val ehDespesa = uiState.tipo == TipoLancamento.DESPESA

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = if (reduzido) 16.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(if (reduzido) 12.dp else 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacos.pequeno)
        ) {
            BotaoCabecalho(icone = IconesAgro.Fechar, descricao = "Fechar sem salvar", aoClicar = aoFechar)
            SeletorDeTipo(ehDespesa = ehDespesa, aoMudarTipo = aoMudarTipo, modifier = Modifier.weight(1f))
            if (uiState.ehEdicao) {
                MenuDaEdicao(aoLancarDeNovo = aoLancarDeNovo, aoExcluir = aoExcluir)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espacos.minimo)
        ) {
            if (!reduzido) {
                Text(
                    text = if (ehDespesa) "Quanto saiu?" else "Quanto entrou?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.agro.sobreCabecalhoSecundario
                )
            }
            // autoSize garante que o valor máximo (R$ 9.999.999,99) caiba em telas estreitas.
            BasicText(
                text = uiState.valorCentavos.emReal(),
                style = MaterialTheme.typography.displayLarge.copy(
                    color = MaterialTheme.agro.sobreCabecalho,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-2).sp
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = if (reduzido) 40.sp else 52.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    // liveRegion: o TalkBack anuncia o valor a cada tecla.
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Valor: " + uiState.valorCentavos.emReal()
                    }
            )
            val erro = uiState.erroValor
            Text(
                text = erro ?: uiState.resumo,
                style = MaterialTheme.typography.labelLarge,
                color = if (erro != null) MaterialTheme.agro.despesaNoCabecalho else MaterialTheme.agro.sobreCabecalhoSecundario,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (erro != null) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier
            )
        }
    }
}

/** Alternador Despesa/Receita; a opção marcada muda cor e peso da fonte (não só a cor). */
@Composable
internal fun SeletorDeTipo(ehDespesa: Boolean, aoMudarTipo: (TipoLancamento) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.agro.cabecalhoElevado)
            .padding(Espacos.minimo)
            .selectableGroup()
    ) {
        OpcaoDeTipo(
            rotulo = "Despesa",
            selecionada = ehDespesa,
            corSelecionada = MaterialTheme.agro.despesaNoCabecalho,
            aoClicar = { aoMudarTipo(TipoLancamento.DESPESA) }
        )
        OpcaoDeTipo(
            rotulo = "Receita",
            selecionada = !ehDespesa,
            corSelecionada = MaterialTheme.agro.receitaNoCabecalho,
            aoClicar = { aoMudarTipo(TipoLancamento.RECEITA) }
        )
    }
}

@Composable
internal fun RowScope.OpcaoDeTipo(rotulo: String, selecionada: Boolean, corSelecionada: Color, aoClicar: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (selecionada) corSelecionada else Color.Transparent)
            .selectable(selected = selecionada, role = Role.RadioButton, onClick = aoClicar)
    ) {
        Text(
            text = rotulo,
            style = if (selecionada) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            color = if (selecionada) MaterialTheme.agro.sobreDestaque else MaterialTheme.agro.sobreCabecalho
        )
    }
}

/** Menu de overflow da edição: "Lançar de novo" e "Excluir". */
@Composable
internal fun MenuDaEdicao(aoLancarDeNovo: () -> Unit, aoExcluir: () -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        BotaoCabecalho(icone = IconesAgro.MaisOpcoes, descricao = "Mais opções", aoClicar = { aberto = true })
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            DropdownMenuItem(
                text = { Text("Lançar de novo") },
                leadingIcon = { Icon(IconesAgro.Copiar, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = {
                    aberto = false
                    aoLancarDeNovo()
                }
            )
            HorizontalDivider()
            // Sem confirmação: a exclusão é desfeita pelo snackbar "Desfazer".
            DropdownMenuItem(
                text = { Text("Excluir lançamento", color = MaterialTheme.agro.despesa) },
                leadingIcon = {
                    Icon(IconesAgro.Lixeira, contentDescription = null, tint = MaterialTheme.agro.despesa, modifier = Modifier.size(20.dp))
                },
                onClick = {
                    aberto = false
                    aoExcluir()
                }
            )
        }
    }
}
