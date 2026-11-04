package br.joao.agrosaldo.feature.conta

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.joao.agrosaldo.core.ui.BotaoPrincipal
import br.joao.agrosaldo.core.ui.CampoSenha
import br.joao.agrosaldo.core.ui.CartaoRequisitosSenha
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.PreviasDaTela

@Composable
fun AlterarSenhaRoute(aoConcluir: () -> Unit, modifier: Modifier = Modifier, viewModel: AlterarSenhaViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Sempre a versão mais nova do callback, sem reiniciar o efeito quando ela muda.
    val aoConcluirAtual by rememberUpdatedState(aoConcluir)
    LaunchedEffect(uiState.concluido) {
        if (uiState.concluido) {
            // Toast em vez de Snackbar: continua visível após voltar para Conta.
            Toast.makeText(context, "Senha alterada", Toast.LENGTH_SHORT).show()
            aoConcluirAtual()
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
            .padding(horizontal = Espacos.margemDaTela)
            .padding(top = Espacos.pequeno, bottom = Espacos.enorme),
        verticalArrangement = Arrangement.spacedBy(Espacos.padrao)
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

        BotaoPrincipal(
            texto = "Salvar nova senha",
            aoClicar = aoSalvar,
            carregando = uiState.salvando,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@PreviasDaTela
@Composable
private fun AlterarSenhaPreview() {
    AgroSaldoTheme {
        AlterarSenhaScreen(
            uiState = AlterarSenhaUiState(senhaAtual = "milho2026", novaSenha = "soja20", nome = "Maria"),
            aoMudarSenhaAtual = {},
            aoMudarNovaSenha = {},
            aoMudarConfirmacao = {},
            aoSalvar = {}
        )
    }
}
