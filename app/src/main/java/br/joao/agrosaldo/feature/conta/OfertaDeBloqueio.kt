package br.joao.agrosaldo.feature.conta

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import br.joao.agrosaldo.core.seguranca.Biometria
import br.joao.agrosaldo.core.seguranca.acharFragmentActivity
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme

/**
 * Oferta, logo depois do cadastro, de pedir a digital ou o PIN do celular ao abrir o app.
 * Opcional: "Agora não" dispensa, e o ajuste fica também na tela de Conta. Não aparece se
 * o aparelho não tiver biometria nem credencial cadastrada. [aoLigar] só é chamado depois
 * de a pessoa confirmar no prompt, como ao ligar pela tela de Conta.
 */
@Composable
fun OfertaDeBloqueioRoute(aoLigar: () -> Unit, aoDispensar: () -> Unit) {
    val context = LocalContext.current
    val disponivel = remember(context) { Biometria.disponivel(context) }
    if (!disponivel) return

    OfertaDeBloqueioDialog(
        aoLigar = {
            val activity = context.acharFragmentActivity()
            if (activity != null) Biometria.confirmarMudancaDoBloqueio(activity, ativar = true, aoConfirmar = aoLigar)
        },
        aoDispensar = aoDispensar
    )
}

@Composable
fun OfertaDeBloqueioDialog(aoLigar: () -> Unit, aoDispensar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoDispensar,
        icon = { Icon(Icons.Outlined.Fingerprint, contentDescription = null) },
        title = { Text("Proteger o app com a digital?") },
        text = {
            Text(
                "O AgroSaldo pede a digital ou o PIN do celular ao abrir e depois de 2 minutos fora. " +
                    "Assim, quem pegar o celular desbloqueado não vê suas contas. Dá para mudar depois em Conta."
            )
        },
        confirmButton = { TextButton(onClick = aoLigar) { Text("Ligar") } },
        dismissButton = { TextButton(onClick = aoDispensar) { Text("Agora não") } },
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Preview(showBackground = true)
@Composable
private fun OfertaDeBloqueioPreview() {
    AgroSaldoTheme {
        OfertaDeBloqueioDialog(aoLigar = {}, aoDispensar = {})
    }
}
