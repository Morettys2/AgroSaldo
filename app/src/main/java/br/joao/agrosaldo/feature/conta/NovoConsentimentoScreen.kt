package br.joao.agrosaldo.feature.conta

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme

/**
 * Aparece quando a política de privacidade mudou desde o último aceite.
 *
 * LGPD: consentimento vale para as finalidades informadas. Se o app passa a
 * fazer algo novo (aqui, usar o GPS), precisa pedir de novo. Reaproveita a
 * tela de consentimento do cadastro, só com outro título e outro texto.
 */
@Composable
fun NovoConsentimentoRoute(
    aoLerPolitica: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NovoConsentimentoViewModel = viewModel(factory = NovoConsentimentoViewModel.Factory)
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
        introducao = "O app agora pode usar a localização do celular para preencher as coordenadas do sítio " +
            "— só quando você tocar no botão. Para continuar, precisamos do seu aceite de novo.",
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
