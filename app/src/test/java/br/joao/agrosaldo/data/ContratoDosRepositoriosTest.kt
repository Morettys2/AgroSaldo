package br.joao.agrosaldo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.joao.agrosaldo.data.local.AgroSaldoDatabase
import br.joao.agrosaldo.data.local.TransacaoRoom
import br.joao.agrosaldo.data.local.entidade.UsuarioEntity
import br.joao.agrosaldo.data.repository.LancamentoRepositoryMemoria
import br.joao.agrosaldo.data.repository.LancamentoRepositoryRoom
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryRoom
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.emOrdemAlfabetica
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Os mesmos cenários contra o Room de verdade (em memória) e contra os fakes do testFixtures
 * (FAKE-01): sessão, posse e ordem se comportam igual nos dois. Senão, um teste de ViewModel com
 * fake passaria por um motivo que não vale no app.
 */
abstract class ContratoDosRepositorios {

    protected val sessao = SessaoMemoria()
    protected abstract val propriedades: PropriedadeRepository
    protected abstract val lancamentos: LancamentoRepository

    /** Faz a próxima gravação contar como mais recente (no Room, avança o relógio do `atualizado_em`). */
    protected open fun avancarRelogio() = Unit

    @Test
    fun `sem sessao a lista vem vazia e a busca nao acha nada`() = runTest {
        sessao.iniciar(ANA)
        propriedades.salvar(propriedade("p1"))
        lancamentos.salvar(lancamento("l1", "p1"))

        sessao.encerrar()

        assertEquals(emptyList<Propriedade>(), propriedades.observarTodas().first())
        assertNull(propriedades.buscarPorId("p1"))
        assertEquals(emptyList<Lancamento>(), lancamentos.observarTodos().first())
        assertNull(lancamentos.buscarPorId("l1"))
    }

    @Test
    fun `sem sessao gravar e excluir falham`() = runTest {
        deveFalhar<IllegalStateException> { propriedades.salvar(propriedade("p1")) }
        deveFalhar<IllegalStateException> { propriedades.excluirComLancamentos("p1") }
        deveFalhar<IllegalStateException> { lancamentos.salvar(lancamento("l1", "p1")) }
        deveFalhar<IllegalStateException> { lancamentos.excluir("l1") }
    }

    @Test
    fun `cada conta ve so as suas propriedades e lancamentos`() = runTest {
        sessao.iniciar(ANA)
        propriedades.salvar(propriedade("p1"))
        lancamentos.salvar(lancamento("l1", "p1"))
        sessao.iniciar(BIA)
        propriedades.salvar(propriedade("p2"))
        lancamentos.salvar(lancamento("l2", "p2"))

        assertEquals(listOf("p2"), propriedades.observarTodas().first().map { it.id })
        assertEquals(listOf("l2"), lancamentos.observarTodos().first().map { it.id })
        assertNull(propriedades.buscarPorId("p1"))
        assertNull(lancamentos.buscarPorId("l1"))
    }

    @Test
    fun `gravar por cima da propriedade de outra conta falha e nao muda nada`() = runTest {
        sessao.iniciar(ANA)
        propriedades.salvar(propriedade("p1"))
        sessao.iniciar(BIA)

        deveFalhar<IllegalArgumentException> { propriedades.salvar(propriedade("p1").copy(nome = "Tomada")) }

        sessao.iniciar(ANA)
        assertEquals("Propriedade p1", propriedades.buscarPorId("p1")?.nome)
    }

    @Test
    fun `lancamento em propriedade de outra conta ou com id de outra conta falha`() = runTest {
        sessao.iniciar(ANA)
        propriedades.salvar(propriedade("p1"))
        lancamentos.salvar(lancamento("l1", "p1"))
        sessao.iniciar(BIA)
        propriedades.salvar(propriedade("p2"))

        deveFalhar<IllegalArgumentException> { lancamentos.salvar(lancamento("l2", "p1")) }
        deveFalhar<IllegalArgumentException> { lancamentos.salvar(lancamento("l1", "p2")) }

        sessao.iniciar(ANA)
        assertEquals("p1", lancamentos.buscarPorId("l1")?.propriedadeId)
    }

    @Test
    fun `excluir o que e de outra conta nao apaga nada`() = runTest {
        sessao.iniciar(ANA)
        propriedades.salvar(propriedade("p1"))
        lancamentos.salvar(lancamento("l1", "p1"))
        sessao.iniciar(BIA)

        assertNull(lancamentos.excluir("l1"))
        assertNull(propriedades.excluirComLancamentos("p1"))

        sessao.iniciar(ANA)
        assertNotNull(propriedades.buscarPorId("p1"))
        assertNotNull(lancamentos.buscarPorId("l1"))
    }

    @Test
    fun `propriedades vem em ordem alfabetica do portugues`() = runTest {
        sessao.iniciar(ANA)
        val salvas = listOf("Sítio Exemplo", "Água Exemplo", "chácara Três Irmãos", "Fazenda Modelo")
            .mapIndexed { i, nome -> propriedade("p$i").copy(nome = nome) }
        salvas.forEach { propriedades.salvar(it) }

        assertEquals(salvas.emOrdemAlfabetica(), propriedades.observarTodas().first())
    }

    @Test
    fun `lancamentos vem mais recentes primeiro, por data e depois pela ultima gravacao`() = runTest {
        sessao.iniciar(ANA)
        propriedades.salvar(propriedade("p1"))
        lancamentos.salvar(lancamento("antigo", "p1", DIA))
        avancarRelogio()
        lancamentos.salvar(lancamento("depois", "p1", DIA.plusDays(2)))
        avancarRelogio()
        lancamentos.salvar(lancamento("mesmo-dia", "p1", DIA))

        assertEquals(listOf("depois", "mesmo-dia", "antigo"), lancamentos.observarTodos().first().map { it.id })
    }

    @Test
    fun `desfazer a exclusao devolve o lancamento ao mesmo lugar da lista`() = runTest {
        sessao.iniciar(ANA)
        propriedades.salvar(propriedade("p1"))
        listOf("l1", "l2", "l3").forEach {
            lancamentos.salvar(lancamento(it, "p1"))
            avancarRelogio()
        }
        val antes = lancamentos.observarTodos().first()

        lancamentos.restaurar(checkNotNull(lancamentos.excluir("l2")))

        assertEquals(antes, lancamentos.observarTodos().first())
    }

    private fun propriedade(id: String) = DadosDeExemplo.propriedades().first().copy(id = id, nome = "Propriedade $id")

    private fun lancamento(id: String, propriedadeId: String, data: LocalDate = DIA) =
        DadosDeExemplo.lancamentos(DIA).first().copy(id = id, propriedadeId = propriedadeId, data = data)

    private suspend inline fun <reified E : Throwable> deveFalhar(crossinline bloco: suspend () -> Unit) {
        val erro = runCatching { bloco() }.exceptionOrNull()
        assertTrue("esperava ${E::class.simpleName}, veio $erro", erro is E)
    }

    protected companion object {
        const val ANA = "conta-ana"
        const val BIA = "conta-bia"
        val DIA: LocalDate = LocalDate.of(2026, 10, 10)
    }
}

/** O contrato no Room de verdade, num banco em memória. */
@RunWith(RobolectricTestRunner::class)
class ContratoNoRoomTest : ContratoDosRepositorios() {

    private val banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AgroSaldoDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val relogio = RelogioMutavel(Instant.parse("2026-10-10T12:00:00Z"))
    override val propriedades =
        PropriedadeRepositoryRoom(banco.propriedadeDao(), banco.lancamentoDao(), sessao, TransacaoRoom(banco), relogio)
    override val lancamentos = LancamentoRepositoryRoom(banco.lancamentoDao(), sessao, TransacaoRoom(banco), relogio)

    override fun avancarRelogio() {
        relogio.instante = relogio.instante.plusSeconds(1)
    }

    @Before
    fun criarContas() = runBlocking {
        listOf(ANA, BIA).forEach { id ->
            val conta = UsuarioEntity(id, id, "$id@exemplo.com", "hash", consentimentoEm = 0L, versaoTermo = 1, criadoEm = 0L)
            banco.usuarioDao().inserir(conta)
        }
    }

    @After
    fun fechar() = banco.close()
}

/** O contrato nos fakes do testFixtures, ligados à mesma sessão. */
class ContratoNoFakeTest : ContratoDosRepositorios() {
    override val lancamentos = LancamentoRepositoryMemoria(emptyList(), sessao = sessao)
    override val propriedades = PropriedadeRepositoryMemoria(emptyList(), lancamentos, sessao = sessao)
}
