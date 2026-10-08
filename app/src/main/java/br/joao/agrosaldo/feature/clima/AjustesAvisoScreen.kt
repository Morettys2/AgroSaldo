package br.joao.agrosaldo.feature.clima

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.aviso.NotificadorDeChuva
import br.joao.agrosaldo.core.ui.PermissaoNotificacao
import br.joao.agrosaldo.core.ui.rememberPermissaoNotificacao
import br.joao.agrosaldo.core.util.emMilimetros
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.agro

/** Ajustes do aviso de chuva: ativação, limites (RN04) e propriedades avisadas. */
@Composable
fun AjustesAvisoRoute(
    aoConcluir: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AjustesAvisoViewModel = viewModel(factory = AjustesAvisoViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissao = rememberPermissaoNotificacao()
    val context = LocalContext.current
    // FLAG_DEBUGGABLE em runtime: ferramentas de teste só em builds debug.
    val ehDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    LaunchedEffect(uiState.salvo) {
        if (uiState.salvo) {
            Toast.makeText(context, "Ajustes salvos", Toast.LENGTH_SHORT).show()
            aoConcluir()
        }
    }

    AjustesAvisoScreen(
        uiState = uiState,
        permissao = permissao,
        aoMudarAtivo = viewModel::aoMudarAtivo,
        aoMudarProbabilidade = viewModel::aoMudarProbabilidade,
        aoMudarChuva = viewModel::aoMudarChuva,
        aoMudarSitio = viewModel::aoMudarSitio,
        aoSalvar = viewModel::aoSalvar,
        modifier = modifier,
        ferramentasDeTeste = if (ehDebug) {
            FerramentasDeTeste(
                aoMostrarExemplo = { NotificadorDeChuva.mostrarExemplo(context) },
                aoVerificarAgora = {
                    viewModel.aoVerificarAgora()
                    Toast.makeText(context, "Verificação iniciada", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            null
        }
    )
}

/** Ações de teste exibidas apenas em builds debug. */
class FerramentasDeTeste(
    val aoMostrarExemplo: () -> Unit,
    val aoVerificarAgora: () -> Unit
)

@Composable
fun AjustesAvisoScreen(
    uiState: AjustesAvisoUiState,
    permissao: PermissaoNotificacao,
    aoMudarAtivo: (Boolean) -> Unit,
    aoMudarProbabilidade: (Float) -> Unit,
    aoMudarChuva: (Float) -> Unit,
    aoMudarSitio: (id: String, avisa: Boolean) -> Unit,
    aoSalvar: () -> Unit,
    modifier: Modifier = Modifier,
    ferramentasDeTeste: FerramentasDeTeste? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 32.dp)
    ) {
        Text(
            text = "Você decide quando o app avisa. O aviso chega quando QUALQUER um dos dois limites é atingido.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Avisar sobre chuva", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Notificação no celular, verificada 2 vezes por dia",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.agro.tintaApagada
                )
            }
            Switch(checked = uiState.ativo, onCheckedChange = aoMudarAtivo)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (uiState.ativo && !permissao.concedida) {
            Spacer(Modifier.height(12.dp))
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.agro.climaContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (permissao.podePedir) {
                            "As notificações do app estão bloqueadas. Sem elas, o aviso só aparece dentro da tela de clima."
                        } else {
                            "As notificações do app estão desligadas nas configurações do celular."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.agro.sobreClimaContainer
                    )
                    if (permissao.podePedir) {
                        TextButton(onClick = permissao.pedir) { Text("Permitir notificações") }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        CartaoLimite(
            titulo = "Chance mínima de chuva",
            valor = "${uiState.probabilidadeMinima}%",
            explicacao = "Abaixo disso o app fica quieto.",
            posicao = uiState.probabilidadeMinima.toFloat(),
            faixa = AjustesAvisoViewModel.PROBABILIDADE_MIN.toFloat()..AjustesAvisoViewModel.PROBABILIDADE_MAX.toFloat(),
            // 30..90 de 5 em 5 = 13 posições; steps exclui as pontas.
            marcas = 11,
            habilitado = uiState.ativo,
            aoMudar = aoMudarProbabilidade
        )

        Spacer(Modifier.height(12.dp))

        CartaoLimite(
            titulo = "Chuva mínima somada",
            valor = uiState.chuvaMinimaMm.emMilimetros(),
            explicacao = "Garoa não adianta para molhar a lavoura.",
            posicao = uiState.chuvaMinimaMm.toFloat(),
            faixa = AjustesAvisoViewModel.CHUVA_MIN.toFloat()..AjustesAvisoViewModel.CHUVA_MAX.toFloat(),
            // 1..10 de 0,5 em 0,5 = 19 posições; steps exclui as pontas.
            marcas = 17,
            habilitado = uiState.ativo,
            aoMudar = aoMudarChuva
        )

        if (uiState.mostrarSitios) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Avisar sobre",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            uiState.sitios.forEach { sitio ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        // toggleable na linha inteira com Role.Switch para acessibilidade.
                        .toggleable(
                            value = sitio.avisa,
                            enabled = uiState.ativo,
                            role = Role.Switch,
                            onValueChange = { aoMudarSitio(sitio.id, it) }
                        )
                        .padding(vertical = 10.dp)
                ) {
                    Text(sitio.nome, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = sitio.avisa, onCheckedChange = null, enabled = uiState.ativo)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Janela de previsão: próximas ${uiState.janelaHoras} horas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = aoSalvar,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Salvar ajustes", style = MaterialTheme.typography.titleMedium)
        }

        ferramentasDeTeste?.let { ferramentas ->
            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Ferramentas de teste (só aparecem na versão de desenvolvimento)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.agro.tintaApagada
            )
            TextButton(onClick = ferramentas.aoMostrarExemplo) { Text("Mostrar notificação de exemplo") }
            TextButton(onClick = ferramentas.aoVerificarAgora) { Text("Verificar chuva agora") }
        }
    }
}

@Composable
private fun CartaoLimite(
    titulo: String,
    valor: String,
    explicacao: String,
    posicao: Float,
    faixa: ClosedFloatingPointRange<Float>,
    marcas: Int,
    habilitado: Boolean,
    aoMudar: (Float) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(titulo, style = MaterialTheme.typography.bodyLarge)
                Text(valor, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = posicao,
                onValueChange = aoMudar,
                valueRange = faixa,
                steps = marcas,
                enabled = habilitado
            )
            Text(explicacao, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.agro.tintaApagada)
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AjustesAvisoPreview() {
    AgroSaldoTheme {
        AjustesAvisoScreen(
            uiState = AjustesAvisoUiState(
                sitios = listOf(
                    SitioDoAviso("1", "Fazenda São João da Serra", avisa = true),
                    SitioDoAviso("2", "Sítio Boa Vista", avisa = false)
                )
            ),
            permissao = PermissaoNotificacao(concedida = false, podePedir = true, pedir = {}),
            aoMudarAtivo = {}, aoMudarProbabilidade = {}, aoMudarChuva = {}, aoMudarSitio = { _, _ -> }, aoSalvar = {}
        )
    }
}
