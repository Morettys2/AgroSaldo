package br.joao.agrosaldo.clima

import br.joao.agrosaldo.clima.ApoioClima.AGORA
import br.joao.agrosaldo.data.preferencias.AjustesAvisoPorConta
import br.joao.agrosaldo.data.preferencias.ArmazenamentoMemoria
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.domain.clima.LimitesChuva
import br.joao.agrosaldo.domain.repository.AjustesAviso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ajustes do aviso de chuva separados por conta (com a migração dos ajustes do aparelho). */
class AjustesAvisoPorContaTest {

    private val armazenamento = ArmazenamentoMemoria()
    private val sessao = SessaoMemoria("ana")

    private fun repositorio(aoEntrarEmConta: (AjustesAviso) -> Unit = {}) = AjustesAvisoPorConta(
        armazenamento,
        sessao,
        aoEntrarEmConta,
        escopo = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    )

    @Test
    fun `ajustes e ultimo aviso valem so para a conta que gravou`() {
        val repo = repositorio()
        repo.salvar(AjustesAviso(ativo = false))
        repo.registrarAviso("p1", AGORA)

        sessao.iniciar("bia")
        assertEquals("outra conta começa do padrão", AjustesAviso(), repo.ajustes.value)
        assertNull(repo.ultimoAvisoEm("p1"))

        sessao.iniciar("ana")
        assertFalse(repo.ajustes.value.ativo)
        assertEquals(AGORA, repo.ultimoAvisoEm("p1"))
    }

    @Test
    fun `sem sessao valem os padroes e nada e gravado`() {
        sessao.encerrar()
        val repo = repositorio()

        repo.salvar(AjustesAviso(ativo = false))
        repo.registrarAviso("p1", AGORA)

        assertEquals(AjustesAviso(), repo.ajustes.value)
        assertTrue(armazenamento.dados.isEmpty())
    }

    @Test
    fun `ajustes gravados antes da separacao por conta ficam com a primeira conta que entra`() {
        armazenamento.dados.putAll(
            mapOf(
                "ativo" to false,
                "probabilidade_minima" to 80,
                "chuva_minima_mm" to 5f,
                "janela_horas" to 24,
                "propriedades_sem_aviso" to setOf("p2"),
                "ultimo_aviso_p1" to AGORA.toEpochMilli()
            )
        )
        sessao.encerrar() // O app abre na tela de login.
        val repo = repositorio()

        sessao.iniciar("ana")

        assertEquals(AjustesAviso(false, LimitesChuva(80, 5.0, 24), setOf("p2")), repo.ajustes.value)
        assertEquals(AGORA, repo.ultimoAvisoEm("p1"))
        assertTrue("sobrou chave sem conta: ${armazenamento.dados.keys}", armazenamento.dados.keys.all { it.startsWith("ana:") })

        sessao.iniciar("bia")
        assertEquals("só a primeira conta adota", AjustesAviso(), repo.ajustes.value)
    }

    @Test
    fun `ajuste gravado fora da faixa volta ao padrao`() {
        armazenamento.dados["ana:janela_horas"] = 99
        assertEquals(LimitesChuva(), repositorio().ajustes.value.limites)
    }

    @Test
    fun `excluir os dados de uma conta apaga tudo dela e nada das outras`() {
        val repo = repositorio()
        repo.salvar(AjustesAviso(ativo = false))
        repo.registrarAviso("p1", AGORA)
        sessao.iniciar("bia")
        repo.salvar(AjustesAviso(limites = LimitesChuva(janelaHoras = 24)))
        repo.registrarAviso("p2", AGORA)

        repo.excluirDadosDoUsuario("ana")

        assertTrue(armazenamento.dados.keys.none { it.startsWith("ana:") })
        assertEquals(24, repo.ajustes.value.limites.janelaHoras)
        assertEquals(AGORA, repo.ultimoAvisoEm("p2"))

        repo.excluirDadosDoUsuario("bia")

        assertTrue(armazenamento.dados.isEmpty())
        assertEquals("a conta conectada volta ao padrão", AjustesAviso(), repo.ajustes.value)
    }

    @Test
    fun `ultimo aviso de propriedade que nao existe mais e esquecido so na conta atual`() {
        val repo = repositorio()
        sessao.iniciar("bia")
        repo.registrarAviso("da-bia", AGORA)
        sessao.iniciar("ana")
        repo.registrarAviso("p1", AGORA)
        repo.registrarAviso("excluida", AGORA)

        repo.esquecerAvisosExceto(setOf("p1"))

        assertEquals(AGORA, repo.ultimoAvisoEm("p1"))
        assertNull(repo.ultimoAvisoEm("excluida"))
        sessao.iniciar("bia")
        assertEquals(AGORA, repo.ultimoAvisoEm("da-bia"))
    }

    @Test
    fun `entrar em outra conta entrega os ajustes dela`() {
        val entradas = mutableListOf<AjustesAviso>()
        val repo = repositorio { entradas += it }
        repo.salvar(AjustesAviso(ativo = false))
        assertTrue("a conta conectada na criação não conta como entrada", entradas.isEmpty())

        sessao.encerrar()
        sessao.iniciar("bia")
        sessao.iniciar("ana")

        assertEquals(listOf(true, false), entradas.map { it.ativo })
    }
}
