package br.joao.agrosaldo.aviso

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.joao.agrosaldo.di.dependencias
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import java.util.concurrent.TimeUnit

/**
 * Worker da verificação periódica de chuva. Delega para o VerificadorDeChuva (domínio),
 * que chama de volta para exibir cada notificação.
 */
class VerificacaoChuvaWorker(
    contexto: Context,
    parametros: WorkerParameters
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        // O AgroSaldoApp sempre existe antes do Worker, mesmo quando o processo sobe só para ele.
        val dependencias = applicationContext.dependencias

        val janela = dependencias.ajustesAviso.ajustes.value.limites.janelaHoras
        val resultado = dependencias.verificadorDeChuva.verificar { aviso ->
            // Conferido a cada aviso: a permissão ou o canal podem mudar durante a verificação.
            val pode = NotificadorDeChuva.podeNotificar(applicationContext)
            if (pode) NotificadorDeChuva.mostrar(applicationContext, aviso, janela)
            pode
        }

        // Sem rede ou serviço fora do ar: retry com backoff, até 3 tentativas.
        return if (resultado.falhaPassageira && runAttemptCount < 3) Result.retry() else Result.success()
    }
}

/** [AgendaDeVerificacao] sobre WorkManager. */
class AgendaWorkManager(private val context: Context) : AgendaDeVerificacao {

    override fun ligar() {
        val pedido = PeriodicWorkRequestBuilder<VerificacaoChuvaWorker>(INTERVALO_HORAS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        // KEEP: se já agendado, preserva o horário; reabrir o app não reinicia o período.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(NOME, ExistingPeriodicWorkPolicy.KEEP, pedido)
    }

    override fun desligar() {
        WorkManager.getInstance(context).cancelUniqueWork(NOME)
    }

    override fun verificarAgora() {
        val pedido = OneTimeWorkRequestBuilder<VerificacaoChuvaWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOME_AGORA, ExistingWorkPolicy.REPLACE, pedido)
    }

    private companion object {
        const val NOME = "verificacao-de-chuva"
        const val NOME_AGORA = "verificacao-de-chuva-agora"
        /** Duas verificações por dia (RN04). */
        const val INTERVALO_HORAS = 12L
    }
}
