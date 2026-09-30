package br.joao.agrosaldo.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.joao.agrosaldo.data.local.AgroSaldoDatabase
import br.joao.agrosaldo.data.local.Semeador
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Testa o banco Room DE VERDADE, rodando no emulador.
 *
 * Usa um banco em memória: nasce vazio a cada teste e some no fim, sem
 * encostar no banco real do app. É o jeito de verificar que o SQL, as chaves
 * estrangeiras e o CASCADE se comportam como o esperado.
 *
 * Para rodar: clique com o botão direito nesta classe > Run 'AgroSaldoDatabaseTest'
 * (com o emulador ligado).
 */
@RunWith(AndroidJUnit4::class)
class AgroSaldoDatabaseTest {

    private lateinit var banco: AgroSaldoDatabase
    private lateinit var propriedadeDao: PropriedadeDao
    private lateinit var lancamentoDao: LancamentoDao

    private val hoje = LocalDate.of(2026, 9, 30)

    @Before
    fun abrir() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        banco = Room.inMemoryDatabaseBuilder(context, AgroSaldoDatabase::class.java).build()
        propriedadeDao = banco.propriedadeDao()
        lancamentoDao = banco.lancamentoDao()
    }

    @After
    fun fechar() {
        banco.close()
    }

    private suspend fun semearExemplos() {
        DadosDeExemplo.propriedades().forEach { propriedadeDao.salvar(it.paraEntidade()) }
        DadosDeExemplo.lancamentos(hoje).forEach { lancamentoDao.salvar(it.paraEntidade()) }
    }

    @Test
    fun salvarEBuscarPropriedade() = runBlocking {
        val fazenda = DadosDeExemplo.propriedades().first().paraEntidade(agora = 1L)
        propriedadeDao.salvar(fazenda)

        assertEquals(fazenda, propriedadeDao.buscarPorId(fazenda.id))
        assertEquals(1, propriedadeDao.contar())
    }

    @Test
    fun listaDePropriedadesVemEmOrdemAlfabetica() = runBlocking {
        semearExemplos()
        val nomes = propriedadeDao.observarTodas().first().map { it.nome }
        assertEquals(listOf("Fazenda São João da Serra", "Sítio Boa Vista"), nomes)
    }

    /**
     * O teste mais importante deste arquivo.
     *
     * Editar o nome de uma propriedade NÃO pode apagar os lançamentos dela.
     * Se o DAO usasse @Insert(onConflict = REPLACE), o SQLite apagaria a linha
     * antiga para inserir a nova, e o CASCADE levaria os lançamentos junto.
     */
    @Test
    fun editarPropriedadeNaoApagaOsLancamentos() = runBlocking {
        semearExemplos()
        val antes = lancamentoDao.observarTodos().first().count { it.propriedadeId == DadosDeExemplo.ID_FAZENDA }

        val fazenda = checkNotNull(propriedadeDao.buscarPorId(DadosDeExemplo.ID_FAZENDA))
        propriedadeDao.salvar(fazenda.copy(nome = "Fazenda São João (editada)"))

        val depois = lancamentoDao.observarTodos().first().count { it.propriedadeId == DadosDeExemplo.ID_FAZENDA }
        assertEquals(antes, depois)
        assertEquals("Fazenda São João (editada)", propriedadeDao.buscarPorId(DadosDeExemplo.ID_FAZENDA)?.nome)
    }

    @Test
    fun excluirPropriedadeApagaOsLancamentosPeloCascade() = runBlocking {
        semearExemplos()

        // Só a propriedade é excluída. Os lançamentos somem pela FOREIGN KEY.
        propriedadeDao.excluir(DadosDeExemplo.ID_SITIO)

        val restantes = lancamentoDao.observarTodos().first()
        assertTrue(restantes.none { it.propriedadeId == DadosDeExemplo.ID_SITIO })
        assertEquals(6, restantes.size)
    }

    @Test
    fun bancoRecusaLancamentoDePropriedadeInexistente() = runBlocking {
        val orfao = Lancamento(
            propriedadeId = "nao-existe", tipo = TipoLancamento.DESPESA,
            categoria = CategoriaLancamento.ADUBO, valorCentavos = 100L, data = hoje
        )
        try {
            lancamentoDao.salvar(orfao.paraEntidade())
            fail("A chave estrangeira deveria ter recusado o lançamento")
        } catch (esperado: SQLiteConstraintException) {
            // RN01 garantida pelo próprio banco.
        }
    }

    @Test
    fun upsertAtualizaEmVezDeDuplicar() = runBlocking {
        semearExemplos()
        val energia = checkNotNull(lancamentoDao.buscarPorId("exemplo-2"))
        lancamentoDao.salvar(energia.copy(valorCentavos = 130_000L))

        assertEquals(9, lancamentoDao.observarTodos().first().size)
        assertEquals(130_000L, lancamentoDao.buscarPorId("exemplo-2")?.valorCentavos)
    }

    @Test
    fun lancamentosVemDoMaisRecenteParaOMaisAntigo() = runBlocking {
        semearExemplos()
        val datas = lancamentoDao.observarTodos().first().map { it.data }
        assertEquals(datas.sortedDescending(), datas)
    }

    @Test
    fun saldoDoPeriodoCalculadoPeloSqlite() = runBlocking {
        semearExemplos()
        val inicio = LocalDate.of(2026, 9, 1).toEpochDay()
        val fim = LocalDate.of(2026, 9, 30).toEpochDay()

        // 18.000 - 1.250 - 780 - 320 = 15.650
        assertEquals(1_565_000L, lancamentoDao.saldoDoPeriodo(DadosDeExemplo.ID_FAZENDA, inicio, fim))
        assertEquals(0L, lancamentoDao.saldoDoPeriodo("nao-existe", inicio, fim))
    }

    @Test
    fun semeadorGeraSqlValidoParaOBancoReal() {
        val sqlite = banco.openHelper.writableDatabase
        Semeador.semear(executar = { sql, args -> sqlite.execSQL(sql, args) }, hoje = hoje)

        runBlocking {
            assertEquals(2, propriedadeDao.contar())
            assertEquals(9, lancamentoDao.observarTodos().first().size)
        }
    }

    @Test
    fun excluirLancamento() = runBlocking {
        semearExemplos()
        lancamentoDao.excluir("exemplo-3")
        assertNull(lancamentoDao.buscarPorId("exemplo-3"))
    }
}
