package br.joao.agrosaldo.core.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.joao.agrosaldo.aviso.NotificadorDeChuva
import br.joao.agrosaldo.core.util.DestinoDaPermissao
import br.joao.agrosaldo.core.util.RegrasDeNotificacao

/** Estado da permissão de notificação e ação para resolvê-la. */
class PermissaoNotificacao(
    val concedida: Boolean,
    /**
     * Mostra o diálogo do sistema ou, quando ele não resolve (permissão negada de vez,
     * notificações do app ou canal do aviso desligados), abre os ajustes de notificação.
     */
    val pedir: () -> Unit
)

/**
 * POST_NOTIFICATIONS (API 33+). Deve ser solicitada em contexto (na tela do aviso),
 * não na abertura do app: após duas recusas o sistema deixa de exibir o diálogo, e então
 * [PermissaoNotificacao.pedir] leva aos ajustes do app.
 * Reavalia em cada ON_RESUME para refletir mudanças feitas nos ajustes do sistema.
 */
@Composable
fun rememberPermissaoNotificacao(): PermissaoNotificacao {
    val context = LocalContext.current
    var concedida by remember { mutableStateOf(NotificadorDeChuva.podeNotificar(context)) }
    // shouldShowRequestPermissionRationale antes do pedido; ver RegrasDeNotificacao.dialogoBloqueado.
    var explicarAntes by remember { mutableStateOf(false) }

    val preferencias = remember(context) { context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE) }

    val pedido = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { aceitou ->
        concedida = NotificadorDeChuva.podeNotificar(context)
        val jaPediuAntes = preferencias.getBoolean(CHAVE_JA_PEDIU, false)
        preferencias.edit { putBoolean(CHAVE_JA_PEDIU, true) }
        if (RegrasDeNotificacao.dialogoBloqueado(aceitou, explicarAntes, deveExplicar(context), jaPediuAntes)) {
            abrirAjustes(context, DestinoDaPermissao.AJUSTES_DO_APP)
        }
    }

    LifecycleResumeEffect(Unit) {
        concedida = NotificadorDeChuva.podeNotificar(context)
        onPauseOrDispose { }
    }

    return PermissaoNotificacao(concedida = concedida) {
        val destino = destinoAtual(context)
        when {
            destino == null -> {
                concedida = NotificadorDeChuva.podeNotificar(context)
            }
            destino == DestinoDaPermissao.DIALOGO -> {
                explicarAntes = deveExplicar(context)
                pedido.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> abrirAjustes(context, destino)
        }
    }
}

/** Preferências do aparelho (fora do backup): se o pedido de notificação já foi feito alguma vez. */
private const val ARQUIVO = "permissoes"
private const val CHAVE_JA_PEDIU = "notificacao_ja_pedida"

private fun destinoAtual(context: Context): DestinoDaPermissao? {
    val permissaoPendente = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    val gerenciador = NotificationManagerCompat.from(context)
    val canal = gerenciador.getNotificationChannelCompat(NotificadorDeChuva.CANAL)
    return RegrasDeNotificacao.destino(
        permissaoPendente = permissaoPendente,
        appLigado = gerenciador.areNotificationsEnabled(),
        canalLigado = canal == null || canal.importance != NotificationManagerCompat.IMPORTANCE_NONE
    )
}

private fun deveExplicar(context: Context): Boolean {
    val activity = context.activity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Ajustes de notificação do app ou do canal do aviso de chuva (telas existentes desde a API 26). */
@Suppress("SwallowedException") // aparelho sem a tela de ajustes: cai para a tela geral
private fun abrirAjustes(context: Context, destino: DestinoDaPermissao) {
    val intent = when (destino) {
        DestinoDaPermissao.AJUSTES_DO_CANAL -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_CHANNEL_ID, NotificadorDeChuva.CANAL)
        else -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Alguns fabricantes não expõem essas telas; a de detalhes do app sempre existe.
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        )
    }
}
