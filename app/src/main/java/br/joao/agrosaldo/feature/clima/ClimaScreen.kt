package br.joao.agrosaldo.feature.clima

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.BotaoCabecalho
import br.joao.agrosaldo.core.ui.CabecalhoSafra
import br.joao.agrosaldo.core.ui.CartaoSafra
import br.joao.agrosaldo.core.ui.PermissaoNotificacao
import br.joao.agrosaldo.core.ui.SeletorPropriedadeCabecalho
import br.joao.agrosaldo.core.ui.TituloDeCartao
import br.joao.agrosaldo.core.ui.rememberPermissaoNotificacao
import br.joao.agrosaldo.domain.clima.CondicaoTempo
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro

/** Previsão do tempo e recomendação de irrigação da propriedade selecionada (RF12, RF13). */
@Composable
fun ClimaRoute(
    aoAbrirAjustes: () -> Unit,
    aoCadastrarPropriedade: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClimaViewModel = viewModel(factory = ClimaViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissao = rememberPermissaoNotificacao()

    // Recalcula o que depende do relógio ("Hoje", hora da chuva) e confere o cache ao retomar.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.aoVoltarParaATela() }

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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "cabecalho") {
            Cabecalho(
                uiState = uiState,
                aoSelecionarPropriedade = aoSelecionarPropriedade,
                aoAtualizar = aoAtualizar,
                aoAbrirAjustes = aoAbrirAjustes
            )
        }

        when {
            uiState.carregando -> item(key = "carregando") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            !uiState.temPropriedade -> item(key = "sem_propriedade") {
                EstadoVazio(
                    icone = IconesAgro.Gota,
                    titulo = "Previsão por propriedade",
                    texto = "Cadastre um sítio com as coordenadas para ver a chuva prevista e receber o aviso.",
                    botao = "Cadastrar propriedade",
                    aoClicar = aoCadastrarPropriedade,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            else -> {
                uiState.mensagem?.let { mensagem ->
                    item(key = "mensagem") { Faixa(texto = mensagem, modifier = Modifier.padding(horizontal = 20.dp)) }
                }

                if (uiState.avisoAtivo && !permissao.concedida && permissao.podePedir) {
                    item(key = "permissao") {
                        CartaoPermissao(aoPermitir = permissao.pedir, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }

                if (!uiState.temPrevisao) {
                    item(key = "sem_previsao") {
                        if (uiState.atualizando) {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            EstadoVazio(
                                icone = IconesAgro.Nuvem,
                                titulo = "Ainda sem previsão",
                                texto = "Conecte o celular à internet uma vez para baixar a previsão. " +
                                    "Depois ela fica salva e aparece mesmo sem sinal.",
                                botao = "Tentar de novo",
                                aoClicar = aoAtualizar,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                } else {
                    item(key = "recomendacao") { CartaoRecomendacao(uiState, Modifier.padding(horizontal = 20.dp)) }
                    item(key = "dias") { CartaoDias(uiState.dias, Modifier.padding(horizontal = 20.dp)) }
                    item(key = "fonte") {
                        // Open-Meteo é CC BY 4.0: atribuição com link é obrigatória.
                        val fonte = buildAnnotatedString {
                            uiState.atualizadaEm?.let { append("$it · ") }
                            append("Fonte: ")
                            withLink(
                                LinkAnnotation.Url(
                                    url = "https://open-meteo.com/",
                                    styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
                                )
                            ) { append("Open-Meteo") }
                            append(" (CC BY 4.0)")
                        }
                        Text(
                            text = fonte,
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
}

/** Cabeçalho com seletor de propriedade, ações, condição atual e barra de chance de chuva da janela. */
@Composable
private fun Cabecalho(
    uiState: ClimaUiState,
    aoSelecionarPropriedade: (String) -> Unit,
    aoAtualizar: () -> Unit,
    aoAbrirAjustes: () -> Unit
) {
    CabecalhoSafra {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (uiState.temPropriedade) {
                SeletorPropriedadeCabecalho(
                    nome = uiState.nomePropriedade,
                    propriedades = uiState.propriedades,
                    aoSelecionar = aoSelecionarPropriedade,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = "Clima",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.agro.sobreCabecalho,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
            }
            if (uiState.temPropriedade) {
                if (uiState.atualizando) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.agro.sobreCabecalho,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    BotaoCabecalho(icone = IconesAgro.Atualizar, descricao = "Atualizar previsão", aoClicar = aoAtualizar)
                }
            }
            BotaoCabecalho(icone = IconesAgro.Sino, descricao = "Ajustes do aviso de chuva", aoClicar = aoAbrirAjustes)
        }

        if (uiState.temPrevisao) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(
                        text = uiState.temperaturaAtual ?: "–",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.agro.sobreCabecalho
                    )
                    Text(
                        text = uiState.condicaoAtual.rotulo,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.agro.sobreCabecalhoSecundario
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.agro.cabecalhoElevado)
                ) {
                    Icon(
                        imageVector = uiState.condicaoAtual.icone(),
                        contentDescription = null,
                        tint = MaterialTheme.agro.sobreCabecalho,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Text(
                text = uiState.resumo,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.agro.sobreCabecalho
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.agro.cabecalhoElevado)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Próximas ${uiState.janelaHoras} h (${uiState.janelaAte})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.agro.sobreCabecalhoSecundario,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = uiState.chanceEVolume,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.agro.destaque
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.agro.cabecalho)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((uiState.probabilidadeJanela / 100f).coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.agro.destaque)
                    )
                }
            }
        }
    }
}

/** Recomendação de irrigação calculada pelo [ClimaViewModel]. */
@Composable
private fun CartaoRecomendacao(uiState: ClimaUiState, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.agro.cartaoClima,
        contentColor = MaterialTheme.agro.sobreCartaoClima,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
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
private fun CartaoDias(dias: List<DiaUi>, modifier: Modifier = Modifier) {
    CartaoSafra(modifier = modifier, preenchimento = 0.dp) {
        TituloDeCartao(
            titulo = "Próximos 7 dias",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp)
        )
        Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
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
private fun Faixa(texto: String, modifier: Modifier = Modifier) {
    CartaoSafra(modifier = modifier, preenchimento = 16.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                IconesAgro.Nuvem,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CartaoPermissao(aoPermitir: () -> Unit, modifier: Modifier = Modifier) {
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
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = aoPermitir,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(IconesAgro.Sino, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Permitir notificações", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
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
    CartaoSafra(modifier = modifier, preenchimento = 28.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.agro.climaContainer)
            ) {
                Icon(icone, contentDescription = null, tint = MaterialTheme.agro.clima, modifier = Modifier.size(36.dp))
            }
            Text(titulo, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = aoClicar,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.agro.destaque,
                    contentColor = MaterialTheme.agro.sobreDestaque
                ),
                modifier = Modifier.heightIn(min = 52.dp)
            ) {
                Text(botao, style = MaterialTheme.typography.labelLarge)
            }
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
    chanceEVolume = "80% · 6 mm",
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
        ClimaScreen(ESTADO_EXEMPLO, PERMISSAO_OK, {}, {}, {}, {}, Modifier.background(MaterialTheme.colorScheme.background))
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun ClimaEscuroPreview() {
    AgroSaldoTheme(escuro = true) {
        ClimaScreen(ESTADO_EXEMPLO, PERMISSAO_OK, {}, {}, {}, {}, Modifier.background(MaterialTheme.colorScheme.background))
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
