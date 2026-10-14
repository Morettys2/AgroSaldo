package br.joao.agrosaldo.aviso

import androidx.work.ListenableWorker
import br.joao.agrosaldo.clima.ApoioClima.AGORA
import br.joao.agrosaldo.clima.ApoioClima.previsao
import br.joao.agrosaldo.data.preferencias.AjustesAvisoMemoria
import br.joao.agrosaldo.data.repository.ClimaRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.domain.clima.AvisoDeChuva
import br.joao.agrosaldo.domain.clima.VerificadorDeChuva
import br.joao.agrosaldo.domain.model.Propriedade
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O que o Worker do aviso de chuva faz a cada 12 h, testado sem Android: a função
 * [verificarChuva] recebe o verificador (com fakes) e as funções de notificar.
 */
class VerificacaoChuvaWorkerTest {

    private val fazenda = Propriedade(
        id = "p1", nome = "Fazenda Santa Clara", cultura = "Milho",
        areaHectares = 50.0, latitude = -21.7, longitude = -46.9
    )
    private val comChuva = previsao(mapOf(18 to (85 to 6.0)))

    private fun verificador(clima: ClimaRepositoryMemoria) = VerificadorDeChuva(
        SessaoMemoria("u1"), PropriedadeRepositoryMemoria(listOf(fazenda)), clima, AjustesAvisoMemoria()
    ) { AGORA.toEpochMilli() }

    private val mostrados = mutableListOf<Pair<AvisoDeChuva, Int>>()

    private suspend fun rodar(
        clima: ClimaRepositoryMemoria,
        tentativa: Int = 0,
        podeNotificar: Boolean = true
    ) = verificarChuva(
        verificador = verificador(clima),
        janelaHoras = 12,
        tentativa = tentativa,
        podeNotificar = { podeNotificar },
        mostrar = { aviso, janela -> mostrados += aviso to janela }
    )

    @Test
    fun `chuva prevista mostra a notificacao com a janela dos ajustes`() = runTest {
        val clima = ClimaRepositoryMemoria().apply { respostas = mapOf("p1" to comChuva) }

        val resultado = rodar(clima)

        assertEquals(ListenableWorker.Result.success(), resultado)
        assertEquals("p1", mostrados.single().first.propriedadeId)
        assertEquals(12, mostrados.single().second)
    }

    @Test
    fun `sem permissao de notificacao nada aparece e o trabalho termina`() = runTest {
        val clima = ClimaRepositoryMemoria().apply { respostas = mapOf("p1" to comChuva) }

        val resultado = rodar(clima, podeNotificar = false)

        assertEquals(ListenableWorker.Result.success(), resultado)
        assertTrue(mostrados.isEmpty())
    }

    @Test
    fun `sem internet o WorkManager tenta de novo`() = runTest {
        val clima = ClimaRepositoryMemoria().apply { semInternet = true }

        assertEquals(ListenableWorker.Result.retry(), rodar(clima, tentativa = 0))
        assertEquals(ListenableWorker.Result.retry(), rodar(clima, tentativa = TENTATIVAS_MAXIMAS - 1))
    }

    @Test
    fun `depois de 3 tentativas sem internet desiste ate a proxima verificacao`() = runTest {
        val clima = ClimaRepositoryMemoria().apply { semInternet = true }

        assertEquals(ListenableWorker.Result.success(), rodar(clima, tentativa = TENTATIVAS_MAXIMAS))
    }

    @Test
    fun `servico fora do ar tambem e falha passageira`() = runTest {
        val clima = ClimaRepositoryMemoria().apply { indisponivel = true }

        assertEquals(ListenableWorker.Result.retry(), rodar(clima))
    }

    @Test
    fun `sem chuva nao notifica`() = runTest {
        val clima = ClimaRepositoryMemoria().apply { respostas = mapOf("p1" to previsao()) }

        assertEquals(ListenableWorker.Result.success(), rodar(clima))
        assertTrue(mostrados.isEmpty())
    }
}
