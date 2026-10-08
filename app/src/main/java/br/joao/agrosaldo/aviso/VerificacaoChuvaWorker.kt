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
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import java.util.concurrent.TimeUnit

/**
 * Worker da verificação periódica de chuva. Apenas delega para o VerificadorDeChuva
 * (domínio) e exibe as notificações resultantes.
 */
class VerificacaoChuvaWorker(
    contexto: Context,
    parametros: WorkerParameters
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        // Idempotente; normalmente o AgroSaldoApp já inicializou.
        Repositorios.inicializar(applicationContext)

        val resultado = Repositorios.verificadorDeChuva.verificar(
            podeNotificar = NotificadorDeChuva.podeNotificar(applicationContext)
        )
        val janela = Repositorios.ajustesAviso.ajustes.value.limites.janelaHoras
        resultado.avisos.forEach { NotificadorDeChuva.mostrar(applicationContext, it, janela) }

        // Falha de rede: retry com backoff, até 3 tentativas.
        return if (resultado.faltouConexao && runAttemptCount < 3) Result.retry() else Result.success()
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
