package br.joao.agrosaldo.feature.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.pertenceAo
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Uma fatia do gasto do mês, já com a proporção calculada para a barra. */
data class GastoPorCategoria(
    val categoria: CategoriaLancamento,
    val totalCentavos: Long,
    val proporcao: Float
)

data class InicioUiState(
    val carregando: Boolean = true,
    val temPropriedade: Boolean = false,
    val nomePropriedade: String = "",
    val propriedades: List<Propriedade> = emptyList(),
    val mes: YearMonth = YearMonth.now(),
    val saldoCentavos: Long = 0L,
    val receitasCentavos: Long = 0L,
    val despesasCentavos: Long = 0L,
    /** Fatia da barra que representa entradas, de 0 a 1. */
    val proporcaoReceitas: Float = 0.5f,
    val gastosPorCategoria: List<GastoPorCategoria> = emptyList(),
    val ultimosLancamentos: List<Lancamento> = emptyList()
)

/**
 * ViewModel da tela inicial.
 *
 * Aqui fica claro por que o ViewModel existe. Nada do que essa tela mostra
 * está guardado em lugar nenhum: saldo, proporção da barra e ranking de
 * categorias são todos derivados dos lançamentos, na hora.
 *
 * Fazer essa conta dentro do composable seria um erro caro: ela rodaria de
 * novo a cada recomposição, inclusive nas que só mudaram um pixel de scroll.
 * Aqui ela roda uma vez por mudança de dado.
 *
 * Nota para a fase 5: com o Room, a soma vira SUM no SQL. Com poucos milhares
 * de lançamentos em memória a diferença não aparece, mas o banco faz melhor.
 */
class InicioViewModel(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository
) : ViewModel() {

    private val _mes = MutableStateFlow(YearMonth.now())

    private val _uiState = MutableStateFlow(InicioUiState())
    val uiState: StateFlow<InicioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                lancamentoRepository.observarTodos(),
                propriedadeRepository.observarTodas(),
                SelecaoAtual.propriedadeId,
                _mes
            ) { lancamentos, propriedades, selecionadaId, mes ->
                montarEstado(lancamentos, propriedades, selecionadaId, mes)
            }.collect { estado ->
                _uiState.value = estado
            }
        }
    }

    private fun montarEstado(
        todosLancamentos: List<Lancamento>,
        propriedades: List<Propriedade>,
        selecionadaId: String?,
        mes: YearMonth
    ): InicioUiState {

        val propriedade = propriedades.firstOrNull { it.id == selecionadaId }
            ?: propriedades.firstOrNull()

        if (propriedade == null) {
            return InicioUiState(carregando = false, temPropriedade = false, mes = mes)
        }

        val doMes = todosLancamentos
            .filter { it.propriedadeId == propriedade.id && it.data.pertenceAo(mes) }

        val receitas = doMes.filter { it.tipo == TipoLancamento.RECEITA }
            .sumOf { it.valorCentavos }
        val despesas = doMes.filter { it.tipo == TipoLancamento.DESPESA }
            .sumOf { it.valorCentavos }

        val movimentado = receitas + despesas
        // Sem movimento no mês, a barra fica meio a meio em vez de sumir.
        val proporcao = if (movimentado == 0L) 0.5f else receitas.toFloat() / movimentado

        // Ranking de gastos: agrupa por categoria, soma, ordena e pega as 3
        // maiores. A proporção é relativa à maior, não ao total — assim a
        // primeira barra sempre preenche e a comparação fica legível.
        val porCategoria = doMes
            .filter { it.tipo == TipoLancamento.DESPESA }
            .groupBy { it.categoria }
            .map { (categoria, itens) -> categoria to itens.sumOf { it.valorCentavos } }
            .sortedByDescending { it.second }
            .take(3)

        val maiorGasto = porCategoria.firstOrNull()?.second ?: 1L

        val gastos = porCategoria.map { (categoria, total) ->
            GastoPorCategoria(
                categoria = categoria,
                totalCentavos = total,
                proporcao = if (maiorGasto == 0L) 0f else total.toFloat() / maiorGasto
            )
        }

        return InicioUiState(
            carregando = false,
            temPropriedade = true,
            nomePropriedade = propriedade.nome,
            propriedades = propriedades,
            mes = mes,
            saldoCentavos = receitas - despesas,
            receitasCentavos = receitas,
            despesasCentavos = despesas,
            proporcaoReceitas = proporcao,
            gastosPorCategoria = gastos,
            ultimosLancamentos = doMes.sortedByDescending { it.data }.take(4)
        )
    }

    fun aoAvancarMes() { _mes.value = _mes.value.plusMonths(1) }
    fun aoVoltarMes() { _mes.value = _mes.value.minusMonths(1) }
    fun aoSelecionarPropriedade(id: String) = SelecaoAtual.selecionar(id)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InicioViewModel(
                    lancamentoRepository = Repositorios.lancamentos,
                    propriedadeRepository = Repositorios.propriedades
                )
            }
        }
    }
}
