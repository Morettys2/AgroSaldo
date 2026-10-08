package br.joao.agrosaldo.feature.conta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.CampoSenha
import br.joao.agrosaldo.core.ui.CampoTexto
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme

/**
 * Rota de login (RF02). Não há callback de sucesso: a AppNavegacao observa a
 * sessão e navega ao início quando ela é aberta.
 */
@Composable
fun LoginRoute(
    aoCriarConta: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LoginScreen(
        uiState = uiState,
        aoMudarEmail = viewModel::aoMudarEmail,
        aoMudarSenha = viewModel::aoMudarSenha,
        aoEntrar = viewModel::aoEntrar,
        aoCriarConta = aoCriarConta,
        modifier = modifier
    )
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    aoMudarEmail: (String) -> Unit,
    aoMudarSenha: (String) -> Unit,
    aoEntrar: () -> Unit,
    aoCriarConta: () -> Unit,
    modifier: Modifier = Modifier
) {
    var explicandoSenha by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            // imePadding antes do scroll: o campo em foco fica acessível com o teclado aberto.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Grass,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(text = "Bem-vindo de volta", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Entre para ver o saldo das suas propriedades.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        CampoTexto(
            valor = uiState.email,
            aoMudar = aoMudarEmail,
            rotulo = "E-mail",
            teclado = KeyboardType.Email
        )

        CampoSenha(
            valor = uiState.senha,
            aoMudar = aoMudarSenha,
            rotulo = "Senha",
            ultimoCampo = true,
            aoConfirmar = aoEntrar
        )

        uiState.erro?.let { erro ->
            Text(
                text = erro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        TextButton(onClick = { explicandoSenha = true }) {
            Text(
                text = "Esqueci minha senha",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = aoEntrar,
            enabled = !uiState.entrando,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (uiState.entrando) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(text = "Entrar", style = MaterialTheme.typography.titleMedium)
            }
        }

        TextButton(onClick = aoCriarConta, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Não tem conta? Criar conta", style = MaterialTheme.typography.bodyLarge)
        }
    }

    if (explicandoSenha) {
        // Conta só local, sem backend: não há recuperação por e-mail; o diálogo explica isso.
        AlertDialog(
            onDismissRequest = { explicandoSenha = false },
            title = { Text("Recuperar a senha") },
            text = {
                Text(
                    "Por enquanto a conta existe só neste celular, então ainda não dá " +
                        "para receber um e-mail de recuperação. Esse recurso chega junto " +
                        "com a sincronização online.\n\nSe não lembrar a senha, é possível " +
                        "criar uma conta nova — mas os dados da conta antiga continuam " +
                        "presos a ela."
                )
            },
            confirmButton = {
                TextButton(onClick = { explicandoSenha = false }) { Text("Entendi") }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun LoginPreview() {
    AgroSaldoTheme {
        LoginScreen(
            uiState = LoginUiState(email = "fabio@exemplo.com", senha = "milho2026"),
            aoMudarEmail = {}, aoMudarSenha = {}, aoEntrar = {}, aoCriarConta = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun LoginComErroPreview() {
    AgroSaldoTheme {
        LoginScreen(
            uiState = LoginUiState(email = "fabio@exemplo.com", erro = "E-mail ou senha incorretos."),
            aoMudarEmail = {}, aoMudarSenha = {}, aoEntrar = {}, aoCriarConta = {}
        )
    }
}
