package br.joao.agrosaldo.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quando a troca de tela não anima (evita o "fantasma" do crossfade padrão entre abas). */
class TransicoesDeTelaTest {

    @Test
    fun `trocar entre abas da barra inferior nao anima`() {
        Rotas.ABAS.forEach { origem ->
            Rotas.ABAS.forEach { destino -> assertTrue("$origem -> $destino", trocaEntreAbas(origem, destino)) }
        }
    }

    @Test
    fun `abrir tela que nao e aba anima`() {
        assertFalse(trocaEntreAbas(Rotas.INICIO, Rotas.CONTA))
        assertFalse(trocaEntreAbas(Rotas.CONTA, Rotas.INICIO))
        assertFalse(trocaEntreAbas(Rotas.LOGIN, Rotas.CRIAR_CONTA))
    }

    @Test
    fun `rota desconhecida anima`() {
        assertFalse(trocaEntreAbas(null, Rotas.INICIO))
        assertFalse(trocaEntreAbas(Rotas.INICIO, null))
    }
}
