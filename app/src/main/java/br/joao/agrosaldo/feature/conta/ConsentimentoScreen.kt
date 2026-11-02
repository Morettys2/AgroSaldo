package br.joao.agrosaldo.feature.conta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.joao.agrosaldo.core.ui.BotaoPrincipal
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.PreviasDaTela
import br.joao.agrosaldo.ui.theme.agro

/**
 * Consentimento LGPD do cadastro. A conta só é criada ao aceitar o termo;
 * recusar não grava nada. Compartilha o [CadastroViewModel] com o formulário.
 */
@Composable
fun ConsentimentoRoute(viewModel: CadastroViewModel, aoLerPolitica: () -> Unit, aoRecusar: () -> Unit, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ConsentimentoScreen(
        criando = uiState.criando,
        erro = uiState.erroGeral,
        aoAceitar = viewModel::aoAceitarTermo,
        aoLerPolitica = aoLerPolitica,
        aoRecusar = aoRecusar,
        modifier = modifier
    )
}

@Composable
fun ConsentimentoScreen(
    criando: Boolean,
    erro: String?,
    aoAceitar: () -> Unit,
    aoLerPolitica: () -> Unit,
    aoRecusar: () -> Unit,
    modifier: Modifier = Modifier,
    titulo: String = "Seus dados são seus",
    introducao: String = "Antes de começar, precisamos do seu aceite — exigência da LGPD (Lei 13.709/2018).",
    textoRecusar: String = "Não aceito"
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Scroll antes do padding: a área rolável ocupa a largura toda.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Espacos.margemDaTela)
            .padding(top = Espacos.amplo, bottom = Espacos.enorme),
        verticalArrangement = Arrangement.spacedBy(Espacos.padrao)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.agro.climaContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.agro.sobreClimaContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.height(Espacos.pequeno))

        Text(text = titulo, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = introducao,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Espacos.pequeno))

        CartaoPonto(
            titulo = "Fica tudo no seu celular",
            texto = "Conta, propriedades e lançamentos são gravados no aparelho e funcionam sem internet. " +
                "Só o backup do Android, se estiver ligado, guarda uma cópia criptografada na sua conta."
        )
        CartaoPonto(
            titulo = "A previsão do tempo usa só as coordenadas",
            texto = "Enviamos latitude e longitude para a Open-Meteo. Nada além disso."
        )
        CartaoPonto(
            titulo = "Localização só quando você pede",
            texto = "O GPS é usado apenas para preencher as coordenadas do sítio, quando você toca no botão. " +
                "Nada é rastreado nem guardado além das coordenadas que você salvar."
        )
        CartaoPonto(
            titulo = "Você apaga quando quiser",
            texto = "Em Conta, o botão de excluir remove tudo de forma permanente."
        )

        erro?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(Espacos.pequeno))

        BotaoPrincipal(
            texto = "Aceito e quero continuar",
            aoClicar = aoAceitar,
            carregando = criando,
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(onClick = aoLerPolitica, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Ler a política completa",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = aoRecusar, enabled = !criando, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = textoRecusar,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CartaoPonto(titulo: String, texto: String, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Espacos.padrao),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = titulo, style = MaterialTheme.typography.labelLarge)
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@PreviasDaTela
@Composable
private fun ConsentimentoPreview() {
    AgroSaldoTheme {
        ConsentimentoScreen(criando = false, erro = null, aoAceitar = {}, aoLerPolitica = {}, aoRecusar = {})
    }
}
