package br.joao.agrosaldo.domain.financeiro

import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import java.time.YearMonth

/**
 * Receitas e despesas de um período, em centavos.
 *
 * O saldo é receitas − despesas. [proporcaoReceitas] alimenta a barra
 * receitas × despesas do Início: 0.5 quando não há movimento, para a barra ficar neutra.
 */
data class TotaisDoPeriodo(val receitasCentavos: Long = 0L, val despesasCentavos: Long = 0L) {
    val saldoCentavos: Long get() = receitasCentavos - despesasCentavos

    val temMovimento: Boolean get() = receitasCentavos > 0L || despesasCentavos > 0L

    /** Receitas / (receitas + despesas), em 0..1. */
    val proporcaoReceitas: Float
        get() {
            val movimentado = receitasCentavos + despesasCentavos
            return if (movimentado == 0L) PROPORCAO_SEM_MOVIMENTO else receitasCentavos.toFloat() / movimentado
        }

    companion object {
        val VAZIO = TotaisDoPeriodo()
        private const val PROPORCAO_SEM_MOVIMENTO = 0.5f
    }
}

/**
 * Total de despesas de uma categoria. [proporcao] em 0..1 é relativa à MAIOR categoria
 * (não ao total), para a primeira barra do ranking ficar sempre cheia.
 */
data class GastoPorCategoria(val categoria: CategoriaLancamento, val totalCentavos: Long, val proporcao: Float)

/**
 * Regras de negócio das contas do produtor: totais, saldo e ranking de gastos.
 *
 * São funções puras — recebem listas e devolvem valores, sem banco, sem Android e sem
 * relógio. Por isso o mesmo cálculo serve ao Início, à lista de lançamentos, ao
 * Histórico e à lista de propriedades, e é testado direto no JVM (`ResumoFinanceiroTest`).
 * Antes de existir este arquivo, cada ViewModel tinha a sua versão da mesma conta.
 */
object ResumoFinanceiro {

    /** Soma receitas e despesas de [lancamentos], sem filtrar nada. */
    fun totais(lancamentos: Iterable<Lancamento>): TotaisDoPeriodo {
        var receitas = 0L
        var despesas = 0L
        for (l in lancamentos) {
            when (l.tipo) {
                TipoLancamento.RECEITA -> receitas += l.valorCentavos
                TipoLancamento.DESPESA -> despesas += l.valorCentavos
            }
        }
        return TotaisDoPeriodo(receitas, despesas)
    }

    /** Lançamentos da propriedade [propriedadeId] com data dentro de [mes]. */
    fun doMes(lancamentos: Iterable<Lancamento>, propriedadeId: String, mes: YearMonth): List<Lancamento> =
        lancamentos.filter { it.propriedadeId == propriedadeId && YearMonth.from(it.data) == mes }

    /** Totais do mês [mes] da propriedade [propriedadeId]. */
    fun resumoDoMes(lancamentos: Iterable<Lancamento>, propriedadeId: String, mes: YearMonth): TotaisDoPeriodo =
        totais(doMes(lancamentos, propriedadeId, mes))

    /**
     * As [quantos] categorias de despesa com maior total, da maior para a menor.
     * Receitas são ignoradas. Empate no total mantém a ordem das categorias no enum,
     * para o ranking não "pular" entre duas recomposições iguais.
     */
    fun rankingDeGastos(lancamentos: Iterable<Lancamento>, quantos: Int): List<GastoPorCategoria> {
        require(quantos >= 0) { "quantos não pode ser negativo." }
        val porCategoria = lancamentos
            .filter { it.tipo == TipoLancamento.DESPESA }
            .groupBy { it.categoria }
            .map { (categoria, itens) -> categoria to itens.sumOf { it.valorCentavos } }
            .sortedWith(compareByDescending<Pair<CategoriaLancamento, Long>> { it.second }.thenBy { it.first.ordinal })
            .take(quantos)

        val maior = porCategoria.firstOrNull()?.second ?: 0L
        return porCategoria.map { (categoria, total) ->
            GastoPorCategoria(
                categoria = categoria,
                totalCentavos = total,
                proporcao = if (maior == 0L) 0f else total.toFloat() / maior
            )
        }
    }

    /**
     * Propriedade em que as telas trabalham: a de id [idSelecionado] ou, se ela não
     * existir mais (excluída, ou de outra conta), a primeira da lista. null se a lista
     * estiver vazia.
     */
    fun propriedadeAtual(propriedades: List<Propriedade>, idSelecionado: String?): Propriedade? =
        propriedades.firstOrNull { it.id == idSelecionado } ?: propriedades.firstOrNull()
}
