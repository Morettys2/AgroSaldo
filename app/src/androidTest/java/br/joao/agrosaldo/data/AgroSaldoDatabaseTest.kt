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
import br.joao.agrosaldo.data.local.entidade.PrevisaoEntity
import br.joao.agrosaldo.data.local.entidade.UsuarioEntity
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryRoom
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import java.time.LocalDate
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

/**
 * DAOs e restrições do SQLite (chaves estrangeiras, CASCADE, índice único) sobre o Room real.
 *
 * Banco em memória, recriado a cada teste; não toca no banco do app.
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
        id = id,
        nome = "Conta $id",
        email = email,
        senhaHash = "hash",
        consentimentoEm = 1L,
        versaoTermo = 1,
        criadoEm = 1L
    )

    /** Cria a conta "u1" com as duas propriedades e os nove lançamentos de exemplo. */
    private suspend fun semearExemplos() {
        usuarioDao.inserir(usuario("u1", "fabio@exemplo.com"))
        DadosDeExemplo.propriedades().forEach { propriedadeDao.salvar(it.paraEntidade(usuarioId = "u1", agora = 0L)) }
        DadosDeExemplo.lancamentos(hoje).forEach { lancamentoDao.salvar(it.paraEntidade(agora = 0L)) }
    }

    /** Conta as linhas direto no SQLite, sem passar pelos filtros dos DAOs. */
    private fun contarLinhas(tabela: String): Int = banco.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tabela").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    @Test
    fun salvarEBuscarPropriedade() = runBlocking {
        usuarioDao.inserir(usuario("u1", "fabio@exemplo.com"))
        val fazenda = DadosDeExemplo.propriedades().first().paraEntidade(usuarioId = "u1", agora = 1L)
        propriedadeDao.salvar(fazenda)

        assertEquals(fazenda, propriedadeDao.buscarPorId(fazenda.id, "u1"))
        assertEquals(1, propriedadeDao.contarDoUsuario("u1"))
    }

    /** A ordem vem do repositório (Collator pt-BR); o COLLATE NOCASE do SQLite mandaria "Água" para o fim. */
    @Test
    fun listaDePropriedadesVemEmOrdemAlfabeticaDoPortugues() = runBlocking {
        semearExemplos()
        val agua = DadosDeExemplo.propriedades().first().copy(id = "agua-limpa", nome = "Água Limpa")
        propriedadeDao.salvar(agua.paraEntidade(usuarioId = "u1", agora = 0L))
        val repositorio = PropriedadeRepositoryRoom(propriedadeDao, lancamentoDao, SessaoMemoria("u1"))

        val nomes = repositorio.observarTodas().first().map { it.nome }
        assertEquals(listOf("Água Limpa", "Fazenda Santa Clara", "Sítio Boa Vista"), nomes)
    }

    /**
     * Com `@Insert(onConflict = REPLACE)` o SQLite apagaria a linha antiga antes de inserir a nova,
     * e o CASCADE levaria os lançamentos; salvar precisa ser upsert.
     */
    @Test
    fun editarPropriedadeNaoApagaOsLancamentos() = runBlocking {
        semearExemplos()
        val antes = lancamentoDao.observarDoUsuario("u1").first().count { it.propriedadeId == DadosDeExemplo.ID_FAZENDA }

        val fazenda = checkNotNull(propriedadeDao.buscarPorId(DadosDeExemplo.ID_FAZENDA, "u1"))
        propriedadeDao.salvar(fazenda.copy(nome = "Fazenda Santa Clara (editada)"))

        val depois = lancamentoDao.observarDoUsuario("u1").first().count { it.propriedadeId == DadosDeExemplo.ID_FAZENDA }
        assertEquals(antes, depois)
        assertEquals("Fazenda Santa Clara (editada)", propriedadeDao.buscarPorId(DadosDeExemplo.ID_FAZENDA, "u1")?.nome)
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
            propriedadeId = "nao-existe",
            tipo = TipoLancamento.DESPESA,
            categoria = CategoriaLancamento.ADUBO,
            valorCentavos = 100L,
            data = hoje
        )
        try {
            lancamentoDao.salvar(orfao.paraEntidade(agora = 0L))
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
    fun excluirLancamento() = runBlocking {
        semearExemplos()
        lancamentoDao.excluir("exemplo-3", "u1")
        assertNull(lancamentoDao.buscarPorId("exemplo-3", "u1"))
    }

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

        // Exclusões pela conta errada devem ser ignoradas.
        propriedadeDao.excluir(DadosDeExemplo.ID_FAZENDA, "u2")
        lancamentoDao.excluir("exemplo-1", "u2")
        assertEquals(2, propriedadeDao.contarDoUsuario("u1"))
        assertEquals(9, lancamentoDao.observarDoUsuario("u1").first().size)
    }

    /** Checagem de posse feita pelos repositórios antes do upsert, que casa só pelo id. */
    @Test
    fun idDeOutraContaOuSemDonoNaoContaComoDoUsuario() = runBlocking {
        semearExemplos()
        usuarioDao.inserir(usuario("u2", "outra@exemplo.com"))
        val semDono = DadosDeExemplo.propriedades().first().copy(id = "sem-dono").paraEntidade(usuarioId = null, agora = 0L)
        propriedadeDao.salvar(semDono)

        assertFalse(propriedadeDao.existeSemSerDoUsuario(DadosDeExemplo.ID_FAZENDA, "u1"))
        assertTrue(propriedadeDao.existeSemSerDoUsuario(DadosDeExemplo.ID_FAZENDA, "u2"))
        assertTrue("sem dono não é de ninguém", propriedadeDao.existeSemSerDoUsuario("sem-dono", "u1"))
        assertFalse("id novo pode ser gravado", propriedadeDao.existeSemSerDoUsuario("nao-existe", "u1"))

        assertFalse(lancamentoDao.existeSemSerDoUsuario("exemplo-1", "u1"))
        assertTrue(lancamentoDao.existeSemSerDoUsuario("exemplo-1", "u2"))
        assertFalse(lancamentoDao.existeSemSerDoUsuario("nao-existe", "u2"))
    }

    /** Direito de exclusão da LGPD: a conta leva tudo junto, em dois níveis de CASCADE. */
    @Test
    fun excluirContaApagaPropriedadesELancamentos() = runBlocking {
        semearExemplos()
        usuarioDao.inserir(usuario("u2", "outra@exemplo.com"))

        usuarioDao.excluir("u1")

        assertNull(usuarioDao.buscarPorId("u1"))
        assertEquals(0, propriedadeDao.contarDoUsuario("u1"))
        assertEquals(0, contarLinhas("lancamentos"))
        assertEquals("outra@exemplo.com", usuarioDao.buscarPorId("u2")?.email)
    }

    @Test
    fun previsaoGuardadaSomeJuntoComAPropriedade() = runBlocking {
        semearExemplos()
        val previsaoDao = banco.previsaoDao()
        previsaoDao.salvar(PrevisaoEntity(DadosDeExemplo.ID_SITIO, -21.8, -46.8, "{}", 1L))
        previsaoDao.salvar(PrevisaoEntity(DadosDeExemplo.ID_SITIO, -21.8, -46.8, "{\"nova\":1}", 2L))

        // Upsert: a segunda gravação substitui a primeira, sem duplicar a linha.
        assertEquals(2L, previsaoDao.buscar(DadosDeExemplo.ID_SITIO)?.obtidaEm)
        assertEquals(1, contarLinhas("previsoes"))

        propriedadeDao.excluir(DadosDeExemplo.ID_SITIO, "u1")
        assertNull(previsaoDao.buscar(DadosDeExemplo.ID_SITIO))
        assertEquals(0, contarLinhas("previsoes"))
    }

    /** Contrato usado por ClimaRepositoryRoom ao descartar a previsão de uma propriedade excluída. */
    @Test
    fun previsaoDePropriedadeExcluidaERecusadaPelaChaveEstrangeira() = runBlocking {
        semearExemplos()
        val previsaoDao = banco.previsaoDao()
        assertTrue(previsaoDao.propriedadeExiste(DadosDeExemplo.ID_SITIO))

        propriedadeDao.excluir(DadosDeExemplo.ID_SITIO, "u1")

        assertFalse(previsaoDao.propriedadeExiste(DadosDeExemplo.ID_SITIO))
        try {
            previsaoDao.salvar(PrevisaoEntity(DadosDeExemplo.ID_SITIO, -21.8, -46.8, "{}", 1L))
            fail("A chave estrangeira deveria ter recusado a previsão")
        } catch (esperado: SQLiteConstraintException) {
            // RuntimeException: é o que ClimaRepositoryRoom.guardar trata.
        }
    }
}
