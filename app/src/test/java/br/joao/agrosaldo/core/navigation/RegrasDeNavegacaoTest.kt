package br.joao.agrosaldo.core.navigation

import br.joao.agrosaldo.core.navigation.RegrasDeNavegacao.destino
import br.joao.agrosaldo.core.navigation.RegrasDeNavegacao.telaPodeAparecer
import br.joao.agrosaldo.domain.model.TipoLancamento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Para onde o app vai quando a pessoa entra, sai ou a política muda. */
class RegrasDeNavegacaoTest {

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
        // Sem o redirecionamento, o "voltar" levaria um usuário desconectado para a tela de Conta.
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

    @Test
    fun `sem saber ainda do novo aceite quem esta conectado espera`() {
        assertNull(destino(Rotas.INICIO, null, logado = true, precisaNovoAceite = null))
        assertNull(destino(Rotas.LOGIN, null, logado = true, precisaNovoAceite = null))
        assertEquals(Rotas.LOGIN, destino(Rotas.CONTA, Rotas.INICIO, logado = false, precisaNovoAceite = null))
    }

    @Test
    fun `clima aberto por fora antes do novo aceite volta para o aceite`() {
        assertEquals(
            Rotas.NOVO_CONSENTIMENTO,
            destino(Rotas.CLIMA, Rotas.NOVO_CONSENTIMENTO, logado = true, precisaNovoAceite = true)
        )
    }

    @Test
    fun `tela so aparece com a sessao conhecida e sem desvio pendente`() {
        assertTrue(telaPodeAparecer(Rotas.INICIO, null, logado = true, precisaNovoAceite = false))
        assertTrue(telaPodeAparecer(Rotas.LOGIN, null, logado = false, precisaNovoAceite = false))
        // NavHost ainda sem rota.
        assertFalse(telaPodeAparecer(null, null, logado = true, precisaNovoAceite = false))
        // Conta ainda sendo lida.
        assertFalse(telaPodeAparecer(Rotas.INICIO, null, logado = true, precisaNovoAceite = null))
        // Desvio para o novo aceite ainda não aconteceu.
        assertFalse(telaPodeAparecer(Rotas.INICIO, null, logado = true, precisaNovoAceite = true))
        assertTrue(telaPodeAparecer(Rotas.NOVO_CONSENTIMENTO, null, logado = true, precisaNovoAceite = true))
    }

    @Test
    fun `rota do formulario leva o tipo dos atalhos do inicio`() {
        assertEquals("lancamento/form?tipo=RECEITA", Rotas.formLancamento(tipo = TipoLancamento.RECEITA))
        assertEquals("lancamento/form?tipo=DESPESA", Rotas.formLancamento(tipo = TipoLancamento.DESPESA))
        assertEquals("lancamento/form", Rotas.formLancamento())
    }

    @Test
    fun `editar e copiar ignoram o tipo`() {
        assertEquals("lancamento/form?id=abc", Rotas.formLancamento(id = "abc", tipo = TipoLancamento.RECEITA))
        assertEquals("lancamento/form?copia=abc", Rotas.formLancamento(copiaDe = "abc", tipo = TipoLancamento.RECEITA))
    }

    @Test
    fun `tipo lido da rota volta ao mesmo, e texto estranho vira nulo`() {
        TipoLancamento.entries.forEach { tipo ->
            val rota = Rotas.formLancamento(tipo = tipo)
            assertEquals(tipo, Rotas.tipoDaRota(rota.substringAfter("tipo=")))
        }
        assertNull(Rotas.tipoDaRota(null))
        assertNull(Rotas.tipoDaRota("receita"))
        assertNull(Rotas.tipoDaRota(""))
    }

    @Test
    fun `cabecalho escuro so nas abas e no novo lancamento`() {
        assertEquals(
            setOf(Rotas.INICIO, Rotas.LANCAMENTOS, Rotas.CLIMA, Rotas.PROPRIEDADES, Rotas.FORM_LANCAMENTO),
            Rotas.COM_CABECALHO_ESCURO
        )
        assertTrue(Rotas.LOGIN !in Rotas.COM_CABECALHO_ESCURO)
        assertTrue(Rotas.CONTA !in Rotas.COM_CABECALHO_ESCURO)
    }
}
