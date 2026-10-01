package br.joao.agrosaldo.core.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.joao.agrosaldo.aviso.NotificadorDeChuva

/** Se o app pode notificar, e como pedir a permissão. */
class PermissaoNotificacao(
    val concedida: Boolean,
    /** Só o Android 13+ tem a janelinha de permissão; antes disso, só nas configurações. */
    val podePedir: Boolean,
    val pedir: () -> Unit
)

/**
 * Desde o Android 13 o app precisa pedir permissão para notificar.
 *
 * A boa prática é pedir NO CONTEXTO — quando a pessoa está olhando o aviso
 * de chuva e entende para que serve — e não assim que o app abre. Pedido
 * fora de contexto costuma ser negado, e depois de negar duas vezes o
 * Android não deixa mais o app perguntar.
 *
 * A checagem roda de novo sempre que a tela volta para a frente: se a
 * pessoa ligou a notificação nas configurações do celular, a tela percebe.
 */
@Composable
fun rememberPermissaoNotificacao(): PermissaoNotificacao {
    val context = LocalContext.current
    var concedida by remember { mutableStateOf(NotificadorDeChuva.podeNotificar(context)) }

    val pedido = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { aceitou ->
        concedida = aceitou && NotificadorDeChuva.podeNotificar(context)
    }

    LifecycleResumeEffect(Unit) {
        concedida = NotificadorDeChuva.podeNotificar(context)
        onPauseOrDispose { }
    }

    val podePedir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    return PermissaoNotificacao(
        concedida = concedida,
        podePedir = podePedir,
        pedir = { if (podePedir) pedido.launch(Manifest.permission.POST_NOTIFICATIONS) }
    )
}
