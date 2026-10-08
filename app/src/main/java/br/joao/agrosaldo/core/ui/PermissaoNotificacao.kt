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

/** Estado da permissão de notificação e ação para solicitá-la. */
class PermissaoNotificacao(
    val concedida: Boolean,
    /** false abaixo do API 33: não há diálogo de runtime, só os ajustes do sistema. */
    val podePedir: Boolean,
    val pedir: () -> Unit
)

/**
 * POST_NOTIFICATIONS (API 33+). Deve ser solicitada em contexto (na tela do aviso),
 * não na abertura do app: após duas recusas o sistema deixa de exibir o diálogo.
 * Reavalia em cada ON_RESUME para refletir mudanças feitas nos ajustes do sistema.
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
