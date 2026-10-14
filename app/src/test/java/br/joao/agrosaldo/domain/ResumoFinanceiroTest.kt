package br.joao.agrosaldo.domain

import br.joao.agrosaldo.domain.financeiro.ResumoFinanceiro
import br.joao.agrosaldo.domain.financeiro.TotaisDoPeriodo
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regras de saldo e ranking de gastos testadas direto na função pura, sem ViewModel,
 * repositório nem coroutine: entra lista, sai número.
 */
class ResumoFinanceiroTest {

    private val outubro = YearMonth.of(2026, 10)

    private fun despesa(
        valor: Long,
        categoria: CategoriaLancamento = CategoriaLancamento.ADUBO,
        data: LocalDate = LocalDate.of(2026, 10, 5),
        propriedadeId: String = "p1"
    ) = Lancamento(
        propriedadeId = propriedadeId,
        tipo = TipoLancamento.DESPESA,
        categoria = categoria,
        valorCentavos = valor,
        data = data
    )

    private fun receita(valor: Long, data: LocalDate = LocalDate.of(2026, 10, 5), propriedadeId: String = "p1") = Lancamento(
        propriedadeId = propriedadeId,
        tipo = TipoLancamento.RECEITA,
        categoria = CategoriaLancamento.VENDA_SAFRA,
        valorCentavos = valor,
        data = data
    )

    private fun propriedade(id: String, nome: String = id) =
        Propriedade(id = id, nome = nome, cultura = "Milho", areaHectares = 10.0, latitude = -21.0, longitude = -47.0)

    // Totais e saldo (RN03)

    @Test
    fun `RN03 - saldo e receitas menos despesas`() {
        val totais = ResumoFinanceiro.totais(listOf(receita(100_00), despesa(30_00), despesa(20_50)))

        assertEquals(100_00L, totais.receitasCentavos)
        assertEquals(50_50L, totais.despesasCentavos)
        assertEquals(49_50L, totais.saldoCentavos)
        assertTrue(totais.temMovimento)
    }

    @Test
    fun `saldo pode ser negativo`() {
        val totais = ResumoFinanceiro.totais(listOf(receita(10_00), despesa(25_00)))
        assertEquals(-15_00L, totais.saldoCentavos)
    }

    @Test
    fun `lista vazia da tudo zero e barra meio a meio`() {
        val totais = ResumoFinanceiro.totais(emptyList())

        assertEquals(TotaisDoPeriodo.VAZIO, totais)
        assertFalse(totais.temMovimento)
        assertEquals(0.5f, totais.proporcaoReceitas, 0.0001f)
    }

    @Test
    fun `proporcao de receitas e receitas sobre o total movimentado`() {
        val totais = ResumoFinanceiro.totais(listOf(receita(75_00), despesa(25_00)))
        assertEquals(0.75f, totais.proporcaoReceitas, 0.0001f)
    }

    @Test
    fun `centavos nao se perdem na soma`() {
        val totais = ResumoFinanceiro.totais(List(3) { despesa(50) })
        assertEquals(1_50L, totais.despesasCentavos)
    }

    // Recorte do mês

    @Test
    fun `resumo do mes ignora outros meses e outras propriedades`() {
        val lancamentos = listOf(
            receita(1_000_00),
            despesa(200_00),
            despesa(999_00, data = LocalDate.of(2026, 9, 30)), // setembro
            despesa(999_00, data = LocalDate.of(2026, 11, 1)), // novembro
            receita(999_00, propriedadeId = "p2") // outra propriedade
        )

        val totais = ResumoFinanceiro.resumoDoMes(lancamentos, "p1", outubro)

        assertEquals(1_000_00L, totais.receitasCentavos)
        assertEquals(200_00L, totais.despesasCentavos)
    }

    @Test
    fun `primeiro e ultimo dia entram no mes`() {
        val lancamentos = listOf(
            despesa(1_00, data = LocalDate.of(2026, 10, 1)),
            despesa(2_00, data = LocalDate.of(2026, 10, 31))
        )
        assertEquals(2, ResumoFinanceiro.doMes(lancamentos, "p1", outubro).size)
    }

    // Ranking de gastos

    @Test
    fun `ranking vem do maior gasto para o menor e soma a mesma categoria`() {
        val ranking = ResumoFinanceiro.rankingDeGastos(
            listOf(
                despesa(100_00, CategoriaLancamento.ADUBO),
                despesa(300_00, CategoriaLancamento.ENERGIA),
                despesa(150_00, CategoriaLancamento.ADUBO),
                despesa(50_00, CategoriaLancamento.COMBUSTIVEL)
            ),
            quantos = 3
        )

        assertEquals(
            listOf(CategoriaLancamento.ENERGIA, CategoriaLancamento.ADUBO, CategoriaLancamento.COMBUSTIVEL),
            ranking.map { it.categoria }
        )
        assertEquals(250_00L, ranking[1].totalCentavos)
    }

    @Test
    fun `proporcao e relativa ao maior gasto, entao a primeira barra fica cheia`() {
        val ranking = ResumoFinanceiro.rankingDeGastos(
            listOf(despesa(200_00, CategoriaLancamento.ENERGIA), despesa(50_00, CategoriaLancamento.ADUBO)),
            quantos = 3
        )

        assertEquals(1f, ranking[0].proporcao, 0.0001f)
        assertEquals(0.25f, ranking[1].proporcao, 0.0001f)
    }

    @Test
    fun `ranking corta no numero pedido`() {
        val todas = CategoriaLancamento.porTipo(TipoLancamento.DESPESA)
        val lancamentos = todas.mapIndexed { i, categoria -> despesa((i + 1) * 10_00L, categoria) }

        assertEquals(3, ResumoFinanceiro.rankingDeGastos(lancamentos, quantos = 3).size)
        assertEquals(5, ResumoFinanceiro.rankingDeGastos(lancamentos, quantos = 5).size)
        assertTrue(ResumoFinanceiro.rankingDeGastos(lancamentos, quantos = 0).isEmpty())
    }

    @Test
    fun `receita nao entra no ranking de gastos`() {
        val ranking = ResumoFinanceiro.rankingDeGastos(listOf(receita(1_000_00), despesa(10_00)), quantos = 3)
        assertEquals(listOf(CategoriaLancamento.ADUBO), ranking.map { it.categoria })
    }

    @Test
    fun `empate no ranking segue a ordem das categorias`() {
        val ranking = ResumoFinanceiro.rankingDeGastos(
            listOf(despesa(10_00, CategoriaLancamento.MANUTENCAO), despesa(10_00, CategoriaLancamento.ENERGIA)),
            quantos = 3
        )
        assertEquals(listOf(CategoriaLancamento.ENERGIA, CategoriaLancamento.MANUTENCAO), ranking.map { it.categoria })
    }

    @Test
    fun `sem despesas o ranking fica vazio`() {
        assertTrue(ResumoFinanceiro.rankingDeGastos(listOf(receita(10_00)), quantos = 3).isEmpty())
    }

    // Propriedade atual

    @Test
    fun `propriedade atual e a selecionada`() {
        val lista = listOf(propriedade("a"), propriedade("b"))
        assertEquals("b", ResumoFinanceiro.propriedadeAtual(lista, "b")?.id)
    }

    @Test
    fun `selecao que nao existe mais cai na primeira`() {
        val lista = listOf(propriedade("a"), propriedade("b"))
        assertEquals("a", ResumoFinanceiro.propriedadeAtual(lista, "excluida")?.id)
        assertEquals("a", ResumoFinanceiro.propriedadeAtual(lista, null)?.id)
    }

    @Test
    fun `sem propriedades nao ha propriedade atual`() {
        assertNull(ResumoFinanceiro.propriedadeAtual(emptyList(), "a"))
    }
}
