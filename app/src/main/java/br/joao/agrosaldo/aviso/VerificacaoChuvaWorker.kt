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
 * A verificação de chuva que roda sozinha, com o app fechado.
 *
 * O WorkManager é o jeito recomendado pelo Android para trabalho em
 * segundo plano: ele respeita a bateria (espera ter internet, agrupa com
 * outros apps) e sobrevive a reiniciar o celular.
 *
 * O Worker em si é fino: quem decide tudo é o VerificadorDeChuva, no domínio.
 */
class VerificacaoChuvaWorker(
    contexto: Context,
    parametros: WorkerParameters
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        // Normalmente o AgroSaldoApp já inicializou; chamar de novo não faz nada.
        Repositorios.inicializar(applicationContext)

        val resultado = Repositorios.verificadorDeChuva.verificar()
        val janela = Repositorios.ajustesAviso.ajustes.value.limites.janelaHoras
        resultado.avisos.forEach { NotificadorDeChuva.mostrar(applicationContext, it, janela) }

        // Sem internet: tenta de novo daqui a pouco, no máximo 3 vezes.
        return if (resultado.faltouConexao && runAttemptCount < 3) Result.retry() else Result.success()
    }
}

/** Liga e desliga a verificação periódica. */
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

        // KEEP: se já está agendado, mantém o horário que já tinha. Abrir o app
        // não reinicia a contagem nem dispara verificação extra.
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
        /** 12 em 12 horas = 2 vezes por dia (RN04). */
        const val INTERVALO_HORAS = 12L
    }
}
