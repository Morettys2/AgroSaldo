package br.joao.agrosaldo.aviso

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
 * Notificação do aviso de chuva, no canal próprio [CANAL] (silenciável
 * separadamente nos ajustes do Android).
 */
object NotificadorDeChuva {

    const val CANAL = "aviso_chuva"
    const val EXTRA_DESTINO = "destino"
    const val DESTINO_CLIMA = "clima"

    /** Id da propriedade do aviso; a navegação só o usa se a propriedade for do usuário conectado. */
    const val EXTRA_PROPRIEDADE = "propriedade_id"

    /** Id comum a todos os avisos; cada propriedade é a tag da sua notificação. */
    private const val ID_AVISO = 1
    private const val ESQUEMA = "agrosaldo"
    private const val HORAS_ATE_A_CHUVA_DE_EXEMPLO = 3L

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
        // O canal pode estar desligado mesmo com as notificações do app ativas.
        val canal = gerenciador.getNotificationChannelCompat(CANAL)
        val canalLigado = canal == null || canal.importance != NotificationManagerCompat.IMPORTANCE_NONE
        return permitido && gerenciador.areNotificationsEnabled() && canalLigado
    }

    fun mostrar(context: Context, aviso: AvisoDeChuva, janelaHoras: Int) {
        // Sem permissão, retorna sem notificar; o aviso continua visível na tela de clima.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val abrirClima = Intent(context, MainActivity::class.java).apply {
            // SINGLE_TOP: com o app aberto, entrega via onNewIntent em vez de recriar a Activity.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // data distinto por propriedade: PendingIntents que só diferem nos extras são o mesmo,
            // e o FLAG_UPDATE_CURRENT faria o aviso de uma propriedade abrir a de outra.
            data = Uri.Builder().scheme(ESQUEMA).authority(DESTINO_CLIMA).appendPath(aviso.propriedadeId).build()
            putExtra(EXTRA_DESTINO, DESTINO_CLIMA)
            putExtra(EXTRA_PROPRIEDADE, aviso.propriedadeId)
        }
        val aoTocar = PendingIntent.getActivity(
            context,
            0,
            abrirClima,
            // FLAG_IMMUTABLE é obrigatório a partir do API 31.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Versão exibida na tela de bloqueio, sem nome da propriedade nem números.
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

        // Tag = propriedade: um aviso novo substitui o anterior da mesma propriedade, e
        // propriedades diferentes nunca colidem (o hashCode do id podia colidir).
        NotificationManagerCompat.from(context).notify(aviso.propriedadeId, ID_AVISO, notificacao)
    }

    /**
     * Exibe uma notificação com dados fictícios. Usado só pelas ferramentas de teste de builds debug.
     * Recebe a propriedade selecionada para o toque abrir o Clima como num aviso real (um id
     * que não é do usuário conectado seria ignorado).
     */
    fun mostrarExemplo(context: Context, propriedadeId: String?) {
        val aviso = AvisoDeChuva(
            propriedadeId = propriedadeId ?: "exemplo",
            nomePropriedade = "Propriedade de exemplo",
            avaliacao = AvaliacaoChuva(
                vaiChover = true,
                probabilidadeMaxima = 85,
                chuvaTotalMm = 6.0,
                inicioPrevisto = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(HORAS_ATE_A_CHUVA_DE_EXEMPLO),
                motivo = MotivoChuva.AMBOS,
                temDados = true
            )
        )
        mostrar(context, aviso, janelaHoras = 12)
    }
}
