package br.joao.agrosaldo.ui.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.agro

/**
 * Tela de conferência do sistema de design.
 *
 * Não faz parte do app. Serve pra você abrir lado a lado com o Figma e
 * confirmar que cor, tipografia e forma bateram. Apague quando a fase 3
 * começar de verdade.
 */
@Composable
fun TelaTokens(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        Titulo("Cores")

        Amostra("primary", MaterialTheme.colorScheme.primary)
        Amostra("primaryContainer", MaterialTheme.colorScheme.primaryContainer)
        Amostra("tertiary (clima)", MaterialTheme.colorScheme.tertiary)
        Amostra("surface", MaterialTheme.colorScheme.surface)
        Amostra("surfaceContainer", MaterialTheme.colorScheme.surfaceContainer)
        Amostra("outline", MaterialTheme.colorScheme.outline)

        Spacer(Modifier.height(8.dp))
        Text(
            "Cores de domínio, fora do Material 3:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Amostra("agro.receita", MaterialTheme.agro.receita)
        Amostra("agro.despesa", MaterialTheme.agro.despesa)
        Amostra("agro.clima", MaterialTheme.agro.clima)

        Divisor()
        Titulo("Tipografia")

        Text("R$ 12.480,00", style = MaterialTheme.typography.displayLarge)
        Text("R$ 14.670,00", style = MaterialTheme.typography.displayMedium)
        Text("Bem-vindo de volta", style = MaterialTheme.typography.headlineMedium)
        Text("Propriedades", style = MaterialTheme.typography.titleLarge)
        Text("Fazenda São João da Serra", style = MaterialTheme.typography.titleMedium)
        Text("Energia elétrica", style = MaterialTheme.typography.bodyLarge)
        Text("Conta do pivô", style = MaterialTheme.typography.bodyMedium)
        Text("Pra onde foi o dinheiro", style = MaterialTheme.typography.labelLarge)
        Text("28/08 · Abastecimento do trator", style = MaterialTheme.typography.labelSmall)

        Divisor()
        Titulo("Formas")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Forma("8", RoundedCornerShape(8.dp))
            Forma("12", RoundedCornerShape(12.dp))
            Forma("16", RoundedCornerShape(16.dp))
            Forma("20", RoundedCornerShape(20.dp))
            Forma("28", RoundedCornerShape(28.dp))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun Divisor() {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Amostra(nome: String, cor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(cor)
        )
        Spacer(Modifier.size(12.dp))
        Text(nome, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Forma(rotulo: String, forma: androidx.compose.ui.graphics.Shape) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .clip(forma)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        Spacer(Modifier.height(4.dp))
        Text(rotulo, style = MaterialTheme.typography.labelSmall)
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun TelaTokensPreview() {
    AgroSaldoTheme { TelaTokens() }
}
