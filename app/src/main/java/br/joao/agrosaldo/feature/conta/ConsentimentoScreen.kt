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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.agro

/**
 * Tela 04 — Consentimento LGPD (RNF10).
 *
 * É aqui, e não no formulário, que a conta é criada. Os três botões são as
 * três escolhas que a LGPD garante: aceitar, ler antes de decidir, e recusar
 * sem prejuízo — recusando, nada foi gravado.
 */
@Composable
fun ConsentimentoRoute(
    viewModel: CadastroViewModel,
    aoLerPolitica: () -> Unit,
    aoRecusar: () -> Unit,
    modifier: Modifier = Modifier
) {
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // O scroll vem antes do padding: a barra de rolagem encosta na
            // borda da tela e só o conteúdo fica afastado.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

        Spacer(Modifier.height(8.dp))

        Text(text = "Seus dados são seus", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Antes de começar, precisamos do seu aceite — exigência da LGPD (Lei 13.709/2018).",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        CartaoPonto(
            titulo = "Fica tudo no seu celular",
            texto = "Conta, propriedades e lançamentos são gravados no aparelho e funcionam sem internet."
        )
        CartaoPonto(
            titulo = "A previsão do tempo usa só as coordenadas",
            texto = "Enviamos latitude e longitude para a Open-Meteo. Nada além disso."
        )
        CartaoPonto(
            titulo = "Você apaga quando quiser",
            texto = "Em Conta, o botão de excluir remove tudo de forma permanente."
        )

        erro?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = aoAceitar,
            enabled = !criando,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (criando) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(text = "Aceito e quero continuar", style = MaterialTheme.typography.titleMedium)
            }
        }

        TextButton(onClick = aoLerPolitica, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Ler a política completa",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = aoRecusar, enabled = !criando, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Não aceito",
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun ConsentimentoPreview() {
    AgroSaldoTheme {
        ConsentimentoScreen(criando = false, erro = null, aoAceitar = {}, aoLerPolitica = {}, aoRecusar = {})
    }
}
