package br.joao.agrosaldo.feature.lancamento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.joao.agrosaldo.core.ui.BotaoCabecalho
import br.joao.agrosaldo.core.ui.CabecalhoSafra
import br.joao.agrosaldo.core.ui.LocalValoresOcultos
import br.joao.agrosaldo.core.ui.SeletorPropriedade
import br.joao.agrosaldo.core.ui.ValorGrande
import br.joao.agrosaldo.core.ui.icone
import br.joao.agrosaldo.core.ui.sangrarNasLaterais
import br.joao.agrosaldo.core.ui.valorEscondido
import br.joao.agrosaldo.core.util.emRealCurto
import br.joao.agrosaldo.core.util.rotulo
import br.joao.agrosaldo.core.util.siglaDoMes
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro
import java.time.Month
import java.time.YearMonth

/*
 * Cabeçalho escuro da lista de lançamentos: propriedade, busca, meses e o resumo do mês.
 */

@Composable
internal fun Cabecalho(
    uiState: LancamentoListaUiState,
    buscaAberta: Boolean,
    focarBusca: Boolean,
    aoFocarBusca: () -> Unit,
    aoAbrirBusca: () -> Unit,
    aoFecharBusca: () -> Unit,
    aoBuscar: (String) -> Unit,
    aoSelecionarMes: (YearMonth) -> Unit,
    aoSelecionarPropriedade: (String) -> Unit
) {
    CabecalhoSafra {
        if (buscaAberta) {
            CampoDeBusca(
                texto = uiState.busca,
                focar = focarBusca,
                aoFocar = aoFocarBusca,
                aoMudar = aoBuscar,
                aoFechar = aoFecharBusca
            )
            Text(
                text = when {
                    !uiState.buscando -> "Procure pelo nome da categoria ou pela descrição."
                    uiState.quantidade == 1 -> "1 resultado em todos os meses"
                    else -> "${uiState.quantidade} resultados em todos os meses"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.agro.sobreCabecalhoSecundario,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Lançamentos",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.agro.sobreCabecalho,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                BotaoCabecalho(
                    icone = IconesAgro.Busca,
                    descricao = "Buscar lançamento",
                    aoClicar = aoAbrirBusca,
                    habilitado = uiState.temPropriedade
                )
            }
            if (uiState.propriedades.size > 1) {
                SeletorPropriedade(
                    nome = uiState.nomePropriedade,
                    propriedades = uiState.propriedades,
                    aoSelecionar = aoSelecionarPropriedade
                )
            }
            PilulasDeMes(meses = uiState.meses, selecionado = uiState.mes, aoSelecionar = aoSelecionarMes)
            if (uiState.temPropriedade) ResumoDoMes(uiState)
        }
    }
}

@Composable
internal fun CampoDeBusca(texto: String, focar: Boolean, aoFocar: () -> Unit, aoMudar: (String) -> Unit, aoFechar: () -> Unit) {
    val foco = remember { FocusRequester() }
    val gerenteDeFoco = LocalFocusManager.current
    // Sempre a versão mais nova do callback, sem reiniciar o efeito quando ela muda.
    val aoFocarAtual by rememberUpdatedState(aoFocar)
    LaunchedEffect(focar) {
        if (focar) {
            foco.requestFocus()
            aoFocarAtual()
        }
    }

    TextField(
        value = texto,
        onValueChange = aoMudar,
        singleLine = true,
        placeholder = { Text("Buscar lançamento", style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(IconesAgro.Busca, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            IconButton(onClick = aoFechar) {
                Icon(IconesAgro.Fechar, contentDescription = "Fechar busca", modifier = Modifier.size(20.dp))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { gerenteDeFoco.clearFocus() }),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.agro.cabecalhoElevado,
            unfocusedContainerColor = MaterialTheme.agro.cabecalhoElevado,
            focusedTextColor = MaterialTheme.agro.sobreCabecalho,
            unfocusedTextColor = MaterialTheme.agro.sobreCabecalho,
            focusedPlaceholderColor = MaterialTheme.agro.sobreCabecalhoSecundario,
            unfocusedPlaceholderColor = MaterialTheme.agro.sobreCabecalhoSecundario,
            focusedLeadingIconColor = MaterialTheme.agro.sobreCabecalho,
            unfocusedLeadingIconColor = MaterialTheme.agro.sobreCabecalho,
            focusedTrailingIconColor = MaterialTheme.agro.sobreCabecalho,
            unfocusedTrailingIconColor = MaterialTheme.agro.sobreCabecalho,
            cursorColor = MaterialTheme.agro.destaque,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(foco)
    )
}

/** Seletor horizontal de meses. O selecionado e os janeiros exibem o ano ("Out 2026"). */
@Composable
internal fun PilulasDeMes(meses: List<YearMonth>, selecionado: YearMonth, aoSelecionar: (YearMonth) -> Unit) {
    val indice = meses.indexOf(selecionado).coerceAtLeast(0)
    // Mantém dois meses anteriores visíveis à esquerda do selecionado.
    val estado = rememberLazyListState(initialFirstVisibleItemIndex = (indice - 2).coerceAtLeast(0))
    LaunchedEffect(indice) { estado.animateScrollToItem((indice - 2).coerceAtLeast(0)) }

    LazyRow(
        state = estado,
        horizontalArrangement = Arrangement.spacedBy(Espacos.pequeno),
        // Ocupa a largura total (anula a margem do cabeçalho) e devolve a margem como contentPadding.
        contentPadding = PaddingValues(horizontal = Espacos.margemDaTela),
        modifier = Modifier
            .fillMaxWidth()
            .sangrarNasLaterais(Espacos.margemDaTela)
    ) {
        items(items = meses, key = { it.toString() }) { mes ->
            val ativo = mes == selecionado
            val texto = if (ativo || mes.month == Month.JANUARY) "${mes.siglaDoMes()} ${mes.year}" else mes.siglaDoMes()
            Surface(
                onClick = { aoSelecionar(mes) },
                shape = CircleShape,
                color = if (ativo) MaterialTheme.agro.destaque else MaterialTheme.agro.cabecalhoElevado,
                contentColor = if (ativo) MaterialTheme.agro.sobreDestaque else MaterialTheme.agro.sobreCabecalho,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .semantics {
                        selected = ativo
                        contentDescription = mes.rotulo()
                    }
            ) {
                Box(Modifier.padding(horizontal = Espacos.padrao, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(texto, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun ResumoDoMes(uiState: LancamentoListaUiState) {
    val oculto = LocalValoresOcultos.current
    Row(verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(
                text = "Saldo do mês",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.agro.sobreCabecalhoSecundario
            )
            ValorGrande(
                texto = uiState.saldoCentavos.emRealCurto(oculto),
                estilo = MaterialTheme.typography.displaySmall,
                cor = if (uiState.saldoCentavos < 0 && !oculto) {
                    MaterialTheme.agro.despesaNoCabecalho
                } else {
                    MaterialTheme.agro.sobreCabecalho
                },
                tamanhoMinimo = 18.sp,
                modifier = Modifier.valorEscondido(oculto)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "+ " + uiState.receitasCentavos.emRealCurto(oculto),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.agro.receitaNoCabecalho,
                modifier = Modifier.semantics {
                    contentDescription = if (oculto) "Entrou: valor escondido" else "Entrou " + uiState.receitasCentavos.emRealCurto()
                }
            )
            Text(
                text = "− " + uiState.despesasCentavos.emRealCurto(oculto),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.agro.despesaNoCabecalho,
                modifier = Modifier.semantics {
                    contentDescription = if (oculto) "Saiu: valor escondido" else "Saiu " + uiState.despesasCentavos.emRealCurto()
                }
            )
        }
    }
}
