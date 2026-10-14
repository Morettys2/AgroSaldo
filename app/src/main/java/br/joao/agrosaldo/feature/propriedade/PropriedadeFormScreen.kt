package br.joao.agrosaldo.feature.propriedade

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.localizacao.rememberBuscaDeLocalizacao
import br.joao.agrosaldo.core.ui.BotaoPrincipal
import br.joao.agrosaldo.core.ui.BotaoSecundario
import br.joao.agrosaldo.core.ui.CampoTexto
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.agro

@Composable
fun PropriedadeFormRoute(
    propriedadeId: String?,
    aoConcluir: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PropriedadeFormViewModel = viewModel(
        // key por id: evita reaproveitar o ViewModel de outra propriedade (ou da criação).
        key = "propriedade-form-${propriedadeId.orEmpty()}",
        factory = PropriedadeFormViewModel.Factory(propriedadeId)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Localização fica na Route (depende da Activity para permissão); a Screen recebe só
    // estado, o que mantém o @Preview funcional.
    var mensagemLocalizacao by remember { mutableStateOf<String?>(null) }
    val busca = rememberBuscaDeLocalizacao(
        aoEncontrar = { latitude, longitude ->
            mensagemLocalizacao = "Coordenadas preenchidas com a sua localização atual."
            viewModel.aoUsarLocalizacao(latitude, longitude)
        },
        aoFalhar = { mensagem -> mensagemLocalizacao = mensagem }
    )

    LaunchedEffect(uiState.salvo, uiState.excluido) {
        if (uiState.salvo || uiState.excluido) aoConcluir()
    }

    val contexto = LocalContext.current
    LaunchedEffect(uiState.naoEncontrado) {
        if (uiState.naoEncontrado) {
            Toast.makeText(contexto, PropriedadeFormViewModel.AVISO_NAO_ENCONTRADO, Toast.LENGTH_SHORT).show()
            aoConcluir()
        }
    }

    PropriedadeFormScreen(
        uiState = uiState,
        aoMudarNome = viewModel::aoMudarNome,
        aoMudarCultura = viewModel::aoMudarCultura,
        aoMudarArea = viewModel::aoMudarArea,
        aoMudarLatitude = viewModel::aoMudarLatitude,
        aoMudarLongitude = viewModel::aoMudarLongitude,
        aoInverterSinalLatitude = viewModel::aoInverterSinalLatitude,
        aoInverterSinalLongitude = viewModel::aoInverterSinalLongitude,
        buscandoLocalizacao = busca.buscando,
        mensagemLocalizacao = mensagemLocalizacao,
        aoUsarMinhaLocalizacao = {
            mensagemLocalizacao = null
            busca.buscar()
        },
        aoSalvar = viewModel::aoSalvar,
        aoExcluir = viewModel::aoExcluir,
        modifier = modifier
    )
}

@Composable
fun PropriedadeFormScreen(
    uiState: PropriedadeFormUiState,
    aoMudarNome: (String) -> Unit,
    aoMudarCultura: (String) -> Unit,
    aoMudarArea: (String) -> Unit,
    aoMudarLatitude: (String) -> Unit,
    aoMudarLongitude: (String) -> Unit,
    aoInverterSinalLatitude: () -> Unit,
    aoInverterSinalLongitude: () -> Unit,
    buscandoLocalizacao: Boolean,
    mensagemLocalizacao: String?,
    aoUsarMinhaLocalizacao: () -> Unit,
    aoSalvar: () -> Unit,
    aoExcluir: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmandoExclusao by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Espacos.margemDaTela)
            .padding(top = Espacos.pequeno, bottom = Espacos.enorme),
        verticalArrangement = Arrangement.spacedBy(Espacos.padrao)
    ) {

        CampoTexto(
            valor = uiState.nome,
            aoMudar = aoMudarNome,
            rotulo = "Nome da propriedade",
            erro = uiState.erroNome != null,
            apoio = uiState.erroNome
        )

        CampoTexto(
            valor = uiState.cultura,
            aoMudar = aoMudarCultura,
            rotulo = "Cultura plantada"
        )

        CampoTexto(
            valor = uiState.area,
            aoMudar = aoMudarArea,
            rotulo = "Área em hectares",
            erro = uiState.erroArea != null,
            // Sem erro, mostra como o número foi entendido ("= 1.000 ha").
            apoio = uiState.erroArea ?: uiState.leituraDaArea,
            teclado = KeyboardType.Decimal
        )

        // Um campo por linha: o botão ± ocupa espaço e a coordenada do GPS tem 6 casas decimais.
        CampoTexto(
            valor = uiState.latitude,
            aoMudar = aoMudarLatitude,
            rotulo = "Latitude",
            erro = uiState.erroCoordenadas != null,
            teclado = KeyboardType.Decimal,
            acaoNoFim = { BotaoSinal(campo = "latitude", aoClicar = aoInverterSinalLatitude) }
        )
        CampoTexto(
            valor = uiState.longitude,
            aoMudar = aoMudarLongitude,
            rotulo = "Longitude",
            erro = uiState.erroCoordenadas != null,
            teclado = KeyboardType.Decimal,
            acaoNoFim = { BotaoSinal(campo = "longitude", aoClicar = aoInverterSinalLongitude) }
        )

        OutlinedButton(
            onClick = aoUsarMinhaLocalizacao,
            enabled = !buscandoLocalizacao,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = Espacos.alvoDeToque)
        ) {
            if (buscandoLocalizacao) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text("Procurando…", modifier = Modifier.padding(start = Espacos.pequeno))
            } else {
                Icon(Icons.Outlined.MyLocation, contentDescription = null)
                Text("Usar minha localização", modifier = Modifier.padding(start = Espacos.pequeno))
            }
        }

        mensagemLocalizacao?.let { mensagem ->
            Text(
                text = mensagem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        (uiState.erroCoordenadas ?: uiState.avisoCoordenadas)?.let { mensagem ->
            Text(
                text = mensagem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.agro.climaContainer)
        ) {
            Row(
                modifier = Modifier.padding(Espacos.padrao),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = MaterialTheme.agro.sobreClimaContainer
                )
                Text(
                    text = "As coordenadas buscam a previsão do tempo desta propriedade. " +
                        "Estando no sítio, toque em \"Usar minha localização\". Longe dele, dá pra " +
                        "copiar do Google Maps segurando o dedo sobre o local.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.agro.sobreClimaContainer
                )
            }
        }

        Spacer(Modifier.height(Espacos.pequeno))

        // Erro geral (não associado a campo) exibido logo acima do botão.
        uiState.erroGeral?.let { mensagem ->
            Text(
                text = mensagem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        BotaoPrincipal(
            texto = when {
                uiState.avisoCoordenadas != null -> "Salvar mesmo assim"
                uiState.ehEdicao -> "Salvar alterações"
                else -> "Salvar propriedade"
            },
            aoClicar = aoSalvar,
            habilitado = !uiState.salvando,
            modifier = Modifier.fillMaxWidth()
        )

        if (uiState.ehEdicao) {
            BotaoSecundario(
                texto = "Excluir propriedade",
                aoClicar = { confirmandoExclusao = true },
                icone = Icons.Outlined.Delete,
                perigo = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (confirmandoExclusao) {
        AlertDialog(
            onDismissRequest = { confirmandoExclusao = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.agro.despesa
                )
            },
            title = { Text("Excluir esta propriedade?") },
            text = {
                Text(
                    "Todos os lançamentos dela também serão apagados. " +
                        "Logo depois, dá para desfazer pela barra que aparece embaixo."
                )
            },
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
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Põe ou tira o sinal de menos: o teclado numérico de alguns aparelhos não tem "−". */
@Composable
private fun BotaoSinal(campo: String, aoClicar: () -> Unit) {
    IconButton(
        onClick = aoClicar,
        modifier = Modifier.semantics { contentDescription = "Trocar o sinal da $campo" }
    ) {
        Text("±", style = MaterialTheme.typography.titleLarge)
    }
}

@Preview(name = "Fonte 2x", showBackground = true, heightDp = 1700, fontScale = 2f)
@Preview(showBackground = true, heightDp = 850)
@Composable
private fun PropriedadeFormNovaPreview() {
    AgroSaldoTheme {
        PropriedadeFormScreen(
            uiState = PropriedadeFormUiState(),
            aoMudarNome = {}, aoMudarCultura = {}, aoMudarArea = {},
            aoMudarLatitude = {}, aoMudarLongitude = {},
            aoInverterSinalLatitude = {}, aoInverterSinalLongitude = {},
            buscandoLocalizacao = false, mensagemLocalizacao = null, aoUsarMinhaLocalizacao = {},
            aoSalvar = {}, aoExcluir = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 850)
@Composable
private fun PropriedadeFormComErroPreview() {
    AgroSaldoTheme {
        PropriedadeFormScreen(
            uiState = PropriedadeFormUiState(
                nome = "",
                cultura = "Milho",
                area = "abc",
                latitude = "-21,7333",
                longitude = "-46,9667",
                erroNome = "Informe o nome da propriedade.",
                erroArea = "A área deve ser um número maior que zero.",
                ehEdicao = true
            ),
            aoMudarNome = {}, aoMudarCultura = {}, aoMudarArea = {},
            aoMudarLatitude = {}, aoMudarLongitude = {},
            aoInverterSinalLatitude = {}, aoInverterSinalLongitude = {},
            buscandoLocalizacao = false, mensagemLocalizacao = null, aoUsarMinhaLocalizacao = {},
            aoSalvar = {}, aoExcluir = {}
        )
    }
}
