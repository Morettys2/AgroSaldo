package br.joao.agrosaldo.feature.clima

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.joao.agrosaldo.core.ui.BotaoCabecalho
import br.joao.agrosaldo.core.ui.CabecalhoSafra
import br.joao.agrosaldo.core.ui.Carregando
import br.joao.agrosaldo.core.ui.EstadoErro
import br.joao.agrosaldo.core.ui.EstadoVazio
import br.joao.agrosaldo.core.ui.FaixaDeAviso
import br.joao.agrosaldo.core.ui.PermissaoNotificacao
import br.joao.agrosaldo.core.ui.SeletorPropriedade
import br.joao.agrosaldo.core.ui.rememberPermissaoNotificacao
import br.joao.agrosaldo.domain.clima.CondicaoTempo
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro

/** Previsão do tempo e recomendação de irrigação da propriedade selecionada. */
@Composable
fun ClimaRoute(
    aoAbrirAjustes: () -> Unit,
    aoCadastrarPropriedade: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClimaViewModel = hiltViewModel()
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
        contentPadding = PaddingValues(bottom = Espacos.grande),
        verticalArrangement = Arrangement.spacedBy(Espacos.padrao)
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
            uiState.carregando -> item(key = "carregando") { Carregando() }

            !uiState.temPropriedade -> item(key = "sem_propriedade") {
                EstadoVazio(
                    icone = IconesAgro.Gota,
                    corDoIcone = MaterialTheme.agro.clima,
                    fundoDoIcone = MaterialTheme.agro.climaContainer,
                    titulo = "Previsão por propriedade",
                    texto = "Cadastre uma propriedade com as coordenadas para ver a chuva prevista e receber o aviso.",
                    acao = "Cadastrar propriedade",
                    destaque = true,
                    aoClicarAcao = aoCadastrarPropriedade,
                    modifier = Modifier.padding(horizontal = Espacos.margemDaTela)
                )
            }

            else -> {
                uiState.erro?.let { erro ->
                    item(key = "erro") { EstadoErro(erro, Modifier.padding(horizontal = Espacos.margemDaTela)) }
                }

                uiState.mensagem?.let { mensagem ->
                    item(key = "mensagem") {
                        FaixaDeAviso(texto = mensagem, modifier = Modifier.padding(horizontal = Espacos.margemDaTela))
                    }
                }

                if (uiState.avisoAtivo && !permissao.concedida) {
                    item(key = "permissao") {
                        CartaoPermissao(aoPermitir = permissao.pedir, modifier = Modifier.padding(horizontal = Espacos.margemDaTela))
                    }
                }

                if (!uiState.temPrevisao) {
                    item(key = "sem_previsao") {
                        if (uiState.atualizando) {
                            Carregando()
                        } else {
                            EstadoVazio(
                                icone = IconesAgro.Nuvem,
                                corDoIcone = MaterialTheme.agro.clima,
                                fundoDoIcone = MaterialTheme.agro.climaContainer,
                                titulo = "Ainda sem previsão",
                                texto = "Conecte o celular à internet uma vez para baixar a previsão. " +
                                    "Depois ela fica salva e aparece mesmo sem sinal.",
                                acao = "Tentar de novo",
                                destaque = true,
                                aoClicarAcao = aoAtualizar,
                                modifier = Modifier.padding(horizontal = Espacos.margemDaTela)
                            )
                        }
                    }
                } else {
                    item(key = "recomendacao") { CartaoRecomendacao(uiState, Modifier.padding(horizontal = Espacos.margemDaTela)) }
                    item(key = "dias") { CartaoDias(uiState.dias, Modifier.padding(horizontal = Espacos.margemDaTela)) }
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
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Espacos.margemDaTela)
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacos.pequeno)) {
            if (uiState.temPropriedade) {
                SeletorPropriedade(
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
                verticalArrangement = Arrangement.spacedBy(Espacos.pequeno),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.agro.cabecalhoElevado)
                    .padding(horizontal = Espacos.padrao, vertical = 14.dp)
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

private val PERMISSAO_OK = PermissaoNotificacao(concedida = true, pedir = {})

private val ESTADO_EXEMPLO = ClimaUiState(
    carregando = false,
    temPropriedade = true,
    nomePropriedade = "Fazenda Modelo",
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

@Preview(name = "Fonte 2x", showBackground = true, heightDp = 2200, fontScale = 2f)
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
                nomePropriedade = "Sítio Exemplo",
                mensagem = ClimaViewModel.MENSAGEM_SEM_INTERNET
            ),
            PermissaoNotificacao(concedida = false, pedir = {}),
            {},
            {},
            {},
            {}
        )
    }
}
