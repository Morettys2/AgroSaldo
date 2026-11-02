package br.joao.agrosaldo.data

import br.joao.agrosaldo.data.repository.UsuarioLogadoRoom
import br.joao.agrosaldo.data.repository.UsuarioRepositoryMemoria
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.domain.RelogioMonotonicoIndisponivel
import br.joao.agrosaldo.domain.model.Usuario
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** O relógio do app, os relógios de teste e o nome do usuário conectado. */
class RelogiosEUsuarioLogadoTest {

    private lateinit var fusoOriginal: TimeZone

    @Before
    fun guardarFuso() {
        fusoOriginal = TimeZone.getDefault()
    }

    @After
    fun restaurarFuso() {
        TimeZone.setDefault(fusoOriginal)
    }

    @Test
    fun `relogio do aparelho acompanha o fuso a cada leitura e nao o de quando foi criado`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
        assertEquals(ZoneId.of("America/Sao_Paulo"), RelogioDoAparelho.zone)

        // Quem viaja com o app aberto: o dia passa a ser o do novo fuso.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(ZoneId.of("Asia/Tokyo"), RelogioDoAparelho.zone)
    }

    @Test
    fun `relogio do aparelho anda com o tempo real`() {
        val antes = System.currentTimeMillis()
        val lido = RelogioDoAparelho.millis()
        val depois = System.currentTimeMillis()

        assertTrue(lido in antes..depois)
    }

    @Test
    fun `relogio mutavel muda o dia e mantem a hora`() {
        val relogio = RelogioMutavel(java.time.LocalDate.of(2026, 9, 30))

        relogio.irParaODia(java.time.LocalDate.of(2026, 10, 1))

        assertEquals(java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.now(relogio))
        assertEquals(java.time.LocalTime.NOON, java.time.LocalTime.now(relogio))
    }

    @Test
    fun `relogio por milissegundos le a fonte a cada chamada`() {
        var ms = 1_000L
        val relogio = relogioPorMs(ZoneOffset.UTC) { ms }

        assertEquals(1_000L, relogio.millis())
        ms = 5_000L
        assertEquals(5_000L, relogio.millis())
    }

    @Test
    fun `relogio monotonico sem boot conhecido nao compara com outros processos`() {
        assertNull(RelogioMonotonicoIndisponivel.instante())
        assertTrue(RelogioMonotonicoIndisponivel.milissegundos() >= 0L)
    }

    private fun usuario(id: String, nome: String) = Usuario(
        id = id,
        nome = nome,
        email = "$id@exemplo.com",
        senhaHash = "hash-de-teste",
        consentimentoEm = Instant.ofEpochMilli(1_000L),
        versaoTermo = 1,
        criadoEm = Instant.ofEpochMilli(1_000L)
    )

    @Test
    fun `usuario logado entrega o nome de quem esta na sessao`() = runTest {
        val usuarios = UsuarioRepositoryMemoria()
        usuarios.criar(usuario("u1", "Maria Souza"))
        val repositorio = UsuarioLogadoRoom(SessaoMemoria("u1"), usuarios)

        assertEquals("Maria Souza", repositorio.observarNome().first())
    }

    @Test
    fun `usuario logado e nulo sem sessao e acompanha login e logout`() = runTest {
        val usuarios = UsuarioRepositoryMemoria()
        usuarios.criar(usuario("u1", "Maria Souza"))
        val sessao = SessaoMemoria()
        val repositorio = UsuarioLogadoRoom(sessao, usuarios)

        assertNull(repositorio.observarNome().first())
        sessao.iniciar("u1")
        assertEquals("Maria Souza", repositorio.observarNome().first())
        sessao.encerrar()
        assertNull(repositorio.observarNome().first())
    }
}
