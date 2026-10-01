package br.joao.agrosaldo.core.localizacao

import android.Manifest
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Estado do botão "Usar minha localização". */
class BuscaDeLocalizacao(
    val buscando: Boolean,
    val buscar: () -> Unit
)

/**
 * Cuida do caminho todo: pede a permissão (só na primeira vez, e só quando
 * a pessoa toca no botão — nunca ao abrir o app) e depois pega a posição.
 *
 * A pessoa pode escolher "localização aproximada" na janela do Android; o
 * app aceita e usa o que vier. Para previsão do tempo, aproximada basta.
 */
@Composable
fun rememberBuscaDeLocalizacao(
    aoEncontrar: (latitude: Double, longitude: Double) -> Unit,
    aoFalhar: (mensagem: String) -> Unit
): BuscaDeLocalizacao {
    val context = LocalContext.current
    var buscando by remember { mutableStateOf(false) }
    // rememberUpdatedState: a resposta do GPS chega depois; garante que ela
    // chame a versão mais nova das funções, não a da primeira composição.
    val aoEncontrarAtual by rememberUpdatedState(aoEncontrar)
    val aoFalharAtual by rememberUpdatedState(aoFalhar)

    // Saiu do formulário antes de o GPS responder: cancela a busca, para a
    // resposta não chegar numa tela que já fechou.
    val busca = remember { arrayOfNulls<CancellationSignal>(1) }
    DisposableEffect(Unit) {
        onDispose { busca[0]?.cancel() }
    }

    val obter = {
        buscando = true
        busca[0] = LocalizacaoDoCelular.obter(
            context = context,
            aoEncontrar = { latitude, longitude ->
                buscando = false
                aoEncontrarAtual(latitude, longitude)
            },
            aoFalhar = { mensagem ->
                buscando = false
                aoFalharAtual(mensagem)
            }
        )
    }

    val pedido = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { respostas ->
        if (respostas.values.any { it }) {
            obter()
        } else {
            aoFalharAtual("Sem permissão de localização. Você ainda pode digitar as coordenadas.")
        }
    }

    return BuscaDeLocalizacao(buscando = buscando) {
        if (LocalizacaoDoCelular.temPermissao(context)) {
            obter()
        } else {
            pedido.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }
}
