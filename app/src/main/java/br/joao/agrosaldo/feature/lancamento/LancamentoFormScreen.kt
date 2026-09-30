package br.joao.agrosaldo.feature.lancamento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.CampoTexto
import br.joao.agrosaldo.core.util.emReal
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.agro

@Composable
fun LancamentoFormRoute(
    lancamentoId: String?,
    aoConcluir: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LancamentoFormViewModel = viewModel(
        key = "lancamento-form-${lancamentoId.orEmpty()}",
        factory = LancamentoFormViewModel.Factory(lancamentoId)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.salvo, uiState.excluido) {
        if (uiState.salvo || uiState.excluido) aoConcluir()
    }

    LancamentoFormScreen(
        uiState = uiState,
        aoMudarTipo = viewModel::aoMudarTipo,
        aoMudarCategoria = viewModel::aoMudarCategoria,
        aoMudarValor = viewModel::aoMudarValor,
        aoMudarData = viewModel::aoMudarData,
        aoMudarPropriedade = viewModel::aoMudarPropriedade,
        aoMudarDescricao = viewModel::aoMudarDescricao,
        aoSalvar = viewModel::aoSalvar,
        aoExcluir = viewModel::aoExcluir,
        modifier = modifier
    )
}

@Composable
fun LancamentoFormScreen(
    uiState: LancamentoFormUiState,
    aoMudarTipo: (TipoLancamento) -> Unit,
    aoMudarCategoria: (CategoriaLancamento) -> Unit,
    aoMudarValor: (String) -> Unit,
    aoMudarData: (String) -> Unit,
    aoMudarPropriedade: (String) -> Unit,
    aoMudarDescricao: (String) -> Unit,
    aoSalvar: () -> Unit,
    aoExcluir: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmandoExclusao by remember { mutableStateOf(false) }

    val ehDespesa = uiState.tipo == TipoLancamento.DESPESA
    val corValor = if (ehDespesa) MaterialTheme.agro.despesa else MaterialTheme.agro.receita

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = ehDespesa,
                onClick = { aoMudarTipo(TipoLancamento.DESPESA) },
                label = { Text("Despesa") },
                shape = CircleShape,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = !ehDespesa,
                onClick = { aoMudarTipo(TipoLancamento.RECEITA) },
                label = { Text("Receita") },
                shape = CircleShape,
                modifier = Modifier.weight(1f)
            )
        }

        // O valor é o dado mais importante da tela, então é o maior elemento
        // dela. Digitar 125050 mostra R$ 1.250,50 embaixo, em tempo real.
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Valor",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = uiState.valorCentavos.emReal(),
                style = MaterialTheme.typography.displayMedium,
                color = corValor,
                textAlign = TextAlign.Center
            )
        }

        CampoTexto(
            valor = uiState.valorDigitos,
            aoMudar = aoMudarValor,
            rotulo = "Digite o valor (só números)",
            erro = uiState.erroValor != null,
            apoio = uiState.erroValor ?: "125050 vira R$ 1.250,50",
            teclado = KeyboardType.Number
        )

        SeletorSuspenso(
            rotulo = "Categoria",
            valorAtual = uiState.categoria.rotulo,
            opcoes = uiState.categoriasDisponiveis.map { it.name to it.rotulo },
            aoSelecionar = { nome ->
                aoMudarCategoria(CategoriaLancamento.valueOf(nome))
            }
        )

        SeletorSuspenso(
            rotulo = "Propriedade",
            valorAtual = uiState.nomePropriedadeSelecionada,
            opcoes = uiState.propriedades.map { it.id to it.nome },
            aoSelecionar = aoMudarPropriedade
        )

        CampoTexto(
            valor = uiState.data,
            aoMudar = aoMudarData,
            rotulo = "Data (dd/MM/aaaa)",
            erro = uiState.erroData != null,
            apoio = uiState.erroData
        )

        CampoTexto(
            valor = uiState.descricao,
            aoMudar = aoMudarDescricao,
            rotulo = "Descrição (opcional)",
            umaLinha = false
        )

        uiState.erroGeral?.let { mensagem ->
            Text(
                text = mensagem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = aoSalvar,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                text = if (uiState.ehEdicao) "Salvar alterações" else "Salvar lançamento",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (uiState.ehEdicao) {
            OutlinedButton(
                onClick = { confirmandoExclusao = true },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.agro.despesa
                )
                Text(
                    text = "Excluir lançamento",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.agro.despesa,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    if (confirmandoExclusao) {
        AlertDialog(
            onDismissRequest = { confirmandoExclusao = false },
            title = { Text("Excluir este lançamento?") },
            text = { Text("Ele sai do saldo do mês. Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoExclusao = false
                    aoExcluir()
                }) {
                    Text("Excluir", color = MaterialTheme.agro.despesa)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmandoExclusao = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

/**
 * Seletor genérico: recebe pares de (valor, rótulo) e devolve o valor escolhido.
 *
 * Serve tanto pra categoria quanto pra propriedade — por isso trabalha com
 * String em vez de conhecer os tipos do domínio.
 */
@Composable
private fun SeletorSuspenso(
    rotulo: String,
    valorAtual: String,
    opcoes: List<Pair<String, String>>,
    aoSelecionar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var aberto by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { aberto = true },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                text = rotulo,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valorAtual,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            opcoes.forEach { (valor, texto) ->
                DropdownMenuItem(
                    text = { Text(texto) },
                    onClick = {
                        aoSelecionar(valor)
                        aberto = false
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun LancamentoFormPreview() {
    AgroSaldoTheme {
        LancamentoFormScreen(
            uiState = LancamentoFormUiState(
                tipo = TipoLancamento.DESPESA,
                categoria = CategoriaLancamento.COMBUSTIVEL,
                valorDigitos = "32000",
                descricao = "Abastecimento do trator"
            ),
            aoMudarTipo = {}, aoMudarCategoria = {}, aoMudarValor = {},
            aoMudarData = {}, aoMudarPropriedade = {}, aoMudarDescricao = {},
            aoSalvar = {}, aoExcluir = {}
        )
    }
}
