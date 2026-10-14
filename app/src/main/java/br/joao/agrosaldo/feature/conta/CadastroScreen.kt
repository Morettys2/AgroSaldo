package br.joao.agrosaldo.feature.conta

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.joao.agrosaldo.core.ui.BotaoPrincipal
import br.joao.agrosaldo.core.ui.CampoSenha
import br.joao.agrosaldo.core.ui.CampoTexto
import br.joao.agrosaldo.core.ui.CartaoRequisitosSenha
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos

/**
 * Formulário de criação de conta (RF01). O [viewModel] é injetado pela AppNavegacao,
 * sem valor padrão, porque é compartilhado com a tela de consentimento.
 */
@Composable
fun CadastroRoute(
    viewModel: CadastroViewModel,
    aoIrParaConsentimento: () -> Unit,
    aoVoltarParaLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Evento de navegação consumido antes de navegar; sem isso, voltar do
    // consentimento reabriria o consentimento.
    LaunchedEffect(uiState.irParaConsentimento) {
        if (uiState.irParaConsentimento) {
            viewModel.aoAbrirConsentimento()
            aoIrParaConsentimento()
        }
    }

    CadastroScreen(
        uiState = uiState,
        aoMudarNome = viewModel::aoMudarNome,
        aoMudarEmail = viewModel::aoMudarEmail,
        aoMudarSenha = viewModel::aoMudarSenha,
        aoMudarConfirmacao = viewModel::aoMudarConfirmacao,
        aoContinuar = viewModel::aoContinuar,
        aoVoltarParaLogin = aoVoltarParaLogin,
        modifier = modifier
    )
}

@Composable
fun CadastroScreen(
    uiState: CadastroUiState,
    aoMudarNome: (String) -> Unit,
    aoMudarEmail: (String) -> Unit,
    aoMudarSenha: (String) -> Unit,
    aoMudarConfirmacao: (String) -> Unit,
    aoContinuar: () -> Unit,
    aoVoltarParaLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // imePadding antes do scroll: o campo em foco fica acessível com o teclado aberto.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Espacos.margemDaTela)
            .padding(top = Espacos.pequeno, bottom = Espacos.enorme),
        verticalArrangement = Arrangement.spacedBy(Espacos.padrao)
    ) {
        Text(
            text = "Leva menos de um minuto. Os dados ficam no seu aparelho.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        CampoTexto(
            valor = uiState.nome,
            aoMudar = aoMudarNome,
            rotulo = "Nome completo",
            erro = uiState.erroNome != null,
            apoio = uiState.erroNome
        )

        CampoTexto(
            valor = uiState.email,
            aoMudar = aoMudarEmail,
            rotulo = "E-mail",
            erro = uiState.erroEmail != null,
            apoio = uiState.erroEmail,
            teclado = KeyboardType.Email
        )

        CampoSenha(
            valor = uiState.senha,
            aoMudar = aoMudarSenha,
            rotulo = "Senha",
            erro = uiState.erroSenha != null,
            apoio = uiState.erroSenha
        )

        CampoSenha(
            valor = uiState.confirmacao,
            aoMudar = aoMudarConfirmacao,
            rotulo = "Confirmar senha",
            erro = uiState.erroConfirmacao != null,
            apoio = uiState.erroConfirmacao,
            ultimoCampo = true,
            aoConfirmar = aoContinuar
        )

        CartaoRequisitosSenha(requisitos = uiState.requisitos)

        BotaoPrincipal(
            texto = "Continuar",
            aoClicar = aoContinuar,
            carregando = uiState.verificando,
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(onClick = aoVoltarParaLogin, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Já tenho conta",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun CadastroPreview() {
    AgroSaldoTheme {
        CadastroScreen(
            uiState = CadastroUiState(
                nome = "Fábio Moretti",
                email = "fabio@exemplo.com",
                senha = "milho20",
                confirmacao = ""
            ),
            aoMudarNome = {}, aoMudarEmail = {}, aoMudarSenha = {}, aoMudarConfirmacao = {},
            aoContinuar = {}, aoVoltarParaLogin = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun CadastroComErrosPreview() {
    AgroSaldoTheme {
        CadastroScreen(
            uiState = CadastroUiState(
                erroNome = "Informe seu nome.",
                erroEmail = "Informe um e-mail válido, como nome@exemplo.com.",
                erroSenha = "A senha não cumpre os requisitos abaixo."
            ),
            aoMudarNome = {}, aoMudarEmail = {}, aoMudarSenha = {}, aoMudarConfirmacao = {},
            aoContinuar = {}, aoVoltarParaLogin = {}
        )
    }
}
