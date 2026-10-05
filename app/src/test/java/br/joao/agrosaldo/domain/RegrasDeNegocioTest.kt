package br.joao.agrosaldo.domain

import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Testa as regras que moram no próprio modelo (camada M do MVVM).
 *
 * Nenhum Android envolvido: são classes Kotlin puras, e o teste roda em
 * milissegundos no computador.
 */
class RegrasDeNegocioTest {

    private fun lancamento(
        tipo: TipoLancamento = TipoLancamento.DESPESA,
        categoria: CategoriaLancamento = CategoriaLancamento.ADUBO,
        valor: Long = 10_000L,
        data: LocalDate = LocalDate.now(),
        propriedadeId: String = "p1"
    ) = Lancamento(
        propriedadeId = propriedadeId, tipo = tipo, categoria = categoria,
        valorCentavos = valor, data = data
    )

    /** Roda o bloco e devolve true se ele lançou IllegalArgumentException. */
    private fun recusa(bloco: () -> Unit): Boolean =
        try { bloco(); false } catch (e: IllegalArgumentException) { true }

    @Test
    fun `RN06 - valor zero ou negativo e recusado`() {
        assertTrue(recusa { lancamento(valor = 0L) })
        assertTrue(recusa { lancamento(valor = -500L) })
    }

    @Test
    fun `categoria de receita nao pode ser usada em despesa`() {
        assertTrue(recusa {
            lancamento(tipo = TipoLancamento.DESPESA, categoria = CategoriaLancamento.VENDA_SAFRA)
        })
        assertTrue(recusa {
            lancamento(tipo = TipoLancamento.RECEITA, categoria = CategoriaLancamento.ADUBO)
        })
    }

    @Test
    fun `RN07 - data mais de 12 meses no futuro e recusada`() {
        assertTrue(recusa { lancamento(data = LocalDate.now().plusMonths(13)) })
        // Dentro do limite passa normalmente.
        lancamento(data = LocalDate.now().plusMonths(11))
    }

    @Test
    fun `RN01 - lancamento sem propriedade e recusado`() {
        assertTrue(recusa { lancamento(propriedadeId = "  ") })
    }

    @Test
    fun `RN03 - receita soma e despesa subtrai no saldo`() {
        val receita = lancamento(
            tipo = TipoLancamento.RECEITA, categoria = CategoriaLancamento.VENDA_SAFRA, valor = 1_000_00L
        )
        val despesa = lancamento(valor = 300_00L)
        assertEquals(1_000_00L, receita.valorComSinal)
        assertEquals(-300_00L, despesa.valorComSinal)
        assertEquals(700_00L, listOf(receita, despesa).sumOf { it.valorComSinal })
    }

    @Test
    fun `RF06 - as 7 despesas da proposta e mais Outras despesas, por ultimo`() {
        val despesas = CategoriaLancamento.porTipo(TipoLancamento.DESPESA)
        assertEquals(8, despesas.size)
        assertTrue(despesas.all { it.tipo == TipoLancamento.DESPESA })
        assertEquals(CategoriaLancamento.OUTRAS_DESPESAS, despesas.last())
        // A primeira continua sendo a sugerida no formulário novo.
        assertEquals(CategoriaLancamento.ENERGIA, despesas.first())
    }

    @Test
    fun `cada lado tem a sua categoria Outras e elas nao se misturam`() {
        assertTrue(recusa {
            lancamento(tipo = TipoLancamento.RECEITA, categoria = CategoriaLancamento.OUTRAS_DESPESAS)
        })
        assertTrue(recusa {
            lancamento(tipo = TipoLancamento.DESPESA, categoria = CategoriaLancamento.OUTROS)
        })
    }

    @Test
    fun `propriedade valida nome, area e coordenadas`() {
        fun propriedade(nome: String = "Sítio", area: Double = 10.0, lat: Double = -21.0, lon: Double = -46.0) =
            Propriedade(nome = nome, cultura = "Milho", areaHectares = area, latitude = lat, longitude = lon)

        assertTrue(recusa { propriedade(nome = "") })
        assertTrue(recusa { propriedade(area = 0.0) })
        assertTrue(recusa { propriedade(lat = 91.0) })
        assertTrue(recusa { propriedade(lon = -181.0) })
        propriedade() // válida, não lança
    }
}
