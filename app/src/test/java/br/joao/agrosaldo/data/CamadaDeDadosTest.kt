package br.joao.agrosaldo.data

import br.joao.agrosaldo.data.local.Semeador
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.entidade.LancamentoEntity
import br.joao.agrosaldo.data.local.entidade.PropriedadeEntity
import br.joao.agrosaldo.data.local.paraDominio
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.data.repository.LancamentoRepositoryRoom
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryRoom
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Testes da camada de dados que rodam sem Android: mapeadores, semeador e
 * os repositórios Room ligados a um DAO falso.
 *
 * O SQL de verdade é testado em app/src/androidTest, com um banco Room em
 * memória rodando no emulador.
 */
class CamadaDeDadosTest {

    // ---------- mapeadores ----------

    @Test
    fun `propriedade vai para o banco e volta igual`() {
        val original = DadosDeExemplo.propriedades().first()
        val entidade = original.paraEntidade(agora = 123L)

        assertEquals(123L, entidade.atualizadoEm)
        assertEquals(original, entidade.paraDominio())
    }

    @Test
    fun `lancamento guarda enum como texto e data como epochDay`() {
        val original = Lancamento(
            id = "l1", propriedadeId = "p1",
            tipo = TipoLancamento.DESPESA, categoria = CategoriaLancamento.MAO_DE_OBRA,
            valorCentavos = 210_000L, data = LocalDate.of(2026, 9, 22), descricao = "Diarista"
        )
        val entidade = original.paraEntidade(agora = 0L)

        assertEquals("DESPESA", entidade.tipo)
        assertEquals("MAO_DE_OBRA", entidade.categoria)
        assertEquals(LocalDate.of(2026, 9, 22).toEpochDay(), entidade.data)
        assertEquals(original, entidade.paraDominio())
    }

    @Test
    fun `todas as categorias sobrevivem a ida e volta`() {
        CategoriaLancamento.entries.forEach { categoria ->
            val l = Lancamento(
                propriedadeId = "p1", tipo = categoria.tipo, categoria = categoria,
                valorCentavos = 1L, data = LocalDate.of(2026, 1, 1)
            )
            assertEquals(categoria, l.paraEntidade().paraDominio().categoria)
        }
    }

    // ---------- semeador ----------

    private data class Comando(val sql: String, val argumentos: List<Any?>)

    private fun comandosDoSemeador(hoje: LocalDate): List<Comando> {
        val comandos = mutableListOf<Comando>()
        Semeador.semear(
            executar = { sql, argumentos -> comandos += Comando(sql, argumentos.toList()) },
            hoje = hoje,
            agora = 0L
        )
        return comandos
    }

    @Test
    fun `semeador insere propriedades antes dos lancamentos`() {
        val comandos = comandosDoSemeador(LocalDate.of(2026, 9, 30))
        val primeiroLancamento = comandos.indexOfFirst { it.sql.startsWith("INSERT INTO lancamentos") }
        val ultimaPropriedade = comandos.indexOfLast { it.sql.startsWith("INSERT INTO propriedades") }

        assertEquals(2 + 9, comandos.size)
        assertTrue(ultimaPropriedade < primeiroLancamento)
    }

    @Test
    fun `cada INSERT do semeador tem um argumento por interrogacao`() {
        comandosDoSemeador(LocalDate.of(2026, 9, 30)).forEach { comando ->
            assertEquals(comando.sql, comando.sql.count { it == '?' }, comando.argumentos.size)
        }
    }

    @Test
    fun `no dia 1 do mes nenhum exemplo fica no futuro`() {
        val hoje = LocalDate.of(2026, 10, 1)
        DadosDeExemplo.lancamentos(hoje).forEach { l ->
            assertFalse("${l.id} em ${l.data}", l.data.isAfter(hoje))
        }
    }

    // ---------- repositórios Room com DAO falso ----------

    /** DAO de mentira: guarda as entidades numa lista, como o Room guardaria. */
    private class PropriedadeDaoFalso : PropriedadeDao {
        val tabela = MutableStateFlow<List<PropriedadeEntity>>(emptyList())
        override fun observarTodas(): Flow<List<PropriedadeEntity>> = tabela
        override suspend fun buscarPorId(id: String) = tabela.value.firstOrNull { it.id == id }
        override suspend fun salvar(propriedade: PropriedadeEntity) = tabela.update { lista ->
            lista.filterNot { it.id == propriedade.id } + propriedade
        }
        override suspend fun excluir(id: String) = tabela.update { it.filterNot { p -> p.id == id } }
        override suspend fun contar() = tabela.value.size
    }

    private class LancamentoDaoFalso : LancamentoDao {
        val tabela = MutableStateFlow<List<LancamentoEntity>>(emptyList())
        override fun observarTodos(): Flow<List<LancamentoEntity>> = tabela
        override suspend fun buscarPorId(id: String) = tabela.value.firstOrNull { it.id == id }
        override suspend fun salvar(lancamento: LancamentoEntity) = tabela.update { lista ->
            lista.filterNot { it.id == lancamento.id } + lancamento
        }
        override suspend fun excluir(id: String) = tabela.update { it.filterNot { l -> l.id == id } }
        override suspend fun excluirDaPropriedade(propriedadeId: String) =
            tabela.update { it.filterNot { l -> l.propriedadeId == propriedadeId } }
        override suspend fun saldoDoPeriodo(propriedadeId: String, inicioEpochDay: Long, fimEpochDay: Long) = 0L
    }

    @Test
    fun `repositorio Room entrega objetos de dominio a partir das entidades`() = runTest {
        val dao = PropriedadeDaoFalso()
        val repositorio = PropriedadeRepositoryRoom(dao)
        val fazenda = DadosDeExemplo.propriedades().first()

        repositorio.salvar(fazenda)

        assertEquals(1, dao.tabela.value.size)
        assertEquals(listOf(fazenda), repositorio.observarTodas().first())
        assertEquals(fazenda, repositorio.buscarPorId(fazenda.id))

        repositorio.excluir(fazenda.id)
        assertNull(repositorio.buscarPorId(fazenda.id))
    }

    @Test
    fun `repositorio Room de lancamentos exclui por propriedade`() = runTest {
        val dao = LancamentoDaoFalso()
        val repositorio = LancamentoRepositoryRoom(dao)
        DadosDeExemplo.lancamentos(LocalDate.of(2026, 9, 30)).forEach { repositorio.salvar(it) }

        repositorio.excluirDaPropriedade(DadosDeExemplo.ID_SITIO)

        val restantes = repositorio.observarTodos().first()
        assertEquals(6, restantes.size)
        assertTrue(restantes.all { it.propriedadeId == DadosDeExemplo.ID_FAZENDA })
    }
}
