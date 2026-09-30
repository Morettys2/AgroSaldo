package br.joao.agrosaldo.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.joao.agrosaldo.data.local.AgroSaldoDatabase
import br.joao.agrosaldo.data.local.Semeador
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.dao.UsuarioDao
import br.joao.agrosaldo.data.local.entidade.UsuarioEntity
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private lateinit var usuarioDao: UsuarioDao
    private lateinit var propriedadeDao: PropriedadeDao
    private lateinit var lancamentoDao: LancamentoDao

    private val hoje = LocalDate.of(2026, 9, 30)

    @Before
    fun abrir() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        banco = Room.inMemoryDatabaseBuilder(context, AgroSaldoDatabase::class.java).build()
        usuarioDao = banco.usuarioDao()
        propriedadeDao = banco.propriedadeDao()
        lancamentoDao = banco.lancamentoDao()
    }

    @After
    fun fechar() {
        banco.close()
    }

    private fun usuario(id: String, email: String) = UsuarioEntity(
        id = id, nome = "Conta $id", email = email, senhaHash = "hash",
        consentimentoEm = 1L, versaoTermo = 1, criadoEm = 1L
    )

    /** Cria a conta "u1" com as duas propriedades e os nove lançamentos de exemplo. */
    private suspend fun semearExemplos() {
        usuarioDao.inserir(usuario("u1", "fabio@exemplo.com"))
        DadosDeExemplo.propriedades().forEach { propriedadeDao.salvar(it.paraEntidade(usuarioId = "u1")) }
        DadosDeExemplo.lancamentos(hoje).forEach { lancamentoDao.salvar(it.paraEntidade()) }
    }

    // ---------- propriedades e lançamentos (fase 3, agora com dono) ----------

    @Test
    fun salvarEBuscarPropriedade() = runBlocking {
        usuarioDao.inserir(usuario("u1", "fabio@exemplo.com"))
        val fazenda = DadosDeExemplo.propriedades().first().paraEntidade(usuarioId = "u1", agora = 1L)
        propriedadeDao.salvar(fazenda)

        assertEquals(fazenda, propriedadeDao.buscarPorId(fazenda.id, "u1"))
        assertEquals(1, propriedadeDao.contarDoUsuario("u1"))
    }

    @Test
    fun listaDePropriedadesVemEmOrdemAlfabetica() = runBlocking {
        semearExemplos()
        val nomes = propriedadeDao.observarDoUsuario("u1").first().map { it.nome }
        assertEquals(listOf("Fazenda São João da Serra", "Sítio Boa Vista"), nomes)
    }

    /**
     * Editar o nome de uma propriedade NÃO pode apagar os lançamentos dela.
     * Se o DAO usasse @Insert(onConflict = REPLACE), o SQLite apagaria a linha
     * antiga para inserir a nova, e o CASCADE levaria os lançamentos junto.
     */
    @Test
    fun editarPropriedadeNaoApagaOsLancamentos() = runBlocking {
        semearExemplos()
        val antes = lancamentoDao.observarDoUsuario("u1").first().count { it.propriedadeId == DadosDeExemplo.ID_FAZENDA }

        val fazenda = checkNotNull(propriedadeDao.buscarPorId(DadosDeExemplo.ID_FAZENDA, "u1"))
        propriedadeDao.salvar(fazenda.copy(nome = "Fazenda São João (editada)"))

        val depois = lancamentoDao.observarDoUsuario("u1").first().count { it.propriedadeId == DadosDeExemplo.ID_FAZENDA }
        assertEquals(antes, depois)
        assertEquals("Fazenda São João (editada)", propriedadeDao.buscarPorId(DadosDeExemplo.ID_FAZENDA, "u1")?.nome)
    }

    @Test
    fun excluirPropriedadeApagaOsLancamentosPeloCascade() = runBlocking {
        semearExemplos()

        propriedadeDao.excluir(DadosDeExemplo.ID_SITIO, "u1")

        val restantes = lancamentoDao.observarDoUsuario("u1").first()
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
        val energia = checkNotNull(lancamentoDao.buscarPorId("exemplo-2", "u1"))
        lancamentoDao.salvar(energia.copy(valorCentavos = 130_000L))

        assertEquals(9, lancamentoDao.observarDoUsuario("u1").first().size)
        assertEquals(130_000L, lancamentoDao.buscarPorId("exemplo-2", "u1")?.valorCentavos)
    }

    @Test
    fun lancamentosVemDoMaisRecenteParaOMaisAntigo() = runBlocking {
        semearExemplos()
        val datas = lancamentoDao.observarDoUsuario("u1").first().map { it.data }
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
    fun excluirLancamento() = runBlocking {
        semearExemplos()
        lancamentoDao.excluir("exemplo-3", "u1")
        assertNull(lancamentoDao.buscarPorId("exemplo-3", "u1"))
    }

    // ---------- contas (login e LGPD) ----------

    @Test
    fun semeadorGeraSqlValidoEAPrimeiraContaAdotaOsExemplos() {
        val sqlite = banco.openHelper.writableDatabase
        Semeador.semear(executar = { sql, args -> sqlite.execSQL(sql, args) }, hoje = hoje)

        runBlocking {
            usuarioDao.inserir(usuario("u1", "fabio@exemplo.com"))
            assertEquals(2, usuarioDao.adotarPropriedadesSemDono("u1"))
            assertEquals(2, propriedadeDao.contarDoUsuario("u1"))
            assertEquals(9, lancamentoDao.observarDoUsuario("u1").first().size)
        }
    }

    @Test
    fun emailRepetidoNaoEntraEOIgnoreDevolveMenosUm() = runBlocking {
        assertTrue(usuarioDao.inserir(usuario("u1", "fabio@exemplo.com")) != -1L)
        assertEquals(-1L, usuarioDao.inserir(usuario("u2", "fabio@exemplo.com")))
        assertNull(usuarioDao.buscarPorId("u2"))
    }

    @Test
    fun cadaContaSoEnxergaOsPropriosDados() = runBlocking {
        semearExemplos()
        usuarioDao.inserir(usuario("u2", "outra@exemplo.com"))

        assertEquals(0, propriedadeDao.observarDoUsuario("u2").first().size)
        assertEquals(0, lancamentoDao.observarDoUsuario("u2").first().size)
        assertNull(propriedadeDao.buscarPorId(DadosDeExemplo.ID_FAZENDA, "u2"))
        assertNull(lancamentoDao.buscarPorId("exemplo-1", "u2"))
        assertFalse(lancamentoDao.propriedadeEhDoUsuario(DadosDeExemplo.ID_FAZENDA, "u2"))

        // Tentativas de apagar pela conta errada não fazem nada.
        propriedadeDao.excluir(DadosDeExemplo.ID_FAZENDA, "u2")
        lancamentoDao.excluir("exemplo-1", "u2")
        assertEquals(2, propriedadeDao.contarDoUsuario("u1"))
        assertEquals(9, lancamentoDao.observarDoUsuario("u1").first().size)
    }

    /** Direito de exclusão da LGPD: a conta leva tudo junto, em dois níveis de CASCADE. */
    @Test
    fun excluirContaApagaPropriedadesELancamentos() = runBlocking {
        semearExemplos()
        usuarioDao.inserir(usuario("u2", "outra@exemplo.com"))

        usuarioDao.excluir("u1")

        assertNull(usuarioDao.buscarPorId("u1"))
        assertEquals(0, propriedadeDao.contarDoUsuario("u1"))
        val sqlite = banco.openHelper.readableDatabase
        sqlite.query("SELECT COUNT(*) FROM lancamentos").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        // A outra conta não foi tocada.
        assertEquals("outra@exemplo.com", usuarioDao.buscarPorId("u2")?.email)
    }
}
