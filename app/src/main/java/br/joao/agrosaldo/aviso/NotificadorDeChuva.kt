package br.joao.agrosaldo.aviso

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.joao.agrosaldo.MainActivity
import br.joao.agrosaldo.R
import br.joao.agrosaldo.domain.clima.AvaliacaoChuva
import br.joao.agrosaldo.domain.clima.AvisoDeChuva
import br.joao.agrosaldo.domain.clima.MotivoChuva
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Mostra a notificação de chuva (tela 17 do Figma).
 *
 * Desde o Android 8 toda notificação precisa de um "canal". O canal aparece
 * nas configurações do celular com o nome "Aviso de chuva": o produtor pode
 * silenciar só ele sem desligar o app inteiro.
 */
object NotificadorDeChuva {

    const val CANAL = "aviso_chuva"
    const val EXTRA_DESTINO = "destino"
    const val DESTINO_CLIMA = "clima"

    fun criarCanal(context: Context) {
        val canal = NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Aviso de chuva")
            .setDescription("Avisa quando há chuva prevista nas suas propriedades.")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(canal)
    }

    /** Android 13+ pede permissão para notificar; antes disso, basta não estar desligado. */
    fun podeNotificar(context: Context): Boolean {
        val permitido = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val gerenciador = NotificationManagerCompat.from(context)
        // A pessoa pode desligar só o canal "Aviso de chuva" nos ajustes do
        // Android, deixando o resto do app ligado.
        val canal = gerenciador.getNotificationChannelCompat(CANAL)
        val canalLigado = canal == null || canal.importance != NotificationManagerCompat.IMPORTANCE_NONE
        return permitido && gerenciador.areNotificationsEnabled() && canalLigado
    }

    fun mostrar(context: Context, aviso: AvisoDeChuva, janelaHoras: Int) {
        // Sem permissão, não insiste: o app mostra o aviso dentro da tela de clima.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val abrirClima = Intent(context, MainActivity::class.java).apply {
            // SINGLE_TOP: com o app aberto, a tela atual recebe o pedido
            // (onNewIntent) em vez de ser fechada e criada de novo.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DESTINO, DESTINO_CLIMA)
        }
        val aoTocar = PendingIntent.getActivity(
            context,
            aviso.propriedadeId.hashCode(),
            abrirClima,
            // IMMUTABLE é obrigatório desde o Android 12: ninguém de fora
            // consegue alterar para onde o toque leva.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Com a tela bloqueada aparece só esta versão, sem nome nem números:
        // quem pegar o celular na mesa não lê os dados do produtor.
        val versaoPublica = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao_chuva)
            .setContentTitle(TextosDoAviso.TITULO_PUBLICO)
            .setContentText(TextosDoAviso.CORPO_PUBLICO)
            .build()

        val corpo = TextosDoAviso.corpo(aviso, janelaHoras)
        val notificacao = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao_chuva)
            .setContentTitle(TextosDoAviso.titulo(aviso))
            .setContentText(corpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setContentIntent(aoTocar)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(versaoPublica)
            .build()

        // Mesmo id por propriedade: um aviso novo substitui o antigo em vez de empilhar.
        NotificationManagerCompat.from(context).notify(aviso.propriedadeId.hashCode(), notificacao)
    }

    /** Ferramenta de teste: mostra como o aviso fica, sem depender de chover de verdade. */
    fun mostrarExemplo(context: Context) {
        val aviso = AvisoDeChuva(
            propriedadeId = "exemplo",
            nomePropriedade = "Propriedade de exemplo",
            avaliacao = AvaliacaoChuva(
                vaiChover = true,
                probabilidadeMaxima = 85,
                chuvaTotalMm = 6.0,
                inicioPrevisto = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(3),
                motivo = MotivoChuva.AMBOS,
                temDados = true
            )
        )
        mostrar(context, aviso, janelaHoras = 12)
    }
}
