package br.joao.agrosaldo.feature.conta

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme

/**
 * Exibida quando a versão da política mudou desde o último aceite. LGPD: mudança
 * relevante (nova finalidade ou novo destino dos dados) exige novo consentimento.
 * Reusa [ConsentimentoScreen].
 */
@Composable
fun NovoConsentimentoRoute(
    aoLerPolitica: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NovoConsentimentoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NovoConsentimentoScreen(
        uiState = uiState,
        aoAceitar = viewModel::aoAceitar,
        aoLerPolitica = aoLerPolitica,
        aoRecusar = viewModel::aoRecusar,
        modifier = modifier
    )
}

@Composable
fun NovoConsentimentoScreen(
    uiState: NovoConsentimentoUiState,
    aoAceitar: () -> Unit,
    aoLerPolitica: () -> Unit,
    aoRecusar: () -> Unit,
    modifier: Modifier = Modifier
) {
    ConsentimentoScreen(
        criando = uiState.processando,
        erro = uiState.erro,
        aoAceitar = aoAceitar,
        aoLerPolitica = aoLerPolitica,
        aoRecusar = aoRecusar,
        modifier = modifier,
        titulo = "A política de privacidade mudou",
        introducao = "A política agora explica o backup do Android e o uso da localização do celular para " +
            "preencher as coordenadas do sítio — só quando você tocar no botão. Para continuar, precisamos " +
            "do seu aceite de novo.",
        textoRecusar = "Não aceito e quero sair"
    )
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun NovoConsentimentoPreview() {
    AgroSaldoTheme {
        NovoConsentimentoScreen(NovoConsentimentoUiState(), aoAceitar = {}, aoLerPolitica = {}, aoRecusar = {})
    }
}
