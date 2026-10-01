package br.joao.agrosaldo.core.navigation

import br.joao.agrosaldo.core.navigation.RegrasDeNavegacao.destino
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Para onde o app vai quando a pessoa entra, sai ou a política muda. */
class RegrasDeNavegacaoTest {

    // ---------- entrar ----------

    @Test
    fun `entrou pelo login vai para o inicio`() {
        assertEquals(Rotas.INICIO, destino(Rotas.LOGIN, null, logado = true, precisaNovoAceite = false))
        assertEquals(Rotas.INICIO, destino(Rotas.CONSENTIMENTO, Rotas.CRIAR_CONTA, logado = true, precisaNovoAceite = false))
    }

    @Test
    fun `conectado dentro do app fica onde esta`() {
        assertNull(destino(Rotas.CLIMA, Rotas.INICIO, logado = true, precisaNovoAceite = false))
        assertNull(destino(Rotas.POLITICA, Rotas.CONTA, logado = true, precisaNovoAceite = false))
    }

    // ---------- sair ----------

    @Test
    fun `saiu de dentro do app vai para o login`() {
        assertEquals(Rotas.LOGIN, destino(Rotas.CONTA, Rotas.PROPRIEDADES, logado = false, precisaNovoAceite = false))
    }

    @Test
    fun `desconectado pode ler a politica aberta pelo cadastro`() {
        assertNull(destino(Rotas.POLITICA, Rotas.CONSENTIMENTO, logado = false, precisaNovoAceite = false))
        assertNull(destino(Rotas.CRIAR_CONTA, Rotas.LOGIN, logado = false, precisaNovoAceite = false))
    }

    @Test
    fun `saiu lendo a politica aberta pela conta vai para o login`() {
        // Senão o "voltar" levaria um desconectado para dentro da Conta.
        assertEquals(Rotas.LOGIN, destino(Rotas.POLITICA, Rotas.CONTA, logado = false, precisaNovoAceite = false))
        assertEquals(
            Rotas.LOGIN,
            destino(Rotas.POLITICA, Rotas.NOVO_CONSENTIMENTO, logado = false, precisaNovoAceite = false)
        )
    }

    @Test
    fun `recusou a politica nova vai para o login`() {
        assertEquals(
            Rotas.LOGIN,
            destino(Rotas.NOVO_CONSENTIMENTO, null, logado = false, precisaNovoAceite = false)
        )
    }

    // ---------- política nova ----------

    @Test
    fun `politica mudou leva qualquer tela para o novo aceite`() {
        assertEquals(
            Rotas.NOVO_CONSENTIMENTO,
            destino(Rotas.INICIO, null, logado = true, precisaNovoAceite = true)
        )
        assertEquals(
            Rotas.NOVO_CONSENTIMENTO,
            destino(Rotas.LOGIN, null, logado = true, precisaNovoAceite = true)
        )
    }

    @Test
    fun `no novo aceite da para ler a politica`() {
        assertNull(destino(Rotas.NOVO_CONSENTIMENTO, null, logado = true, precisaNovoAceite = true))
        assertNull(destino(Rotas.POLITICA, Rotas.NOVO_CONSENTIMENTO, logado = true, precisaNovoAceite = true))
    }

    @Test
    fun `politica aberta pela conta nao fura o novo aceite`() {
        assertEquals(
            Rotas.NOVO_CONSENTIMENTO,
            destino(Rotas.POLITICA, Rotas.CONTA, logado = true, precisaNovoAceite = true)
        )
    }

    @Test
    fun `aceitou a politica nova vai para o inicio`() {
        assertEquals(
            Rotas.INICIO,
            destino(Rotas.NOVO_CONSENTIMENTO, null, logado = true, precisaNovoAceite = false)
        )
    }
}
