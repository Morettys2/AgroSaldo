package br.joao.agrosaldo.feature.lancamento

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.BotaoCabecalho
import br.joao.agrosaldo.core.ui.cor
import br.joao.agrosaldo.core.ui.icone
import br.joao.agrosaldo.core.util.emMillisUtc
import br.joao.agrosaldo.core.util.emReal
import br.joao.agrosaldo.core.util.paraLocalDateOuNulo
import br.joao.agrosaldo.core.util.paraLocalDateUtc
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro
import java.time.LocalDate

@Composable
fun LancamentoFormRoute(
    lancamentoId: String?,
    aoConcluir: () -> Unit,
    aoLancarDeNovo: (String) -> Unit,
    aoCadastrarPropriedade: () -> Unit,
    modifier: Modifier = Modifier,
    copiaDe: String? = null,
    tipoInicial: TipoLancamento? = null,
    viewModel: LancamentoFormViewModel = viewModel(
        key = "lancamento-form-${lancamentoId.orEmpty()}-${copiaDe.orEmpty()}-${tipoInicial?.name.orEmpty()}",
        factory = LancamentoFormViewModel.Factory(lancamentoId, copiaDe, tipoInicial)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    LaunchedEffect(uiState.salvo, uiState.excluido) {
        if (uiState.salvo || uiState.excluido) aoConcluir()
    }

    LaunchedEffect(uiState.naoEncontrado) {
        if (uiState.naoEncontrado) {
            Toast.makeText(contexto, LancamentoFormViewModel.AVISO_NAO_ENCONTRADO, Toast.LENGTH_SHORT).show()
            aoConcluir()
        }
    }

    LancamentoFormScreen(
        uiState = uiState,
        titulo = when {
            lancamentoId != null -> "Editar lançamento"
            copiaDe != null -> "Lançar de novo"
            else -> "Novo lançamento"
        },
        aoFechar = aoConcluir,
        aoMudarTipo = viewModel::aoMudarTipo,
        aoMudarCategoria = viewModel::aoMudarCategoria,
        aoTeclar = viewModel::aoTeclar,
        aoApagarDigito = viewModel::aoApagarDigito,
        aoLimparValor = viewModel::aoLimparValor,
        aoEscolherData = viewModel::aoEscolherData,
        aoUsarHoje = viewModel::aoUsarHoje,
        aoUsarOntem = viewModel::aoUsarOntem,
        aoMudarPropriedade = viewModel::aoMudarPropriedade,
        aoMudarDescricao = viewModel::aoMudarDescricao,
        aoSalvar = viewModel::aoSalvar,
        aoExcluir = viewModel::aoExcluir,
        aoLancarDeNovo = { lancamentoId?.let(aoLancarDeNovo) },
        aoCadastrarPropriedade = aoCadastrarPropriedade,
        modifier = modifier
    )
}

/**
 * Formulário de lançamento (criação, edição e "Lançar de novo").
 *
 * Usa teclado numérico próprio em vez do IME: layout estável entre aparelhos, tecla "00"
 * garantida e sem cobrir os demais campos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LancamentoFormScreen(
    uiState: LancamentoFormUiState,
    titulo: String,
    aoFechar: () -> Unit,
    aoMudarTipo: (TipoLancamento) -> Unit,
    aoMudarCategoria: (CategoriaLancamento) -> Unit,
    aoTeclar: (String) -> Unit,
    aoApagarDigito: () -> Unit,
    aoLimparValor: () -> Unit,
    aoEscolherData: (LocalDate) -> Unit,
    aoUsarHoje: () -> Unit,
    aoUsarOntem: () -> Unit,
    aoMudarPropriedade: (String) -> Unit,
    aoMudarDescricao: (String) -> Unit,
    aoSalvar: () -> Unit,
    aoExcluir: () -> Unit,
    aoLancarDeNovo: () -> Unit,
    aoCadastrarPropriedade: () -> Unit,
    modifier: Modifier = Modifier
) {
    var escolhendoData by rememberSaveable { mutableStateOf(false) }
    // Com o IME aberto (campo de descrição), o teclado numérico é ocultado.
    val tecladoDoSistemaAberto = WindowInsets.isImeVisible
    val escalaDaFonte = LocalDensity.current.fontScale.coerceAtLeast(1f)

    val campos: @Composable () -> Unit = {
        CamposDoLancamento(
            uiState = uiState,
            aoMudarCategoria = aoMudarCategoria,
            aoUsarHoje = aoUsarHoje,
            aoUsarOntem = aoUsarOntem,
            aoAbrirCalendario = { escolhendoData = true },
            aoMudarPropriedade = aoMudarPropriedade,
            aoMudarDescricao = aoMudarDescricao,
            aoCadastrarPropriedade = aoCadastrarPropriedade
        )
    }
    val teclado: @Composable () -> Unit = {
        if (!tecladoDoSistemaAberto) {
            TecladoNumerico(aoTeclar = aoTeclar, aoApagar = aoApagarDigito, aoLimpar = aoLimparValor)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.agro.cabecalho)
            .semantics { paneTitle = titulo }
    ) {
        // Tela alta: topo e teclado fixos, só os campos rolam. Tela baixa (ou fonte grande):
        // tudo rola, exceto a barra de salvar.
        val compacto = maxHeight < ALTURA_PARA_TECLADO_FIXO * escalaDaFonte

        Column(modifier = Modifier.fillMaxSize()) {
            if (compacto) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Topo(
                        uiState = uiState,
                        reduzido = true,
                        aoFechar = aoFechar,
                        aoMudarTipo = aoMudarTipo,
                        aoLancarDeNovo = aoLancarDeNovo,
                        aoExcluir = aoExcluir
                    )
                    Folha {
                        Column(
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // No modo compacto o teclado vem antes dos campos, logo abaixo do valor.
                            teclado()
                            campos()
                        }
                    }
                }
            } else {
                Topo(
                    uiState = uiState,
                    reduzido = tecladoDoSistemaAberto,
                    aoFechar = aoFechar,
                    aoMudarTipo = aoMudarTipo,
                    aoLancarDeNovo = aoLancarDeNovo,
                    aoExcluir = aoExcluir
                )
                Folha(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        campos()
                    }
                    Box(Modifier.padding(horizontal = 20.dp)) { teclado() }
                }
            }

            BarraDeSalvar(
                uiState = uiState,
                aoSalvar = aoSalvar
            )
        }
    }

    if (escolhendoData) {
        CalendarioDialogo(
            dataAtual = uiState.data.paraLocalDateOuNulo(),
            ultimaPermitida = uiState.hoje.plusMonths(LancamentoFormViewModel.MESES_NO_FUTURO),
            aoEscolher = aoEscolherData,
            aoFechar = { escolhendoData = false }
        )
    }
}

/**
 * Cabeçalho com seletor de tipo e valor.
 *
 * @param reduzido oculta a legenda e reduz o valor (tela compacta ou IME aberto).
 */
@Composable
private fun Topo(
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
private fun SeletorDeTipo(ehDespesa: Boolean, aoMudarTipo: (TipoLancamento) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.agro.cabecalhoElevado)
            .padding(4.dp)
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
private fun RowScope.OpcaoDeTipo(rotulo: String, selecionada: Boolean, corSelecionada: Color, aoClicar: () -> Unit) {
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
private fun MenuDaEdicao(aoLancarDeNovo: () -> Unit, aoExcluir: () -> Unit) {
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

@Composable
private fun Folha(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(MaterialTheme.colorScheme.background),
        content = conteudo
    )
}

/** Categoria, data, propriedade e descrição; sem propriedades, mostra aviso para cadastrar. */
@Composable
private fun CamposDoLancamento(
    uiState: LancamentoFormUiState,
    aoMudarCategoria: (CategoriaLancamento) -> Unit,
    aoUsarHoje: () -> Unit,
    aoUsarOntem: () -> Unit,
    aoAbrirCalendario: () -> Unit,
    aoMudarPropriedade: (String) -> Unit,
    aoMudarDescricao: (String) -> Unit,
    aoCadastrarPropriedade: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (uiState.carregado && uiState.propriedades.isEmpty()) {
            SemPropriedade(aoCadastrarPropriedade)
        }

        Categorias(
            categorias = uiState.categoriasDisponiveis,
            escolhida = uiState.categoria,
            aoEscolher = aoMudarCategoria
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoData(
                rotulo = uiState.rotuloData,
                erro = uiState.erroData,
                aoUsarHoje = aoUsarHoje,
                aoUsarOntem = aoUsarOntem,
                aoAbrirCalendario = aoAbrirCalendario,
                modifier = Modifier.weight(1f)
            )
            CampoSitio(
                propriedades = uiState.propriedades,
                escolhidaId = uiState.propriedadeId,
                aoEscolher = aoMudarPropriedade,
                modifier = Modifier.weight(1f)
            )
        }

        CampoDescricao(texto = uiState.descricao, aoMudar = aoMudarDescricao)
    }
}

/** Aviso exibido quando não há propriedade cadastrada (o formulário abre mesmo assim). */
@Composable
private fun SemPropriedade(aoCadastrar: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Falta cadastrar um sítio", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Todo lançamento pertence a uma propriedade. Cadastre a primeira e depois volte aqui.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = aoCadastrar,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cadastrar sítio", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Botão de salvar fixo no rodapé, com o erro geral logo acima para ficar sempre visível. */
@Composable
private fun BarraDeSalvar(uiState: LancamentoFormUiState, aoSalvar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        uiState.erroGeral?.let { mensagem ->
            Text(
                text = mensagem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Button(
            onClick = aoSalvar,
            enabled = !uiState.salvando,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.agro.destaque,
                contentColor = MaterialTheme.agro.sobreDestaque
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
        ) {
            Text(
                text = when {
                    uiState.ehEdicao -> "Salvar alterações"
                    uiState.tipo == TipoLancamento.DESPESA -> "Salvar despesa"
                    else -> "Salvar receita"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** Todas as categorias do tipo, sem truncamento. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Categorias(
    categorias: List<CategoriaLancamento>,
    escolhida: CategoriaLancamento,
    aoEscolher: (CategoriaLancamento) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Categoria",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup()
        ) {
            categorias.forEach { categoria ->
                ChipCategoria(
                    categoria = categoria,
                    selecionada = categoria == escolhida,
                    aoClicar = { aoEscolher(categoria) }
                )
            }
        }
    }
}

@Composable
private fun ChipCategoria(categoria: CategoriaLancamento, selecionada: Boolean, aoClicar: () -> Unit) {
    val cor = categoria.cor()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (selecionada) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
            .selectable(selected = selecionada, role = Role.RadioButton, onClick = aoClicar)
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (selecionada) MaterialTheme.agro.destaque else cor.fundo)
        ) {
            Icon(
                // Selecionada exibe ✓, indicando o estado sem depender só da cor.
                imageVector = if (selecionada) IconesAgro.Confirmado else categoria.icone(),
                contentDescription = null,
                tint = if (selecionada) MaterialTheme.agro.sobreDestaque else cor.conteudo,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = categoria.rotulo,
            style = if (selecionada) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (selecionada) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Campo clicável com ícone, legenda e valor (ex.: "Data / Hoje, 4 out"). */
@Composable
private fun CampoDeEscolha(
    icone: ImageVector,
    legenda: String,
    valor: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    erro: Boolean = false
) {
    Surface(
        onClick = aoClicar,
        enabled = habilitado,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (erro) BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null,
        modifier = modifier.heightIn(min = 60.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(20.dp))
            Column {
                Text(legenda, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(valor, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CampoData(
    rotulo: String,
    erro: String?,
    aoUsarHoje: () -> Unit,
    aoUsarOntem: () -> Unit,
    aoAbrirCalendario: () -> Unit,
    modifier: Modifier = Modifier
) {
    var aberto by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            CampoDeEscolha(
                icone = IconesAgro.Calendario,
                legenda = "Data",
                valor = rotulo,
                aoClicar = { aberto = true },
                erro = erro != null,
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
                DropdownMenuItem(text = { Text("Hoje") }, onClick = { aberto = false; aoUsarHoje() })
                DropdownMenuItem(text = { Text("Ontem") }, onClick = { aberto = false; aoUsarOntem() })
                DropdownMenuItem(
                    text = { Text("Escolher no calendário") },
                    leadingIcon = { Icon(IconesAgro.Calendario, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { aberto = false; aoAbrirCalendario() }
                )
            }
        }
        if (erro != null) {
            Text(
                text = erro,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
private fun CampoSitio(
    propriedades: List<Propriedade>,
    escolhidaId: String,
    aoEscolher: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var aberto by remember { mutableStateOf(false) }
    val nome = propriedades.firstOrNull { it.id == escolhidaId }?.nome ?: "Escolha"
    Box(modifier = modifier) {
        CampoDeEscolha(
            icone = IconesAgro.Sitio,
            legenda = "Sítio",
            valor = nome,
            aoClicar = { aberto = true },
            habilitado = propriedades.size > 1,
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            propriedades.forEach { propriedade ->
                DropdownMenuItem(
                    text = { Text(propriedade.nome) },
                    trailingIcon = if (propriedade.id == escolhidaId) {
                        { Icon(IconesAgro.Confirmado, contentDescription = "Escolhido", modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        aoEscolher(propriedade.id)
                        aberto = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CampoDescricao(texto: String, aoMudar: (String) -> Unit) {
    val foco = LocalFocusManager.current
    TextField(
        value = texto,
        onValueChange = aoMudar,
        singleLine = true,
        label = { Text("Descrição (opcional)") },
        leadingIcon = { Icon(IconesAgro.Lapis, contentDescription = null, modifier = Modifier.size(20.dp)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { foco.clearFocus() }),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Teclado 1–9, 00, 0 e ⌫ (toque apaga um dígito; long press zera o valor). */
@Composable
private fun TecladoNumerico(aoTeclar: (String) -> Unit, aoApagar: () -> Unit, aoLimpar: () -> Unit) {
    val linhas = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
private fun RowScope.TeclaDigito(tecla: String, aoTeclar: (String) -> Unit) {
    Surface(
        onClick = { aoTeclar(tecla) },
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .semantics { contentDescription = if (tecla == "00") "Zero zero" else tecla }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(tecla, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.TeclaApagar(aoApagar: () -> Unit, aoLimpar: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
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

/** DatePicker com datas após [ultimaPermitida] desabilitadas (RN07). Datas em millis UTC. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarioDialogo(
    dataAtual: LocalDate?,
    ultimaPermitida: LocalDate,
    aoEscolher: (LocalDate) -> Unit,
    aoFechar: () -> Unit
) {
    val limite = ultimaPermitida.emMillisUtc()
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = (dataAtual ?: LocalDate.now()).emMillisUtc(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= limite
            override fun isSelectableYear(year: Int): Boolean = year <= ultimaPermitida.year
        }
    )

    DatePickerDialog(
        onDismissRequest = aoFechar,
        confirmButton = {
            TextButton(
                onClick = {
                    estado.selectedDateMillis?.let { aoEscolher(it.paraLocalDateUtc()) }
                    aoFechar()
                },
                enabled = estado.selectedDateMillis != null
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = aoFechar) { Text("Cancelar") }
        }
    ) {
        DatePicker(state = estado)
    }
}

/**
 * Altura mínima (multiplicada pela escala de fonte) para o layout com teclado fixo;
 * abaixo disso usa o layout compacto com rolagem total.
 */
private val ALTURA_PARA_TECLADO_FIXO = 760.dp

// ---------- prévias ----------

private val SITIOS_DE_EXEMPLO = listOf(
    Propriedade(id = "p1", nome = "Fazenda Santa Clara", cultura = "Milho", areaHectares = 50.0, latitude = -21.7, longitude = -46.9),
    Propriedade(id = "p2", nome = "Sítio Boa Vista", cultura = "Café", areaHectares = 12.0, latitude = -21.7, longitude = -46.9)
)

@Composable
private fun FormDeExemplo(uiState: LancamentoFormUiState) {
    LancamentoFormScreen(
        uiState = uiState,
        titulo = "Novo lançamento",
        aoFechar = {}, aoMudarTipo = {}, aoMudarCategoria = {}, aoTeclar = {}, aoApagarDigito = {},
        aoLimparValor = {}, aoEscolherData = {}, aoUsarHoje = {}, aoUsarOntem = {},
        aoMudarPropriedade = {}, aoMudarDescricao = {}, aoSalvar = {}, aoExcluir = {}, aoLancarDeNovo = {},
        aoCadastrarPropriedade = {}
    )
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LancamentoFormPreview() {
    AgroSaldoTheme {
        FormDeExemplo(
            LancamentoFormUiState(
                tipo = TipoLancamento.DESPESA,
                categoria = CategoriaLancamento.COMBUSTIVEL,
                valorDigitos = "32000",
                propriedadeId = "p1",
                propriedades = SITIOS_DE_EXEMPLO,
                descricao = "Abastecimento do trator"
            )
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LancamentoFormReceitaEscuroPreview() {
    AgroSaldoTheme(escuro = true) {
        FormDeExemplo(
            LancamentoFormUiState(
                tipo = TipoLancamento.RECEITA,
                categoria = CategoriaLancamento.VENDA_SAFRA,
                valorDigitos = "1800000",
                propriedadeId = "p1",
                propriedades = SITIOS_DE_EXEMPLO,
                ehEdicao = true
            )
        )
    }
}
