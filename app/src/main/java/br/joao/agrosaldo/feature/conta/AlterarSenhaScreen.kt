package br.joao.agrosaldo.feature.conta

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.core.ui.CampoSenha
import br.joao.agrosaldo.core.ui.CartaoRequisitosSenha
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme

@Composable
fun AlterarSenhaRoute(
    aoConcluir: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlterarSenhaViewModel = viewModel(factory = AlterarSenhaViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.concluido) {
        if (uiState.concluido) {
            // Toast é o aviso mais simples do Android: aparece por cima de
            // qualquer tela, então continua visível depois de voltar para Conta.
            Toast.makeText(context, "Senha alterada", Toast.LENGTH_SHORT).show()
            aoConcluir()
        }
    }

    AlterarSenhaScreen(
        uiState = uiState,
        aoMudarSenhaAtual = viewModel::aoMudarSenhaAtual,
        aoMudarNovaSenha = viewModel::aoMudarNovaSenha,
        aoMudarConfirmacao = viewModel::aoMudarConfirmacao,
        aoSalvar = viewModel::aoSalvar,
        modifier = modifier
    )
}

@Composable
fun AlterarSenhaScreen(
    uiState: AlterarSenhaUiState,
    aoMudarSenhaAtual: (String) -> Unit,
    aoMudarNovaSenha: (String) -> Unit,
    aoMudarConfirmacao: (String) -> Unit,
    aoSalvar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CampoSenha(
            valor = uiState.senhaAtual,
            aoMudar = aoMudarSenhaAtual,
            rotulo = "Senha atual",
            erro = uiState.erroSenhaAtual != null,
            apoio = uiState.erroSenhaAtual
        )

        CampoSenha(
            valor = uiState.novaSenha,
            aoMudar = aoMudarNovaSenha,
            rotulo = "Nova senha",
            erro = uiState.erroNovaSenha != null,
            apoio = uiState.erroNovaSenha
        )

        CampoSenha(
            valor = uiState.confirmacao,
            aoMudar = aoMudarConfirmacao,
            rotulo = "Confirmar nova senha",
            erro = uiState.erroConfirmacao != null,
            apoio = uiState.erroConfirmacao,
            ultimoCampo = true,
            aoConfirmar = aoSalvar
        )

        CartaoRequisitosSenha(requisitos = uiState.requisitos)

        Button(
            onClick = aoSalvar,
            enabled = !uiState.salvando,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (uiState.salvando) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(text = "Salvar nova senha", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun AlterarSenhaPreview() {
    AgroSaldoTheme {
        AlterarSenhaScreen(
            uiState = AlterarSenhaUiState(senhaAtual = "milho2026", novaSenha = "soja20", nome = "Fábio"),
            aoMudarSenhaAtual = {}, aoMudarNovaSenha = {}, aoMudarConfirmacao = {}, aoSalvar = {}
        )
    }
}
