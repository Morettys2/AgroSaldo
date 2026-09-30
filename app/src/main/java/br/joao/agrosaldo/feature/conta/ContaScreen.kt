package br.joao.agrosaldo.feature.conta

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.CampoSenha
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.agro

/**
 * Tela 19 — Conta.
 *
 * Sair e excluir não navegam: eles encerram a sessão, e a AppNavegacao leva
 * para o login ao perceber. Por isso a Route só recebe os destinos que
 * continuam logado (privacidade e propriedades).
 */
@Composable
fun ContaRoute(
    aoAbrirPrivacidade: () -> Unit,
    aoAlterarSenha: () -> Unit,
    aoAbrirPropriedades: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContaViewModel = viewModel(factory = ContaViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ContaScreen(
        uiState = uiState,
        aoAbrirPrivacidade = aoAbrirPrivacidade,
        aoAlterarSenha = aoAlterarSenha,
        aoAbrirPropriedades = aoAbrirPropriedades,
        aoSair = viewModel::aoSair,
        aoExcluirConta = viewModel::aoExcluirConta,
        aoCancelarExclusao = viewModel::aoCancelarExclusao,
        modifier = modifier
    )
}

@Composable
fun ContaScreen(
    uiState: ContaUiState,
    aoAbrirPrivacidade: () -> Unit,
    aoAlterarSenha: () -> Unit,
    aoAbrirPropriedades: () -> Unit,
    aoSair: () -> Unit,
    aoExcluirConta: (senha: String) -> Unit,
    aoCancelarExclusao: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmandoExclusao by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 32.dp)
    ) {
        Cabecalho(
            iniciais = uiState.iniciais,
            nome = uiState.nome,
            email = uiState.email
        )

        ItemConta(
            icone = Icons.Outlined.Shield,
            titulo = "Privacidade e dados",
            apoio = if (uiState.consentimentoEm.isBlank()) "O que o app guarda"
            else "Termo aceito em ${uiState.consentimentoEm}",
            aoClicar = aoAbrirPrivacidade
        )
        ItemConta(
            icone = Icons.Outlined.Lock,
            titulo = "Alterar senha",
            apoio = "Pede a senha atual",
            aoClicar = aoAlterarSenha
        )
        ItemConta(
            icone = Icons.Outlined.Grass,
            titulo = "Propriedades",
            apoio = uiState.resumoPropriedades,
            aoClicar = aoAbrirPropriedades
        )

        Spacer(Modifier.height(32.dp))

        OutlinedButton(
            onClick = aoSair,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
            Text(
                text = "Sair da conta",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Cor de despesa, não de erro: excluir é uma escolha legítima da
        // pessoa, não uma falha do sistema.
        OutlinedButton(
            onClick = { confirmandoExclusao = true },
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.agro.despesa),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.agro.despesa),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Outlined.DeleteForever, contentDescription = null)
            Text(
                text = "Excluir conta e todos os dados",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "A exclusão é permanente e apaga todas as propriedades e lançamentos desta conta.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (confirmandoExclusao) {
        DialogoExclusao(
            excluindo = uiState.excluindo,
            erro = uiState.erroExclusao,
            aoConfirmar = aoExcluirConta,
            aoCancelar = {
                confirmandoExclusao = false
                aoCancelarExclusao()
            }
        )
    }
}

@Composable
private fun Cabecalho(iniciais: String, nome: String, email: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 24.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = iniciais,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Text(text = nome, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ItemConta(
    icone: ImageVector,
    titulo: String,
    apoio: String,
    aoClicar: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = aoClicar)
                .padding(vertical = 16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text = titulo, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = apoio,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.agro.tintaApagada
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.agro.tintaApagada
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Pede a senha antes de apagar. Um celular esquecido na mesa da cozinha não
 * pode perder o histórico inteiro com dois toques de outra pessoa.
 */
@Composable
private fun DialogoExclusao(
    excluindo: Boolean,
    erro: String?,
    aoConfirmar: (String) -> Unit,
    aoCancelar: () -> Unit
) {
    var senha by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!excluindo) aoCancelar() },
        icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.agro.despesa) },
        title = { Text("Excluir a conta?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "A conta, as propriedades e todos os lançamentos serão apagados deste " +
                        "celular. Não dá para desfazer."
                )
                CampoSenha(
                    valor = senha,
                    aoMudar = { senha = it },
                    rotulo = "Sua senha",
                    erro = erro != null,
                    apoio = erro,
                    ultimoCampo = true,
                    aoConfirmar = { aoConfirmar(senha) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { aoConfirmar(senha) }, enabled = !excluindo) {
                if (excluindo) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("Excluir tudo", color = MaterialTheme.agro.despesa)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = aoCancelar, enabled = !excluindo) { Text("Cancelar") }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ContaPreview() {
    AgroSaldoTheme {
        ContaScreen(
            uiState = ContaUiState(
                carregando = false,
                nome = "Fábio Moretti",
                email = "fabio@exemplo.com",
                iniciais = "FM",
                consentimentoEm = "30/09/2026 às 19:40",
                versaoTermo = 1,
                quantidadePropriedades = 2
            ),
            aoAbrirPrivacidade = {}, aoAlterarSenha = {}, aoAbrirPropriedades = {}, aoSair = {},
            aoExcluirConta = {}, aoCancelarExclusao = {}
        )
    }
}
