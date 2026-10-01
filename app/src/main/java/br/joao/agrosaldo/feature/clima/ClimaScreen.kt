package br.joao.agrosaldo.feature.clima

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.PermissaoNotificacao
import br.joao.agrosaldo.core.ui.SeletorPropriedade
import br.joao.agrosaldo.core.ui.rememberPermissaoNotificacao
import br.joao.agrosaldo.domain.clima.CondicaoTempo
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.agro

/**
 * Tela 16 — Clima (RF12, RF13).
 */
@Composable
fun ClimaRoute(
    aoAbrirAjustes: () -> Unit,
    aoCadastrarPropriedade: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClimaViewModel = viewModel(factory = ClimaViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissao = rememberPermissaoNotificacao()

    ClimaScreen(
        uiState = uiState,
        permissao = permissao,
        aoSelecionarPropriedade = viewModel::aoSelecionarPropriedade,
        aoAtualizar = viewModel::aoAtualizar,
        aoAbrirAjustes = aoAbrirAjustes,
        aoCadastrarPropriedade = aoCadastrarPropriedade,
        modifier = modifier
    )
}

@Composable
fun ClimaScreen(
    uiState: ClimaUiState,
    permissao: PermissaoNotificacao,
    aoSelecionarPropriedade: (String) -> Unit,
    aoAtualizar: () -> Unit,
    aoAbrirAjustes: () -> Unit,
    aoCadastrarPropriedade: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.carregando -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        !uiState.temPropriedade -> EstadoVazio(
            icone = Icons.Outlined.WaterDrop,
            titulo = "Previsão por propriedade",
            texto = "Cadastre um sítio com as coordenadas para ver a chuva prevista e receber o aviso.",
            botao = "Cadastrar propriedade",
            aoClicar = aoCadastrarPropriedade,
            modifier = modifier
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Cabecalho(
                    uiState = uiState,
                    aoSelecionarPropriedade = aoSelecionarPropriedade,
                    aoAtualizar = aoAtualizar,
                    aoAbrirAjustes = aoAbrirAjustes
                )
            }

            uiState.mensagem?.let { mensagem ->
                item { Faixa(texto = mensagem, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            if (uiState.avisoAtivo && !permissao.concedida && permissao.podePedir) {
                item {
                    CartaoPermissao(aoPermitir = permissao.pedir, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            if (!uiState.temPrevisao) {
                item {
                    if (uiState.atualizando) {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        EstadoVazio(
                            icone = Icons.Outlined.CloudOff,
                            titulo = "Ainda sem previsão",
                            texto = "Conecte o celular à internet uma vez para baixar a previsão. " +
                                "Depois ela fica salva e aparece mesmo sem sinal.",
                            botao = "Tentar de novo",
                            aoClicar = aoAtualizar
                        )
                    }
                }
            } else {
                item { CartaoAgora(uiState, Modifier.padding(horizontal = 16.dp)) }
                item { CartaoRecomendacao(uiState, Modifier.padding(horizontal = 16.dp)) }
                item {
                    Text(
                        text = "Próximos 7 dias",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    )
                }
                item { CartaoDias(uiState.dias, Modifier.padding(horizontal = 16.dp)) }
                item {
                    Text(
                        text = listOfNotNull(uiState.atualizadaEm, "Fonte: Open-Meteo").joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.agro.tintaApagada,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Cabecalho(
    uiState: ClimaUiState,
    aoSelecionarPropriedade: (String) -> Unit,
    aoAtualizar: () -> Unit,
    aoAbrirAjustes: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp)
    ) {
        SeletorPropriedade(
            nome = uiState.nomePropriedade,
            propriedades = uiState.propriedades,
            aoSelecionar = aoSelecionarPropriedade,
            modifier = Modifier.weight(1f)
        )
        if (uiState.atualizando) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        } else {
            IconButton(onClick = aoAtualizar) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar previsão")
            }
        }
        IconButton(onClick = aoAbrirAjustes) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = "Ajustes do aviso de chuva")
        }
    }
}

/** O cartão grande: agora, e a chance de chuva nas próximas horas. */
@Composable
private fun CartaoAgora(uiState: ClimaUiState, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.agro.climaContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Icon(
                imageVector = uiState.condicaoAtual.icone(),
                contentDescription = uiState.condicaoAtual.rotulo,
                tint = MaterialTheme.agro.clima,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = uiState.temperaturaAtual ?: "–",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.agro.sobreClimaContainer
            )
            Text(
                text = uiState.resumo,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.agro.sobreClimaContainer,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Próximas ${uiState.janelaHoras}h (${uiState.janelaAte})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.agro.sobreClimaContainer
                )
                Text(
                    text = "${uiState.probabilidadeJanela}% · ${uiState.chuvaJanela}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.agro.sobreClimaContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { uiState.probabilidadeJanela / 100f },
                color = MaterialTheme.agro.clima,
                trackColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }
    }
}

/** O que fazer com o pivô — a pergunta que o produtor tem quando abre esta tela. */
@Composable
private fun CartaoRecomendacao(uiState: ClimaUiState, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = if (uiState.vaiChover) Icons.Outlined.WaterDrop else Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = uiState.recomendacaoTitulo,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = uiState.recomendacaoTexto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun CartaoDias(dias: List<DiaUi>, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            dias.forEachIndexed { indice, dia ->
                if (indice > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinhaDia(dia)
            }
        }
    }
}

@Composable
private fun LinhaDia(dia: DiaUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Text(text = dia.rotulo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        // Chance e milímetros do dia, um embaixo do outro: "81%" sozinho não
        // diz se é garoa ou chuva de molhar a lavoura.
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
            modifier = Modifier.size(20.dp)
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
private fun Faixa(texto: String, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CartaoPermissao(aoPermitir: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.agro.climaContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Receba o aviso de chuva",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.agro.sobreClimaContainer
            )
            Text(
                text = "Para avisar quando a chuva chegar perto, o app precisa da sua permissão para mandar notificações.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.agro.sobreClimaContainer
            )
            TextButton(onClick = aoPermitir) { Text("Permitir notificações") }
        }
    }
}

@Composable
private fun EstadoVazio(
    icone: ImageVector,
    titulo: String,
    texto: String,
    botao: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.agro.clima, modifier = Modifier.size(56.dp))
        Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(onClick = aoClicar, shape = CircleShape) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(botao, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

// ---------- previews ----------

private val PERMISSAO_OK = PermissaoNotificacao(concedida = true, podePedir = true, pedir = {})

private val ESTADO_EXEMPLO = ClimaUiState(
    carregando = false,
    temPropriedade = true,
    nomePropriedade = "Fazenda São João da Serra",
    temPrevisao = true,
    temperaturaAtual = "26°",
    condicaoAtual = CondicaoTempo.NUBLADO,
    resumo = "Chuva prevista a partir das 18h",
    janelaAte = "até as 2h de amanhã",
    probabilidadeJanela = 80,
    chuvaJanela = "6 mm",
    vaiChover = true,
    recomendacaoTitulo = "Dá para adiar a irrigação do pivô",
    recomendacaoTexto = "Previsão de 80% de chance e 6 mm nas próximas 12 horas. " +
        "Passou dos limites que você configurou (60% de chance ou 2 mm).",
    dias = listOf(
        DiaUi("Hoje", CondicaoTempo.CHUVA, "31°", "22°", 80, "12 mm"),
        DiaUi("Amanhã", CondicaoTempo.PANCADAS, "29°", "21°", 65, "6 mm"),
        DiaUi("Sábado", CondicaoTempo.NUBLADO, "28°", "20°", 30, "0 mm"),
        DiaUi("Domingo", CondicaoTempo.CEU_LIMPO, "30°", "19°", 10, "0 mm")
    ),
    atualizadaEm = "Atualizada às 14:05"
)

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun ClimaPreview() {
    AgroSaldoTheme {
        ClimaScreen(ESTADO_EXEMPLO, PERMISSAO_OK, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ClimaSemInternetPreview() {
    AgroSaldoTheme {
        ClimaScreen(
            ClimaUiState(
                carregando = false,
                temPropriedade = true,
                nomePropriedade = "Sítio Boa Vista",
                mensagem = ClimaViewModel.MENSAGEM_SEM_INTERNET
            ),
            PermissaoNotificacao(concedida = false, podePedir = true, pedir = {}),
            {}, {}, {}, {}
        )
    }
}
