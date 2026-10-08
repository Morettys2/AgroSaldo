package br.joao.agrosaldo.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

/** [Desfazer] (oferta de desfazer exclusão) e [SelecaoAtual] (propriedade e mês compartilhados entre as abas). */
class DesfazerESelecaoTest {

    @Test
    fun `desfazer restaura e tira a oferta da tela`() = runTest {
        val desfazer = Desfazer()
        var restaurou = 0
        desfazer.oferecer("Lançamento excluído") { restaurou++ }
        val pendente = desfazer.pendente.value

        assertTrue(desfazer.desfazer(checkNotNull(pendente)))

        assertEquals(1, restaurou)
        assertNull(desfazer.pendente.value)
    }

    @Test
    fun `tocar duas vezes rapido em desfazer restaura uma vez so`() = runTest {
        val desfazer = Desfazer()
        var restaurou = 0
        // Restauração bloqueada até [gravacao] completar: o segundo toque chega com a primeira em andamento.
        val gravacao = CompletableDeferred<Unit>()
        desfazer.oferecer("x") {
            restaurou++
            gravacao.await()
        }
        val pendente = checkNotNull(desfazer.pendente.value)

        val primeiro = launch { desfazer.desfazer(pendente) }
        val segundo = launch { desfazer.desfazer(pendente) }
        yield()
        gravacao.complete(Unit)
        primeiro.join()
        segundo.join()

        assertEquals(1, restaurou)
        assertNull(desfazer.pendente.value)
    }

    @Test
    fun `a barra sumiu sozinha - a exclusao fica valendo`() = runTest {
        val desfazer = Desfazer()
        var restaurou = 0
        desfazer.oferecer("x") { restaurou++ }
        val pendente = checkNotNull(desfazer.pendente.value)

        desfazer.dispensar(pendente)
        desfazer.desfazer(pendente)

        assertNull(desfazer.pendente.value)
        assertEquals(0, restaurou)
    }

    @Test
    fun `uma exclusao nova substitui a anterior, que nao volta mais`() = runTest {
        val desfazer = Desfazer()
        var primeira = 0
        desfazer.oferecer("primeira") { primeira++ }
        val antiga = checkNotNull(desfazer.pendente.value)
        desfazer.oferecer("segunda") { }

        desfazer.desfazer(antiga)
        desfazer.dispensar(antiga)

        assertEquals(0, primeira)
        assertEquals("segunda", desfazer.pendente.value?.mensagem)
    }

    @Test
    fun `oferta antiga expira - reabrir o app horas depois nao mostra a barra`() {
        var agora = 1_000L
        val desfazer = Desfazer(relogio = { agora })
        desfazer.oferecer("x") { }
        val pendente = checkNotNull(desfazer.pendente.value)

        agora += Desfazer.VALIDADE_MS
        assertFalse(desfazer.expirou(pendente))
        agora += 1
        assertTrue(desfazer.expirou(pendente))
    }

    @Test
    fun `se a restauracao falhar, avisa e tira a oferta da tela`() = runTest {
        val desfazer = Desfazer()
        desfazer.oferecer("x") { throw IllegalStateException("banco indisponível") }

        val restaurou = desfazer.desfazer(checkNotNull(desfazer.pendente.value))

        assertFalse(restaurou)
        assertNull(desfazer.pendente.value)
    }

    @Test
    fun `cancelamento durante a restauracao e repassado, nao vira falha`() = runTest {
        val desfazer = Desfazer()
        desfazer.oferecer("x") { throw CancellationException("cancelado") }
        val pendente = checkNotNull(desfazer.pendente.value)

        try {
            desfazer.desfazer(pendente)
            fail("O cancelamento deveria chegar a quem chamou")
        } catch (esperado: CancellationException) {
            assertNull(desfazer.pendente.value)
        }
    }

    @Test
    fun `trocar de conta esquece o que dava para desfazer`() {
        val desfazer = Desfazer()
        desfazer.oferecer("x") { }
        desfazer.esquecer()
        assertNull(desfazer.pendente.value)
    }

    // SelecaoAtual é global: desliga a persistência e zera o estado antes de cada teste.
    @Before
    fun limpar() {
        SelecaoAtual.configurar(inicial = null) { }
        SelecaoAtual.limpar()
    }

    @Test
    fun `lembra a propriedade escolhida da ultima vez`() {
        val gravadas = mutableListOf<String?>()
        SelecaoAtual.configurar(inicial = "sitio-1") { gravadas += it }

        assertEquals("sitio-1", SelecaoAtual.propriedadeId.value)

        SelecaoAtual.selecionar("sitio-2")
        SelecaoAtual.selecionar("sitio-2") // Mesma seleção: não deve gravar de novo.
        SelecaoAtual.limparSeFor("sitio-2")

        assertEquals(listOf("sitio-2", null), gravadas)
        assertNull(SelecaoAtual.propriedadeId.value)
    }

    @Test
    fun `o mes e o mesmo em todas as abas e volta para o atual ao trocar de conta`() {
        SelecaoAtual.voltarMes()
        SelecaoAtual.voltarMes()
        SelecaoAtual.avancarMes()
        assertEquals(YearMonth.now().minusMonths(1), SelecaoAtual.mes.value)

        SelecaoAtual.selecionarMes(YearMonth.of(2025, 1))
        assertEquals(YearMonth.of(2025, 1), SelecaoAtual.mes.value)

        SelecaoAtual.limpar()
        assertEquals(YearMonth.now(), SelecaoAtual.mes.value)
    }

    @Test
    fun `na virada do mes com o app aberto a selecao vai para o mes novo`() {
        SelecaoAtual.limpar(agora = YearMonth.of(2026, 9))

        SelecaoAtual.acompanharMesCorrente(YearMonth.of(2026, 10))

        assertEquals(YearMonth.of(2026, 10), SelecaoAtual.mes.value)
    }

    @Test
    fun `mes escolhido a mao continua escolhido depois da virada`() {
        SelecaoAtual.limpar(agora = YearMonth.of(2026, 9))
        SelecaoAtual.selecionarMes(YearMonth.of(2026, 6))

        SelecaoAtual.acompanharMesCorrente(YearMonth.of(2026, 10))
        assertEquals(YearMonth.of(2026, 6), SelecaoAtual.mes.value)

        // De volta ao mês corrente, a seleção volta a acompanhar o calendário.
        SelecaoAtual.selecionarMes(YearMonth.of(2026, 10))
        SelecaoAtual.acompanharMesCorrente(YearMonth.of(2026, 11))
        assertEquals(YearMonth.of(2026, 11), SelecaoAtual.mes.value)
    }

    @Test
    fun `sem virada, ou com o relogio voltando, o mes nao muda`() {
        SelecaoAtual.limpar(agora = YearMonth.of(2026, 9))

        SelecaoAtual.acompanharMesCorrente(YearMonth.of(2026, 9))
        assertEquals(YearMonth.of(2026, 9), SelecaoAtual.mes.value)

        SelecaoAtual.acompanharMesCorrente(YearMonth.of(2026, 8))
        assertEquals(YearMonth.of(2026, 9), SelecaoAtual.mes.value)
    }
}
