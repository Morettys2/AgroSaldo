package br.joao.agrosaldo.domain

import br.joao.agrosaldo.domain.financeiro.ResumoFinanceiro
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras de negócio validadas no próprio modelo (Lancamento, CategoriaLancamento, Propriedade). */
class RegrasDeNegocioTest {

    private fun lancamento(
        tipo: TipoLancamento = TipoLancamento.DESPESA,
        categoria: CategoriaLancamento = CategoriaLancamento.ADUBO,
        valor: Long = 10_000L,
        data: LocalDate = LocalDate.now(),
        propriedadeId: String = "p1"
    ) = Lancamento(
        propriedadeId = propriedadeId,
        tipo = tipo,
        categoria = categoria,
        valorCentavos = valor,
        data = data
    )

    /** true se [bloco] lançar IllegalArgumentException. */
    private fun recusa(bloco: () -> Unit): Boolean = try {
        bloco()
        false
    } catch (e: IllegalArgumentException) {
        true
    }

    @Test
    fun `RN06 - valor zero ou negativo e recusado`() {
        assertTrue(recusa { lancamento(valor = 0L) })
        assertTrue(recusa { lancamento(valor = -500L) })
    }

    @Test
    fun `categoria de receita nao pode ser usada em despesa`() {
        assertTrue(
            recusa {
                lancamento(tipo = TipoLancamento.DESPESA, categoria = CategoriaLancamento.VENDA_SAFRA)
            }
        )
        assertTrue(
            recusa {
                lancamento(tipo = TipoLancamento.RECEITA, categoria = CategoriaLancamento.ADUBO)
            }
        )
    }

    @Test
    fun `RN07 - data mais de 12 meses no futuro fica fora do limite`() {
        val hoje = LocalDate.of(2026, 10, 7)
        assertTrue(Lancamento.dataDentroDoLimite(hoje.plusMonths(12), hoje))
        assertFalse(Lancamento.dataDentroDoLimite(hoje.plusMonths(12).plusDays(1), hoje))
        assertTrue(Lancamento.dataDentroDoLimite(hoje.minusYears(3), hoje))
    }

    @Test
    fun `lancamento ja gravado no futuro continua legivel se o relogio voltar`() {
        // A data limite não é validada no init: um lançamento gravado com data distante (relógio do
        // aparelho atrasado na época) precisa ser lido do banco sem lançar exceção.
        val longe = lancamento(data = LocalDate.now().plusYears(2))
        assertEquals(LocalDate.now().plusYears(2), longe.data)
    }

    @Test
    fun `RN01 - lancamento sem propriedade e recusado`() {
        assertTrue(recusa { lancamento(propriedadeId = "  ") })
    }

    @Test
    fun `RN03 - receita soma e despesa subtrai no saldo`() {
        val receita = lancamento(
            tipo = TipoLancamento.RECEITA,
            categoria = CategoriaLancamento.VENDA_SAFRA,
            valor = 1_000_00L
        )
        val despesa = lancamento(valor = 300_00L)
        // RN06: o valor é sempre positivo; o sinal vem do tipo na hora de somar.
        assertEquals(300_00L, despesa.valorCentavos)
        assertEquals(700_00L, ResumoFinanceiro.totais(listOf(receita, despesa)).saldoCentavos)
    }

    @Test
    fun `RF06 - as 7 despesas da proposta e mais Outras despesas, por ultimo`() {
        val despesas = CategoriaLancamento.porTipo(TipoLancamento.DESPESA)
        assertEquals(8, despesas.size)
        assertTrue(despesas.all { it.tipo == TipoLancamento.DESPESA })
        assertEquals(CategoriaLancamento.OUTRAS_DESPESAS, despesas.last())
        // A primeira é a sugerida no formulário novo.
        assertEquals(CategoriaLancamento.ENERGIA, despesas.first())
    }

    @Test
    fun `cada lado tem a sua categoria Outras e elas nao se misturam`() {
        assertTrue(
            recusa {
                lancamento(tipo = TipoLancamento.RECEITA, categoria = CategoriaLancamento.OUTRAS_DESPESAS)
            }
        )
        assertTrue(
            recusa {
                lancamento(tipo = TipoLancamento.DESPESA, categoria = CategoriaLancamento.OUTROS)
            }
        )
    }

    @Test
    fun `propriedade valida nome, area e coordenadas`() {
        fun propriedade(nome: String = "Sítio", area: Double = 10.0, lat: Double = -23.5, lon: Double = -48.0) =
            Propriedade(nome = nome, cultura = "Milho", areaHectares = area, latitude = lat, longitude = lon)

        assertTrue(recusa { propriedade(nome = "") })
        assertTrue(recusa { propriedade(area = 0.0) })
        assertTrue(recusa { propriedade(lat = 91.0) })
        assertTrue(recusa { propriedade(lon = -181.0) })
        propriedade() // Válida: não deve lançar.
    }
}
