package br.joao.agrosaldo.data

import br.joao.agrosaldo.data.local.Semeador
import br.joao.agrosaldo.data.local.Transacao
import br.joao.agrosaldo.data.local.TransacaoDireta
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.dao.UsuarioDao
import br.joao.agrosaldo.data.local.entidade.LancamentoEntity
import br.joao.agrosaldo.data.local.entidade.PropriedadeEntity
import br.joao.agrosaldo.data.local.entidade.UsuarioEntity
import br.joao.agrosaldo.data.local.paraDominio
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.data.relogioEm
import br.joao.agrosaldo.data.repository.LancamentoRepositoryRoom
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryRoom
import br.joao.agrosaldo.data.repository.UsuarioRepositoryRoom
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.model.Usuario
import br.joao.agrosaldo.domain.model.emOrdemAlfabetica
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Camada de dados na JVM: mapeadores, semeador e repositórios Room sobre
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
            id = "l1",
            propriedadeId = "p1",
            tipo = TipoLancamento.DESPESA,
            categoria = CategoriaLancamento.MAO_DE_OBRA,
            valorCentavos = 210_000L,
            data = LocalDate.of(2026, 9, 22),
            descricao = "Diarista"
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
                propriedadeId = "p1",
                tipo = categoria.tipo,
                categoria = categoria,
                valorCentavos = 1L,
                data = LocalDate.of(2026, 1, 1)
            )
            assertEquals(categoria, l.paraEntidade(agora = 0L).paraDominio().categoria)
        }
    }

    @Test
    fun `usuario vai para o banco e volta igual com instantes em milissegundos`() {
        val original = usuario("u1", "maria@exemplo.com")
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

    /** DAO em memória que filtra por dono como as queries reais (sem ordenar, como o SQL). */
    private class PropriedadeDaoFalso : PropriedadeDao {
        val tabela = MutableStateFlow<List<PropriedadeEntity>>(emptyList())
        override fun observarDoUsuario(usuarioId: String): Flow<List<PropriedadeEntity>> =
            tabela.map { lista -> lista.filter { it.usuarioId == usuarioId } }
        override suspend fun buscarPorId(id: String, usuarioId: String) =
            tabela.value.firstOrNull { it.id == id && it.usuarioId == usuarioId }
        override suspend fun existeSemSerDoUsuario(id: String, usuarioId: String) =
            tabela.value.any { it.id == id && it.usuarioId != usuarioId }
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
        override suspend fun existeSemSerDoUsuario(id: String, usuarioId: String) =
            tabela.value.any { it.id == id && !propriedadeEhDoUsuario(it.propriedadeId, usuarioId) }
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
        override suspend fun contar() = tabela.value.size
        override suspend fun adotarPropriedadesSemDono(usuarioId: String): Int {
            val orfas = propriedades.tabela.value.count { it.usuarioId == null }
            propriedades.tabela.update { lista ->
                lista.map { if (it.usuarioId == null) it.copy(usuarioId = usuarioId) else it }
            }
            return orfas
        }
    }

    private fun usuario(id: String, email: String) = Usuario(
        id = id,
        nome = "Maria Souza",
        email = email,
        senhaHash = "hash-de-teste",
        consentimentoEm = Instant.ofEpochMilli(1_000L),
        versaoTermo = 1,
        criadoEm = Instant.ofEpochMilli(1_000L)
    )

    @Test
    fun `propriedades vem em ordem alfabetica do portugues, com acento e minuscula no lugar certo`() = runTest {
        val dao = PropriedadeDaoFalso()
        val modelo = DadosDeExemplo.propriedades().first()
        listOf("Sítio Exemplo", "Água Exemplo", "chácara Três Irmãos", "Fazenda Modelo").forEachIndexed { i, nome ->
            dao.salvar(modelo.copy(id = "p$i", nome = nome).paraEntidade(usuarioId = "u1", agora = 0L))
        }
        val repositorio =
            PropriedadeRepositoryRoom(
                dao,
                LancamentoDaoFalso(dao),
                SessaoMemoria("u1"),
                TransacaoDireta,
                relogio = relogioEm(LocalDate.of(2026, 9, 30))
            )

        assertEquals(
            "a primeira da lista é a propriedade padrão",
            listOf("Água Exemplo", "chácara Três Irmãos", "Fazenda Modelo", "Sítio Exemplo"),
            repositorio.observarTodas().first().map { it.nome }
        )
    }

    @Test
    fun `repositorio Room entrega objetos de dominio a partir das entidades`() = runTest {
        val dao = PropriedadeDaoFalso()
        val repositorio =
            PropriedadeRepositoryRoom(
                dao,
                LancamentoDaoFalso(dao),
                SessaoMemoria("u1"),
                TransacaoDireta,
                relogio = relogioEm(LocalDate.of(2026, 9, 30))
            )
        val fazenda = DadosDeExemplo.propriedades().first()

        repositorio.salvar(fazenda)

        assertEquals("u1", dao.tabela.value.single().usuarioId)
        assertEquals(listOf(fazenda), repositorio.observarTodas().first())
        assertEquals(fazenda, repositorio.buscarPorId(fazenda.id))

        assertNotNull(repositorio.excluirComLancamentos(fazenda.id))
        assertNull(repositorio.buscarPorId(fazenda.id))
    }

    @Test
    fun `cada conta so enxerga as proprias propriedades`() = runTest {
        val dao = PropriedadeDaoFalso()
        val sessao = SessaoMemoria("u1")
        val repositorio =
            PropriedadeRepositoryRoom(dao, LancamentoDaoFalso(dao), sessao, TransacaoDireta, relogio = relogioEm(LocalDate.of(2026, 9, 30)))
        val fazenda = DadosDeExemplo.propriedades().first()
        repositorio.salvar(fazenda)

        sessao.iniciar("u2")
        assertEquals(emptyList<Any>(), repositorio.observarTodas().first())
        assertNull(repositorio.buscarPorId(fazenda.id))

        // Excluir pela conta errada não apaga nada.
        assertNull(repositorio.excluirComLancamentos(fazenda.id))
        assertEquals(1, dao.tabela.value.size)

        sessao.iniciar("u1")
        assertEquals(listOf(fazenda), repositorio.observarTodas().first())
    }

    @Test
    fun `uma conta nao sobrescreve a propriedade da outra`() = runTest {
        val dao = PropriedadeDaoFalso()
        val sessao = SessaoMemoria("u1")
        val repositorio =
            PropriedadeRepositoryRoom(dao, LancamentoDaoFalso(dao), sessao, TransacaoDireta, relogio = relogioEm(LocalDate.of(2026, 9, 30)))
        val fazenda = DadosDeExemplo.propriedades().first()
        repositorio.salvar(fazenda)

        sessao.iniciar("u2")
        try {
            repositorio.salvar(fazenda.copy(nome = "Tomada"))
            fail("Deveria recusar a gravação")
        } catch (esperado: IllegalArgumentException) {
            assertEquals("Fazenda Modelo", dao.tabela.value.single().nome)
        }
    }

    /** Conta as transações abertas e marca se a escrita no DAO aconteceu dentro de uma. */
    private class TransacaoContada : Transacao {
        var execucoes = 0
        var aberta = false
        override suspend fun <R> executar(bloco: suspend () -> R): R {
            execucoes++
            aberta = true
            try {
                return bloco()
            } finally {
                aberta = false
            }
        }
    }

    @Test
    fun `propriedade sem dono nao e adotada por quem salva com o mesmo id`() = runTest {
        val dao = PropriedadeDaoFalso()
        val exemplo = DadosDeExemplo.propriedades().first()
        dao.salvar(exemplo.paraEntidade(usuarioId = null, agora = 0L))
        val repositorio =
            PropriedadeRepositoryRoom(
                dao,
                LancamentoDaoFalso(dao),
                SessaoMemoria("u1"),
                TransacaoDireta,
                relogio = relogioEm(LocalDate.of(2026, 9, 30))
            )

        try {
            repositorio.salvar(exemplo.copy(nome = "Tomada"))
            fail("Deveria recusar a gravação")
        } catch (esperado: IllegalArgumentException) {
            val linha = dao.tabela.value.single()
            assertNull(linha.usuarioId)
            assertEquals(exemplo.nome, linha.nome)
        }
    }

    @Test
    fun `salvar propriedade confere o dono e grava na mesma transacao`() = runTest {
        val transacao = TransacaoContada()
        var gravouDentro = false
        val dao = object : PropriedadeDao by PropriedadeDaoFalso() {
            override suspend fun salvar(propriedade: PropriedadeEntity) {
                gravouDentro = transacao.aberta
            }
        }
        val repositorio =
            PropriedadeRepositoryRoom(
                dao,
                LancamentoDaoFalso(PropriedadeDaoFalso()),
                SessaoMemoria("u1"),
                transacao,
                relogio = relogioEm(LocalDate.of(2026, 9, 30))
            )

        repositorio.salvar(DadosDeExemplo.propriedades().first())

        assertEquals(1, transacao.execucoes)
        assertTrue(gravouDentro)
    }

    @Test
    fun `lancamento de outra conta nao muda de dono por ter o mesmo id`() = runTest {
        val (propriedades, dao, _) = cenarioDeLancamentos()
        propriedades.salvar(
            DadosDeExemplo.propriedades().first().copy(id = "sitio-u2", nome = "Sítio da outra conta")
                .paraEntidade(usuarioId = "u2", agora = 0L)
        )
        val repositorio =
            LancamentoRepositoryRoom(dao, SessaoMemoria("u2"), TransacaoDireta, relogio = relogioEm(LocalDate.of(2026, 9, 30)))
        val original = checkNotNull(dao.tabela.value.firstOrNull { it.id == "exemplo-1" })

        try {
            repositorio.salvar(original.paraDominio().copy(propriedadeId = "sitio-u2", descricao = "Tomado"))
            fail("Não pode sobrescrever o lançamento de outra conta")
        } catch (esperado: IllegalArgumentException) {
            assertEquals(original, dao.tabela.value.first { it.id == "exemplo-1" })
        }
    }

    @Test
    fun `salvar lancamento confere o dono e grava na mesma transacao`() = runTest {
        val (_, falso, sessao) = cenarioDeLancamentos()
        val transacao = TransacaoContada()
        var gravouDentro = false
        val dao = object : LancamentoDao by falso {
            override suspend fun salvar(lancamento: LancamentoEntity) {
                gravouDentro = transacao.aberta
            }
        }
        val repositorio = LancamentoRepositoryRoom(dao, sessao, transacao, relogio = relogioEm(LocalDate.of(2026, 9, 30)))
        val existente = checkNotNull(repositorio.buscarPorId("exemplo-2"))

        repositorio.salvar(existente.copy(valorCentavos = 130_000L))

        assertEquals(1, transacao.execucoes)
        assertTrue(gravouDentro)
    }

    @Test
    fun `excluir lancamento le e apaga na mesma transacao e devolve o apagado`() = runTest {
        val (_, falso, sessao) = cenarioDeLancamentos()
        val transacao = TransacaoContada()
        val passos = mutableListOf<Boolean>()
        val dao = object : LancamentoDao by falso {
            override suspend fun buscarPorId(id: String, usuarioId: String): LancamentoEntity? {
                passos += transacao.aberta
                return falso.buscarPorId(id, usuarioId)
            }

            override suspend fun excluir(id: String, usuarioId: String) {
                passos += transacao.aberta
                falso.excluir(id, usuarioId)
            }
        }
        val repositorio = LancamentoRepositoryRoom(dao, sessao, transacao, relogio = relogioEm(LocalDate.of(2026, 9, 30)))
        val antes = checkNotNull(repositorio.buscarPorId("exemplo-2"))
        passos.clear()

        val apagado = repositorio.excluir("exemplo-2")

        assertEquals(antes, apagado?.lancamento)
        assertNull(repositorio.buscarPorId("exemplo-2"))
        assertEquals("ler e apagar dentro da transação", listOf(true, true), passos.take(2))
        assertEquals(1, transacao.execucoes)
    }

    @Test
    fun `excluir lancamento inexistente ou de outra conta devolve null e nao apaga nada`() = runTest {
        val (_, dao, _) = cenarioDeLancamentos()
        val repositorio =
            LancamentoRepositoryRoom(dao, SessaoMemoria("u2"), TransacaoDireta, relogio = relogioEm(LocalDate.of(2026, 9, 30)))

        assertNull(repositorio.excluir("exemplo-1"))
        assertNull(repositorio.excluir("nao-existe"))
        assertEquals(9, dao.tabela.value.size)
    }

    @Test
    fun `sem ninguem conectado a lista vem vazia e gravar e recusado`() = runTest {
        val dao = PropriedadeDaoFalso()
        dao.salvar(DadosDeExemplo.propriedades().first().paraEntidade(usuarioId = "u1", agora = 0L))
        val repositorio =
            PropriedadeRepositoryRoom(
                dao,
                LancamentoDaoFalso(dao),
                SessaoMemoria(null),
                TransacaoDireta,
                relogio = relogioEm(LocalDate.of(2026, 9, 30))
            )

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
        DadosDeExemplo.propriedades().forEach { propriedades.salvar(it.paraEntidade(usuarioId = "u1", agora = 0L)) }
        val lancamentos = LancamentoDaoFalso(propriedades)
        DadosDeExemplo.lancamentos(LocalDate.of(2026, 9, 30)).forEach { lancamentos.salvar(it.paraEntidade(agora = 0L)) }
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
        val propriedades =
            PropriedadeRepositoryRoom(propriedadesDao, lancamentosDao, sessao, transacao, relogio = relogioEm(LocalDate.of(2026, 9, 30)))
        val lancamentos = LancamentoRepositoryRoom(lancamentosDao, sessao, TransacaoDireta, relogio = relogioEm(LocalDate.of(2026, 9, 30)))

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
        val propriedades =
            PropriedadeRepositoryRoom(
                propriedadesDao,
                lancamentosDao,
                SessaoMemoria("u2"),
                TransacaoDireta,
                relogio = relogioEm(LocalDate.of(2026, 9, 30))
            )

        assertNull(propriedades.excluirComLancamentos(DadosDeExemplo.ID_SITIO))
        assertEquals(9, lancamentosDao.tabela.value.size)
        assertEquals(2, propriedadesDao.tabela.value.size)
    }

    @Test
    fun `lancamentos de outra conta nao aparecem nem podem ser gravados`() = runTest {
        val (_, dao, sessao) = cenarioDeLancamentos()
        val repositorio = LancamentoRepositoryRoom(dao, sessao, TransacaoDireta, relogio = relogioEm(LocalDate.of(2026, 9, 30)))
        assertEquals(9, repositorio.observarTodos().first().size)

        sessao.iniciar("u2")
        assertEquals(0, repositorio.observarTodos().first().size)
        assertNull(repositorio.buscarPorId("exemplo-1"))

        val intruso = Lancamento(
            propriedadeId = DadosDeExemplo.ID_FAZENDA,
            tipo = TipoLancamento.DESPESA,
            categoria = CategoriaLancamento.ADUBO,
            valorCentavos = 100L,
            data = LocalDate.of(2026, 9, 1)
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
        DadosDeExemplo.propriedades().forEach { propriedades.salvar(it.paraEntidade(usuarioId = null, agora = 0L)) }
        val repositorio = UsuarioRepositoryRoom(UsuarioDaoFalso(propriedades), TransacaoDireta)

        assertTrue(repositorio.criar(usuario("u1", "maria@exemplo.com")))

        assertTrue(propriedades.tabela.value.all { it.usuarioId == "u1" })
    }

    /** Transação falsa com rollback: restaura as tabelas em memória se o bloco falhar. */
    private class TransacaoEmMemoria(private val usuarios: UsuarioDaoFalso, private val propriedades: PropriedadeDaoFalso) : Transacao {
        var execucoes = 0
        override suspend fun <R> executar(bloco: suspend () -> R): R {
            execucoes++
            val usuariosAntes = usuarios.tabela.value
            val propriedadesAntes = propriedades.tabela.value
            try {
                return bloco()
            } catch (e: Exception) {
                usuarios.tabela.value = usuariosAntes
                propriedades.tabela.value = propriedadesAntes
                throw e
            }
        }
    }

    @Test
    fun `criar conta e adotar as propriedades sem dono e tudo ou nada`() = runTest {
        val propriedades = PropriedadeDaoFalso()
        propriedades.salvar(DadosDeExemplo.propriedades().first().paraEntidade(usuarioId = null, agora = 0L))
        val usuarios = UsuarioDaoFalso(propriedades)
        val adocaoQuebrada = object : UsuarioDao by usuarios {
            override suspend fun adotarPropriedadesSemDono(usuarioId: String): Int = throw IllegalStateException("disco cheio")
        }
        val transacao = TransacaoEmMemoria(usuarios, propriedades)
        val repositorio = UsuarioRepositoryRoom(adocaoQuebrada, transacao)

        try {
            repositorio.criar(usuario("u1", "maria@exemplo.com"))
            fail("A falha na adoção deveria chegar ao cadastro")
        } catch (esperado: IllegalStateException) {
            assertNull("a conta não fica criada pela metade", usuarios.buscarPorId("u1"))
            assertNull(propriedades.tabela.value.single().usuarioId)
            assertEquals(1, transacao.execucoes)
        }
    }

    @Test
    fun `email repetido nao cria conta nem rouba propriedades`() = runTest {
        val propriedades = PropriedadeDaoFalso()
        val repositorio = UsuarioRepositoryRoom(UsuarioDaoFalso(propriedades), TransacaoDireta)
        repositorio.criar(usuario("u1", "maria@exemplo.com"))
        propriedades.salvar(DadosDeExemplo.propriedades().first().paraEntidade(usuarioId = null, agora = 0L))

        assertFalse(repositorio.criar(usuario("u2", "maria@exemplo.com")))

        assertNull(repositorio.buscarPorId("u2"))
        assertNull(propriedades.tabela.value.single().usuarioId)
    }

    @Test
    fun `novo aceite grava a data e a versao do termo`() = runTest {
        val repositorio = UsuarioRepositoryRoom(UsuarioDaoFalso(PropriedadeDaoFalso()), TransacaoDireta)
        repositorio.criar(usuario("u1", "maria@exemplo.com"))

        repositorio.registrarConsentimento("u1", Instant.ofEpochMilli(5_000L), versao = 2)

        val gravado = repositorio.buscarPorId("u1")
        assertEquals(2, gravado?.versaoTermo)
        assertEquals(Instant.ofEpochMilli(5_000L), gravado?.consentimentoEm)
    }

    // ---------- registros gravados antes dos limites de 18/10/2026 (BUG-01) ----------

    @Test
    fun `lancamento antigo com descricao acima do limite e lido cortado`() {
        val antigo = Lancamento(
            propriedadeId = "p1",
            tipo = TipoLancamento.DESPESA,
            categoria = CategoriaLancamento.ADUBO,
            valorCentavos = 100L,
            data = LocalDate.of(2026, 1, 1)
        ).paraEntidade(agora = 0L).copy(descricao = "x".repeat(250))

        assertEquals(Lancamento.DESCRICAO_MAXIMA, antigo.paraDominio().descricao.length)
    }

    @Test
    fun `propriedade antiga com nome, cultura e area fora dos limites e lida normalizada`() {
        val antiga = DadosDeExemplo.propriedades().first().paraEntidade(usuarioId = "u1", agora = 0L)
            .copy(nome = "N".repeat(90), cultura = "C".repeat(70), areaHectares = Double.POSITIVE_INFINITY)

        val lida = antiga.paraDominio()

        assertEquals(Propriedade.NOME_MAXIMO, lida.nome.length)
        assertEquals(Propriedade.CULTURA_MAXIMA, lida.cultura.length)
        assertEquals(Propriedade.AREA_MAXIMA_HECTARES, lida.areaHectares, 0.0)
    }

    @Test
    fun `um lancamento antigo fora do limite nao esconde a lista inteira`() = runTest {
        val propriedadesDao = PropriedadeDaoFalso()
        val fazenda = DadosDeExemplo.propriedades().first()
        propriedadesDao.salvar(fazenda.paraEntidade(usuarioId = "u1", agora = 0L))
        val dao = LancamentoDaoFalso(propriedadesDao)
        DadosDeExemplo.lancamentos(LocalDate.of(2026, 9, 30))
            .filter { it.propriedadeId == fazenda.id }
            .forEach { dao.salvar(it.paraEntidade(agora = 0L)) }
        dao.salvar(dao.tabela.value.first().copy(descricao = "x".repeat(250)))
        val repositorio = LancamentoRepositoryRoom(dao, SessaoMemoria("u1"), TransacaoDireta, relogioEm(LocalDate.of(2026, 9, 30)))

        assertEquals(dao.tabela.value.size, repositorio.observarTodos().first().size)
    }

    @Test
    fun `usuario antigo com nome acima do limite e lido cortado`() {
        val antigo = usuario("u1", "maria@exemplo.com").paraEntidade().copy(nome = "N".repeat(120))

        assertEquals(Usuario.NOME_MAXIMO, antigo.paraDominio().nome.length)
    }

    // ---------- fakes do testFixtures iguais ao Room (FAKE-01) ----------

    @Test
    fun `fake em memoria entrega as propriedades na mesma ordem do repositorio Room`() = runTest {
        val modelo = DadosDeExemplo.propriedades().first()
        val nomes = listOf("Sítio Exemplo", "Água Exemplo", "chácara Três Irmãos", "Fazenda Modelo")
        val propriedades = nomes.mapIndexed { i, nome -> modelo.copy(id = "p$i", nome = nome) }

        val fake = PropriedadeRepositoryMemoria(propriedades)

        assertEquals(propriedades.emOrdemAlfabetica(), fake.observarTodas().first())
    }

    // ---------- desfazer devolve o atualizado_em original (DAD-01) ----------

    @Test
    fun `desfazer a exclusao de um lancamento devolve o atualizado_em original`() = runTest {
        val (_, dao, sessao) = cenarioDeLancamentos()
        val original = dao.tabela.value.first { it.id == "exemplo-2" }.copy(atualizadoEm = 111L)
        dao.salvar(original)
        val repositorio = LancamentoRepositoryRoom(dao, sessao, TransacaoDireta, relogio = relogioEm(LocalDate.of(2026, 9, 30)))

        repositorio.restaurar(checkNotNull(repositorio.excluir("exemplo-2")))

        assertEquals(original, dao.tabela.value.first { it.id == "exemplo-2" })
    }

    @Test
    fun `desfazer a exclusao de uma propriedade devolve o atualizado_em original dela e dos lancamentos`() = runTest {
        val (propriedadesDao, lancamentosDao, sessao) = cenarioDeLancamentos()
        val sitio = DadosDeExemplo.ID_SITIO
        propriedadesDao.salvar(propriedadesDao.tabela.value.first { it.id == sitio }.copy(atualizadoEm = 222L))
        lancamentosDao.tabela.value.filter { it.propriedadeId == sitio }
            .forEachIndexed { i, lancamento -> lancamentosDao.salvar(lancamento.copy(atualizadoEm = 300L + i)) }
        val antes = propriedadesDao.tabela.value.first { it.id == sitio }
        val lancamentosAntes = lancamentosDao.tabela.value.filter { it.propriedadeId == sitio }.sortedBy { it.id }
        val repositorio =
            PropriedadeRepositoryRoom(
                propriedadesDao,
                lancamentosDao,
                sessao,
                TransacaoDireta,
                relogio = relogioEm(LocalDate.of(2026, 9, 30))
            )

        repositorio.restaurar(checkNotNull(repositorio.excluirComLancamentos(sitio)))

        assertEquals(antes, propriedadesDao.tabela.value.first { it.id == sitio })
        assertEquals(lancamentosAntes, lancamentosDao.tabela.value.filter { it.propriedadeId == sitio }.sortedBy { it.id })
    }
}
