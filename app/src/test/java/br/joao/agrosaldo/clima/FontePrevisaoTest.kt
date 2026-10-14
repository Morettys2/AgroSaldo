package br.joao.agrosaldo.clima

import br.joao.agrosaldo.data.clima.chamadaBloqueante
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Download da previsão: cancelamento e tempo total sobre uma leitura bloqueante. */
class FontePrevisaoTest {

    private val comecou = CountDownLatch(1)
    private val fechada = CountDownLatch(1)

    /** Imita a leitura do socket: só termina, com erro, quando a conexão é fechada. */
    private val leituraTravada: () -> String = {
        comecou.countDown()
        check(fechada.await(10, TimeUnit.SECONDS)) { "a conexão não foi fechada" }
        throw IOException("Socket closed")
    }

    @Test
    fun `cancelar a busca fecha a conexao e o download para na hora`() = runBlocking {
        val busca = CoroutineScope(Dispatchers.Default).async {
            chamadaBloqueante(60_000, interromper = { fechada.countDown() }, bloqueante = leituraTravada)
        }
        assertTrue(comecou.await(5, TimeUnit.SECONDS))

        busca.cancel()

        assertNotNull("o download continuou travado", withTimeoutOrNull(5_000) { busca.join() })
        val erro = runCatching { busca.await() }.exceptionOrNull()
        assertTrue("o cancelamento não pode virar falta de conexão: $erro", erro is CancellationException)
    }

    @Test
    fun `download que passa do tempo total vira falta de conexao`() = runBlocking {
        val erro = runCatching {
            chamadaBloqueante(100, interromper = { fechada.countDown() }, bloqueante = leituraTravada)
        }.exceptionOrNull()

        assertTrue("erro: $erro", erro is IOException)
        assertEquals("a conexão foi fechada", 0L, fechada.count)
    }

    @Test
    fun `download que termina devolve o corpo e fecha a conexao`() = runBlocking {
        val fechou = AtomicBoolean(false)

        assertEquals("{}", chamadaBloqueante(5_000, interromper = { fechou.set(true) }) { "{}" })
        assertTrue(fechou.get())
    }
}
