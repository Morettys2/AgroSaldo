package br.joao.agrosaldo.aviso

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * O agendamento do aviso de chuva (TST-01). Os pedidos são conferidos direto; a fila, com o
 * WorkManager de verdade no Robolectric, sem o `work-testing`: o Worker nunca chega a rodar,
 * porque o executor dos Workers descarta as tarefas. O teste só olha o que ficou na fila.
 */
@RunWith(RobolectricTestRunner::class)
class AgendaWorkManagerTest {

    @Test
    fun `verificacao periodica roda o worker da chuva a cada 12 horas so com internet`() {
        val pedido = AgendaWorkManager.pedidoPeriodico()

        assertEquals(VerificacaoChuvaWorker::class.java.name, pedido.workSpec.workerClassName)
        assertEquals(TimeUnit.HOURS.toMillis(12), pedido.workSpec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, pedido.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun `verificacao que falha tenta de novo em 15 minutos com espera crescente`() {
        val pedido = AgendaWorkManager.pedidoPeriodico()

        assertEquals(BackoffPolicy.EXPONENTIAL, pedido.workSpec.backoffPolicy)
        assertEquals(TimeUnit.MINUTES.toMillis(15), pedido.workSpec.backoffDelayDuration)
    }

    @Test
    fun `verificar agora roda o worker da chuva uma vez so`() {
        val pedido = AgendaWorkManager.pedidoImediato()

        assertEquals(VerificacaoChuvaWorker::class.java.name, pedido.workSpec.workerClassName)
        assertFalse(pedido.workSpec.isPeriodic)
    }

    // Um teste só para a fila: o WorkManager é único no processo e só pode ser iniciado uma vez.
    @Test
    fun `na fila ligar mantem o horario, verificar agora substitui e desligar cancela`() {
        val contexto: Context = ApplicationProvider.getApplicationContext()
        // Uma thread só para as tarefas internas: o Room do WorkManager não aceita a thread principal,
        // e com uma thread a consulta à fila sempre roda depois do pedido feito antes dela.
        val tarefas = Executors.newSingleThreadExecutor()
        try {
            WorkManager.initialize(
                contexto,
                Configuration.Builder()
                    .setTaskExecutor(tarefas)
                    .setExecutor { }
                    .setWorkerFactory(FabricaDeWorkerParado)
                    .build()
            )
            val workManager = WorkManager.getInstance(contexto)
            val agenda = AgendaWorkManager(contexto)
            fun fila(nome: String) = workManager.getWorkInfosForUniqueWork(nome).get()

            agenda.ligar()
            val periodico = fila(AgendaWorkManager.NOME).single()
            agenda.ligar()
            assertEquals(periodico.id, fila(AgendaWorkManager.NOME).single().id)

            agenda.verificarAgora()
            val primeiraVez = fila(AgendaWorkManager.NOME_AGORA).single()
            agenda.verificarAgora()
            assertNotEquals(primeiraVez.id, fila(AgendaWorkManager.NOME_AGORA).single().id)

            agenda.desligar()
            assertEquals(WorkInfo.State.CANCELLED, fila(AgendaWorkManager.NOME).single().state)
        } finally {
            tarefas.shutdown()
        }
    }

    /** Cria um Worker que não faz nada; com o executor que descarta tarefas, ele nem chega a rodar. */
    private object FabricaDeWorkerParado : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
            WorkerParado(appContext, workerParameters)
    }

    private class WorkerParado(contexto: Context, parametros: WorkerParameters) : Worker(contexto, parametros) {
        override fun doWork(): Result = Result.success()
    }
}
