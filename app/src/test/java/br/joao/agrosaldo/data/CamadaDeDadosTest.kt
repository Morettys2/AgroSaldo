package br.joao.agrosaldo.data

import br.joao.agrosaldo.data.local.MigracoesSql
import br.joao.agrosaldo.data.local.Semeador
import br.joao.agrosaldo.data.local.Transacao
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.dao.UsuarioDao
import br.joao.agrosaldo.data.local.entidade.LancamentoEntity
import br.joao.agrosaldo.data.local.entidade.PropriedadeEntity
import br.joao.agrosaldo.data.local.entidade.UsuarioEntity
import br.joao.agrosaldo.data.local.paraDominio
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.data.repository.LancamentoRepositoryRoom
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryRoom
import br.joao.agrosaldo.data.repository.UsuarioRepositoryRoom
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.model.Usuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Camada de dados na JVM: mapeadores, semeador, SQL de migração e repositórios Room sobre
 * DAOs falsos. O SQL real é testado em `app/src/androidTest`.
 */
class CamadaDeDadosTest {

    @Test
    fun `propriedade vai para o banco e volta igual`() {
        val original = DadosDeExemplo.propriedades().first()
        val entidade = original.paraEntidade(usuarioId = "u1", agora = 123L)

        assertEquals(123L, entidade.atualizadoEm)
        assertEquals("u1", entidade.usuarioId)
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

    @Test
    fun `usuario vai para o banco e volta igual com instantes em milissegundos`() {
        val original = usuario("u1", "fabio@exemplo.com")
        val entidade = original.paraEntidade()

        assertEquals(original.consentimentoEm.toEpochMilli(), entidade.consentimentoEm)
        assertEquals(original, entidade.paraDominio())
    }

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
    fun `exemplos nascem sem dono para a primeira conta adotar`() {
        val propriedades = comandosDoSemeador(LocalDate.of(2026, 9, 30))
            .filter { it.sql.startsWith("INSERT INTO propriedades") }
        assertTrue(propriedades.none { "usuario_id" in it.sql })
    }

    @Test
    fun `no dia 1 do mes nenhum exemplo fica no futuro`() {
        val hoje = LocalDate.of(2026, 10, 1)
        DadosDeExemplo.lancamentos(hoje).forEach { l ->
            assertFalse("${l.id} em ${l.data}", l.data.isAfter(hoje))
        }
    }

    @Test
    fun `migracao 1 para 2 nunca apaga nem recria tabela`() {
        // Apagar "propriedades" dispararia o CASCADE e levaria os lançamentos.
        MigracoesSql.DE_1_PARA_2.forEach { sql ->
            assertFalse(sql, sql.contains("DROP", ignoreCase = true))
            assertFalse(sql, sql.contains("DELETE", ignoreCase = true) && !sql.contains("ON DELETE"))
        }
    }

    @Test
    fun `migracao cria a tabela de usuarios antes de apontar para ela`() {
        val criaUsuarios = MigracoesSql.DE_1_PARA_2.indexOfFirst { it.contains("CREATE TABLE IF NOT EXISTS `usuarios`") }
        val alteraPropriedades = MigracoesSql.DE_1_PARA_2.indexOfFirst { it.startsWith("ALTER TABLE `propriedades`") }
        assertTrue(criaUsuarios in 0 until alteraPropriedades)
    }

    /** DAO em memória que filtra por dono como as queries reais. */
    private class PropriedadeDaoFalso : PropriedadeDao {
        val tabela = MutableStateFlow<List<PropriedadeEntity>>(emptyList())
        override fun observarDoUsuario(usuarioId: String): Flow<List<PropriedadeEntity>> =
            tabela.map { lista -> lista.filter { it.usuarioId == usuarioId }.sortedBy { it.nome.lowercase() } }
        override suspend fun buscarPorId(id: String, usuarioId: String) =
            tabela.value.firstOrNull { it.id == id && it.usuarioId == usuarioId }
        override suspend fun donoDe(id: String) = tabela.value.firstOrNull { it.id == id }?.usuarioId
        override suspend fun salvar(propriedade: PropriedadeEntity) = tabela.update { lista ->
            lista.filterNot { it.id == propriedade.id } + propriedade
        }
        override suspend fun excluir(id: String, usuarioId: String) =
            tabela.update { it.filterNot { p -> p.id == id && p.usuarioId == usuarioId } }
        override suspend fun contarDoUsuario(usuarioId: String) = tabela.value.count { it.usuarioId == usuarioId }
    }

    /** Resolve o dono de cada lançamento pela propriedade, como o JOIN das queries reais. */
    private class LancamentoDaoFalso(private val propriedades: PropriedadeDaoFalso) : LancamentoDao {
        val tabela = MutableStateFlow<List<LancamentoEntity>>(emptyList())

        private fun doUsuario(lista: List<LancamentoEntity>, donos: List<PropriedadeEntity>, usuarioId: String) =
            lista.filter { l -> donos.any { it.id == l.propriedadeId && it.usuarioId == usuarioId } }

        override fun observarDoUsuario(usuarioId: String): Flow<List<LancamentoEntity>> =
            combine(tabela, propriedades.tabela) { lista, donos ->
                doUsuario(lista, donos, usuarioId).sortedByDescending { it.data }
            }
        override suspend fun buscarPorId(id: String, usuarioId: String) =
            doUsuario(tabela.value, propriedades.tabela.value, usuarioId).firstOrNull { it.id == id }
        override suspend fun propriedadeEhDoUsuario(propriedadeId: String, usuarioId: String) =
            propriedades.tabela.value.any { it.id == propriedadeId && it.usuarioId == usuarioId }
        override suspend fun salvar(lancamento: LancamentoEntity) = tabela.update { lista ->
            lista.filterNot { it.id == lancamento.id } + lancamento
        }
        override suspend fun salvarTodos(lancamentos: List<LancamentoEntity>) = lancamentos.forEach { salvar(it) }
        override suspend fun daPropriedade(propriedadeId: String, usuarioId: String) =
            doUsuario(tabela.value, propriedades.tabela.value, usuarioId).filter { it.propriedadeId == propriedadeId }
        override suspend fun excluir(id: String, usuarioId: String) {
            val alvo = buscarPorId(id, usuarioId) ?: return
            tabela.update { it - alvo }
        }
        override suspend fun excluirDaPropriedade(propriedadeId: String, usuarioId: String) {
            if (!propriedadeEhDoUsuario(propriedadeId, usuarioId)) return
            tabela.update { it.filterNot { l -> l.propriedadeId == propriedadeId } }
        }
        override suspend fun saldoDoPeriodo(propriedadeId: String, inicioEpochDay: Long, fimEpochDay: Long) = 0L
    }

    private class UsuarioDaoFalso(private val propriedades: PropriedadeDaoFalso) : UsuarioDao {
        val tabela = MutableStateFlow<List<UsuarioEntity>>(emptyList())
        override suspend fun inserir(usuario: UsuarioEntity): Long {
            // Imita o índice único + IGNORE: e-mail repetido não entra e devolve -1.
            if (tabela.value.any { it.email == usuario.email || it.id == usuario.id }) return -1L
            tabela.update { it + usuario }
            return tabela.value.size.toLong()
        }
        override fun observar(id: String) = tabela.map { lista -> lista.firstOrNull { it.id == id } }
        override suspend fun buscarPorId(id: String) = tabela.value.firstOrNull { it.id == id }
        override suspend fun buscarPorEmail(email: String) = tabela.value.firstOrNull { it.email == email }
        override suspend fun atualizarSenha(id: String, senhaHash: String) = tabela.update { lista ->
            lista.map { if (it.id == id) it.copy(senhaHash = senhaHash) else it }
        }
        override suspend fun registrarConsentimento(id: String, em: Long, versao: Int) = tabela.update { lista ->
            lista.map { if (it.id == id) it.copy(consentimentoEm = em, versaoTermo = versao) else it }
        }
        override suspend fun excluir(id: String) = tabela.update { it.filterNot { u -> u.id == id } }
        override suspend fun adotarPropriedadesSemDono(usuarioId: String): Int {
            val orfas = propriedades.tabela.value.count { it.usuarioId == null }
            propriedades.tabela.update { lista ->
                lista.map { if (it.usuarioId == null) it.copy(usuarioId = usuarioId) else it }
            }
            return orfas
        }
    }

    private fun usuario(id: String, email: String) = Usuario(
        id = id, nome = "Fábio Moretti", email = email, senhaHash = "hash-de-teste",
        consentimentoEm = Instant.ofEpochMilli(1_000L), versaoTermo = 1,
        criadoEm = Instant.ofEpochMilli(1_000L)
    )

    @Test
    fun `repositorio Room entrega objetos de dominio a partir das entidades`() = runTest {
        val dao = PropriedadeDaoFalso()
        val repositorio = PropriedadeRepositoryRoom(dao, LancamentoDaoFalso(dao), SessaoMemoria("u1"))
        val fazenda = DadosDeExemplo.propriedades().first()

        repositorio.salvar(fazenda)

        assertEquals("u1", dao.tabela.value.single().usuarioId)
        assertEquals(listOf(fazenda), repositorio.observarTodas().first())
        assertEquals(fazenda, repositorio.buscarPorId(fazenda.id))

        repositorio.excluir(fazenda.id)
        assertNull(repositorio.buscarPorId(fazenda.id))
    }

    @Test
    fun `cada conta so enxerga as proprias propriedades`() = runTest {
        val dao = PropriedadeDaoFalso()
        val sessao = SessaoMemoria("u1")
        val repositorio = PropriedadeRepositoryRoom(dao, LancamentoDaoFalso(dao), sessao)
        val fazenda = DadosDeExemplo.propriedades().first()
        repositorio.salvar(fazenda)

        sessao.iniciar("u2")
        assertEquals(emptyList<Any>(), repositorio.observarTodas().first())
        assertNull(repositorio.buscarPorId(fazenda.id))

        // Excluir pela conta errada não apaga nada.
        repositorio.excluir(fazenda.id)
        assertEquals(1, dao.tabela.value.size)

        sessao.iniciar("u1")
        assertEquals(listOf(fazenda), repositorio.observarTodas().first())
    }

    @Test
    fun `uma conta nao sobrescreve a propriedade da outra`() = runTest {
        val dao = PropriedadeDaoFalso()
        val sessao = SessaoMemoria("u1")
        val repositorio = PropriedadeRepositoryRoom(dao, LancamentoDaoFalso(dao), sessao)
        val fazenda = DadosDeExemplo.propriedades().first()
        repositorio.salvar(fazenda)

        sessao.iniciar("u2")
        try {
            repositorio.salvar(fazenda.copy(nome = "Tomada"))
            fail("Deveria recusar a gravação")
        } catch (esperado: IllegalArgumentException) {
            assertEquals("Fazenda São João da Serra", dao.tabela.value.single().nome)
        }
    }

    @Test
    fun `sem ninguem conectado a lista vem vazia e gravar e recusado`() = runTest {
        val dao = PropriedadeDaoFalso()
        dao.salvar(DadosDeExemplo.propriedades().first().paraEntidade(usuarioId = "u1"))
        val repositorio = PropriedadeRepositoryRoom(dao, LancamentoDaoFalso(dao), SessaoMemoria(null))

        assertEquals(emptyList<Any>(), repositorio.observarTodas().first())
        try {
            repositorio.salvar(DadosDeExemplo.propriedades().last())
            fail("Sem sessão não se grava nada")
        } catch (esperado: IllegalStateException) {
            assertEquals(1, dao.tabela.value.size)
        }
    }

    private suspend fun cenarioDeLancamentos(): Triple<PropriedadeDaoFalso, LancamentoDaoFalso, SessaoMemoria> {
        val propriedades = PropriedadeDaoFalso()
        DadosDeExemplo.propriedades().forEach { propriedades.salvar(it.paraEntidade(usuarioId = "u1")) }
        val lancamentos = LancamentoDaoFalso(propriedades)
        DadosDeExemplo.lancamentos(LocalDate.of(2026, 9, 30)).forEach { lancamentos.salvar(it.paraEntidade()) }
        return Triple(propriedades, lancamentos, SessaoMemoria("u1"))
    }

    @Test
    fun `excluir propriedade leva os lancamentos dela e desfazer traz tudo de volta`() = runTest {
        val (propriedadesDao, lancamentosDao, sessao) = cenarioDeLancamentos()
        val transacoes = mutableListOf<String>()
        val transacao = object : Transacao {
            override suspend fun <R> executar(bloco: suspend () -> R): R {
                transacoes += "transação"
                return bloco()
            }
        }
        val propriedades = PropriedadeRepositoryRoom(propriedadesDao, lancamentosDao, sessao, transacao)
        val lancamentos = LancamentoRepositoryRoom(lancamentosDao, sessao)

        val excluida = checkNotNull(propriedades.excluirComLancamentos(DadosDeExemplo.ID_SITIO))

        assertEquals(3, excluida.lancamentos.size)
        assertNull(propriedades.buscarPorId(DadosDeExemplo.ID_SITIO))
        assertTrue(lancamentos.observarTodos().first().all { it.propriedadeId == DadosDeExemplo.ID_FAZENDA })

        propriedades.restaurar(excluida)

        assertEquals(excluida.propriedade, propriedades.buscarPorId(DadosDeExemplo.ID_SITIO))
        assertEquals(9, lancamentos.observarTodos().first().size)
        assertEquals("excluir e restaurar, cada um numa transação", 2, transacoes.size)
    }

    @Test
    fun `excluir propriedade de outra conta nao apaga nada`() = runTest {
        val (propriedadesDao, lancamentosDao, _) = cenarioDeLancamentos()
        val propriedades = PropriedadeRepositoryRoom(propriedadesDao, lancamentosDao, SessaoMemoria("u2"))

        assertNull(propriedades.excluirComLancamentos(DadosDeExemplo.ID_SITIO))
        assertEquals(9, lancamentosDao.tabela.value.size)
        assertEquals(2, propriedadesDao.tabela.value.size)
    }

    @Test
    fun `lancamentos de outra conta nao aparecem nem podem ser gravados`() = runTest {
        val (_, dao, sessao) = cenarioDeLancamentos()
        val repositorio = LancamentoRepositoryRoom(dao, sessao)
        assertEquals(9, repositorio.observarTodos().first().size)

        sessao.iniciar("u2")
        assertEquals(0, repositorio.observarTodos().first().size)
        assertNull(repositorio.buscarPorId("exemplo-1"))

        val intruso = Lancamento(
            propriedadeId = DadosDeExemplo.ID_FAZENDA, tipo = TipoLancamento.DESPESA,
            categoria = CategoriaLancamento.ADUBO, valorCentavos = 100L, data = LocalDate.of(2026, 9, 1)
        )
        try {
            repositorio.salvar(intruso)
            fail("Não pode lançar na propriedade de outra conta")
        } catch (esperado: IllegalArgumentException) {
            assertEquals(9, dao.tabela.value.size)
        }
    }

    @Test
    fun `conta nova fica com as propriedades sem dono`() = runTest {
        val propriedades = PropriedadeDaoFalso()
        DadosDeExemplo.propriedades().forEach { propriedades.salvar(it.paraEntidade(usuarioId = null)) }
        val repositorio = UsuarioRepositoryRoom(UsuarioDaoFalso(propriedades))

        assertTrue(repositorio.criar(usuario("u1", "fabio@exemplo.com")))

        assertTrue(propriedades.tabela.value.all { it.usuarioId == "u1" })
    }

    @Test
    fun `email repetido nao cria conta nem rouba propriedades`() = runTest {
        val propriedades = PropriedadeDaoFalso()
        val repositorio = UsuarioRepositoryRoom(UsuarioDaoFalso(propriedades))
        repositorio.criar(usuario("u1", "fabio@exemplo.com"))
        propriedades.salvar(DadosDeExemplo.propriedades().first().paraEntidade(usuarioId = null))

        assertFalse(repositorio.criar(usuario("u2", "fabio@exemplo.com")))

        assertNull(repositorio.buscarPorId("u2"))
        assertNull(propriedades.tabela.value.single().usuarioId)
    }

    @Test
    fun `novo aceite grava a data e a versao do termo`() = runTest {
        val repositorio = UsuarioRepositoryRoom(UsuarioDaoFalso(PropriedadeDaoFalso()))
        repositorio.criar(usuario("u1", "fabio@exemplo.com"))

        repositorio.registrarConsentimento("u1", Instant.ofEpochMilli(5_000L), versao = 2)

        val gravado = repositorio.buscarPorId("u1")
        assertEquals(2, gravado?.versaoTermo)
        assertEquals(Instant.ofEpochMilli(5_000L), gravado?.consentimentoEm)
    }
}
