package br.joao.agrosaldo.feature.lancamento

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.BotaoDestaque
import br.joao.agrosaldo.core.ui.BotaoTonal
import br.joao.agrosaldo.core.util.paraLocalDateOuNulo
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.agro

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

    // Sempre a versão mais nova do callback, sem reiniciar o efeito quando ela muda.
    val aoConcluirAtual by rememberUpdatedState(aoConcluir)
    LaunchedEffect(uiState.salvo, uiState.excluido) {
        if (uiState.salvo || uiState.excluido) aoConcluirAtual()
    }

    LaunchedEffect(uiState.naoEncontrado) {
        if (uiState.naoEncontrado) {
            Toast.makeText(contexto, LancamentoFormViewModel.AVISO_NAO_ENCONTRADO, Toast.LENGTH_SHORT).show()
            aoConcluirAtual()
        }
    }

    // Um objeto só com as ações, criado uma vez: a tela recebe 1 parâmetro em vez de 15 lambdas.
    val acoes = remember(viewModel, lancamentoId) {
        LancamentoFormAcoes(
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
            aoCadastrarPropriedade = aoCadastrarPropriedade
        )
    }

    LancamentoFormScreen(
        uiState = uiState,
        titulo = when {
            lancamentoId != null -> "Editar lançamento"
            copiaDe != null -> "Lançar de novo"
            else -> "Novo lançamento"
        },
        acoes = acoes,
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
fun LancamentoFormScreen(uiState: LancamentoFormUiState, titulo: String, acoes: LancamentoFormAcoes, modifier: Modifier = Modifier) {
    var escolhendoData by rememberSaveable { mutableStateOf(false) }
    // Com o IME aberto (campo de descrição), o teclado numérico é ocultado.
    val tecladoDoSistemaAberto = WindowInsets.isImeVisible
    val escalaDaFonte = LocalDensity.current.fontScale.coerceAtLeast(1f)

    val campos: @Composable () -> Unit = {
        CamposDoLancamento(
            uiState = uiState,
            aoMudarCategoria = acoes.aoMudarCategoria,
            aoUsarHoje = acoes.aoUsarHoje,
            aoUsarOntem = acoes.aoUsarOntem,
            aoAbrirCalendario = { escolhendoData = true },
            aoMudarPropriedade = acoes.aoMudarPropriedade,
            aoMudarDescricao = acoes.aoMudarDescricao,
            aoCadastrarPropriedade = acoes.aoCadastrarPropriedade
        )
    }
    val teclado: @Composable () -> Unit = {
        if (!tecladoDoSistemaAberto) {
            TecladoNumerico(aoTeclar = acoes.aoTeclar, aoApagar = acoes.aoApagarDigito, aoLimpar = acoes.aoLimparValor)
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
                        aoFechar = acoes.aoFechar,
                        aoMudarTipo = acoes.aoMudarTipo,
                        aoLancarDeNovo = acoes.aoLancarDeNovo,
                        aoExcluir = acoes.aoExcluir
                    )
                    Folha {
                        Column(
                            modifier = Modifier.padding(
                                start = Espacos.margemDaTela,
                                end = Espacos.margemDaTela,
                                top = Espacos.largo,
                                bottom = Espacos.pequeno
                            ),
                            verticalArrangement = Arrangement.spacedBy(Espacos.padrao)
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
                    aoFechar = acoes.aoFechar,
                    aoMudarTipo = acoes.aoMudarTipo,
                    aoLancarDeNovo = acoes.aoLancarDeNovo,
                    aoExcluir = acoes.aoExcluir
                )
                Folha(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = Espacos.margemDaTela,
                                end = Espacos.margemDaTela,
                                top = Espacos.largo,
                                bottom = Espacos.pequeno
                            ),
                        verticalArrangement = Arrangement.spacedBy(Espacos.padrao)
                    ) {
                        campos()
                    }
                    Box(Modifier.padding(horizontal = Espacos.margemDaTela)) { teclado() }
                }
            }

            BarraDeSalvar(
                uiState = uiState,
                aoSalvar = acoes.aoSalvar
            )
        }
    }

    if (escolhendoData) {
        CalendarioDialogo(
            dataAtual = uiState.data.paraLocalDateOuNulo(),
            ultimaPermitida = uiState.hoje.plusMonths(LancamentoFormViewModel.MESES_NO_FUTURO),
            aoEscolher = acoes.aoEscolherData,
            aoFechar = { escolhendoData = false }
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(Espacos.padrao)) {
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
            BotaoTonal(texto = "Cadastrar propriedade", aoClicar = aoCadastrar)
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
            .padding(start = Espacos.margemDaTela, end = Espacos.margemDaTela, top = Espacos.pequeno, bottom = Espacos.padrao),
        verticalArrangement = Arrangement.spacedBy(Espacos.pequeno)
    ) {
        uiState.erroGeral?.let { mensagem ->
            Text(
                text = mensagem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        BotaoDestaque(
            texto = when {
                uiState.ehEdicao -> "Salvar alterações"
                uiState.tipo == TipoLancamento.DESPESA -> "Salvar despesa"
                else -> "Salvar receita"
            },
            aoClicar = aoSalvar,
            habilitado = !uiState.salvando,
            // Um pouco mais alto que o padrão: é o botão do polegar, fixo no rodapé.
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
        )
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
    LancamentoFormScreen(uiState = uiState, titulo = "Novo lançamento", acoes = LancamentoFormAcoes())
}

@Preview(name = "Fonte 2x", showBackground = true, heightDp = 1800, fontScale = 2f)
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
