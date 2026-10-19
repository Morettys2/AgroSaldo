package br.joao.agrosaldo.aviso

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.joao.agrosaldo.domain.clima.AvisoDeChuva
import br.joao.agrosaldo.domain.clima.VerificadorDeChuva
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Worker da verificação periódica de chuva. Delega para o VerificadorDeChuva (domínio),
 * que chama de volta para exibir cada notificação.
 */
@HiltWorker
class VerificacaoChuvaWorker @AssistedInject constructor(
    @Assisted contexto: Context,
    @Assisted parametros: WorkerParameters,
    private val verificador: VerificadorDeChuva,
    private val ajustes: AjustesAvisoRepository
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result = verificarChuva(
        verificador = verificador,
        janelaHoras = ajustes.ajustes.value.limites.janelaHoras,
        tentativa = runAttemptCount,
        podeNotificar = { NotificadorDeChuva.podeNotificar(applicationContext) },
        mostrar = { aviso, janela -> NotificadorDeChuva.mostrar(applicationContext, aviso, janela) }
    )
}

/**
 * O trabalho do Worker sem o Worker: recebe tudo de fora para ser testado no JVM, com
 * fakes (veja `VerificacaoChuvaWorkerTest`). O [VerificacaoChuvaWorker] só junta as peças
 * reais do Android.
 *
 * @param tentativa quantas vezes o WorkManager já rodou este trabalho (0 na primeira).
 * @return [ListenableWorker.Result.retry] numa falha passageira (sem rede, serviço fora do
 *   ar) até [TENTATIVAS_MAXIMAS]; depois disso, ou sem falha, success.
 */
internal suspend fun verificarChuva(
    verificador: VerificadorDeChuva,
    janelaHoras: Int,
    tentativa: Int,
    podeNotificar: () -> Boolean,
    mostrar: (AvisoDeChuva, Int) -> Unit
): ListenableWorker.Result {
    val resultado = verificador.verificar { aviso ->
        // Conferido a cada aviso: a permissão ou o canal podem mudar durante a verificação.
        val pode = podeNotificar()
        if (pode) mostrar(aviso, janelaHoras)
        pode
    }
    // Sem rede ou serviço fora do ar: retry com backoff, até 3 tentativas.
    return if (resultado.falhaPassageira && tentativa < TENTATIVAS_MAXIMAS) {
        ListenableWorker.Result.retry()
    } else {
        ListenableWorker.Result.success()
    }
}

/** Depois disso, a próxima chance é a verificação periódica seguinte (12 h). */
internal const val TENTATIVAS_MAXIMAS = 3

/** [AgendaDeVerificacao] sobre WorkManager. */
class AgendaWorkManager(private val context: Context) : AgendaDeVerificacao {

    override fun ligar() {
        val pedido = PeriodicWorkRequestBuilder<VerificacaoChuvaWorker>(INTERVALO_HORAS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, ESPERA_INICIAL_MINUTOS, TimeUnit.MINUTES)
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

        /** Duas verificações por dia. */
        const val INTERVALO_HORAS = 12L

        /** Primeira espera do backoff exponencial quando a verificação falha. */
        const val ESPERA_INICIAL_MINUTOS = 15L
    }
}
