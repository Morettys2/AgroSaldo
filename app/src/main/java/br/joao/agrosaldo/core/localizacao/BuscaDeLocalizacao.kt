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

/** Estado e ação do botão "Usar minha localização". */
class BuscaDeLocalizacao(
    val buscando: Boolean,
    val buscar: () -> Unit
)

/**
 * Solicita permissão de localização sob demanda (no toque) e obtém a posição atual.
 * Permissão aproximada é aceita: a precisão basta para previsão do tempo.
 */
@Composable
fun rememberBuscaDeLocalizacao(
    aoEncontrar: (latitude: Double, longitude: Double) -> Unit,
    aoFalhar: (mensagem: String) -> Unit
): BuscaDeLocalizacao {
    val context = LocalContext.current
    var buscando by remember { mutableStateOf(false) }
    val aoEncontrarAtual by rememberUpdatedState(aoEncontrar)
    val aoFalharAtual by rememberUpdatedState(aoFalhar)

    // Cancela a busca pendente ao sair da composição.
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
