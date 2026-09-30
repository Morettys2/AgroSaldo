package br.joao.agrosaldo.feature.lancamento

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.pertenceAo
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.data.SelecaoAtual
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

/** Filtro por tipo (RF09). */
enum class FiltroLancamento { TUDO, DESPESAS, RECEITAS }

data class LancamentoListaUiState(
    val carregando: Boolean = true,
    val nomePropriedade: String = "",
    val temPropriedade: Boolean = false,
    val mes: YearMonth = YearMonth.now(),
    val filtro: FiltroLancamento = FiltroLancamento.TUDO,
    val lancamentos: List<Lancamento> = emptyList(),
    val quantidade: Int = 0,
    val saldoCentavos: Long = 0L,
    val propriedades: List<Propriedade> = emptyList()
)

/**
 * ViewModel da lista de lançamentos.
 *
 * O ponto interessante aqui é o `combine`: cinco fontes que mudam por conta
 * própria — lançamentos, propriedades, propriedade selecionada, mês e filtro —
 * viram um único estado de tela.
 *
 * Sem ele, cada uma dessas mudanças exigiria recalcular a tela na mão e as
 * combinações ficariam fáceis de esquecer. Com ele, mexer em qualquer uma
 * refaz o estado inteiro automaticamente.
 */
class LancamentoListaViewModel(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository
) : ViewModel() {

    private val _mes = MutableStateFlow(YearMonth.now())
    private val _filtro = MutableStateFlow(FiltroLancamento.TUDO)

    private val _uiState = MutableStateFlow(LancamentoListaUiState())
    val uiState: StateFlow<LancamentoListaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                lancamentoRepository.observarTodos(),
                propriedadeRepository.observarTodas(),
                SelecaoAtual.propriedadeId,
                _mes,
                _filtro
            ) { lancamentos, propriedades, selecionadaId, mes, filtro ->
                montarEstado(lancamentos, propriedades, selecionadaId, mes, filtro)
            }.collect { estado ->
                _uiState.value = estado
            }
        }
    }

    private fun montarEstado(
        todosLancamentos: List<Lancamento>,
        propriedades: List<Propriedade>,
        selecionadaId: String?,
        mes: YearMonth,
        filtro: FiltroLancamento
    ): LancamentoListaUiState {

        // Se nada foi escolhido ainda, assume a primeira da lista.
        val propriedade = propriedades.firstOrNull { it.id == selecionadaId }
            ?: propriedades.firstOrNull()

        if (propriedade == null) {
            return LancamentoListaUiState(
                carregando = false,
                temPropriedade = false,
                mes = mes,
                filtro = filtro
            )
        }

        val doMes = todosLancamentos
            .filter { it.propriedadeId == propriedade.id && it.data.pertenceAo(mes) }

        val filtrados = when (filtro) {
            FiltroLancamento.TUDO -> doMes
            FiltroLancamento.DESPESAS -> doMes.filter { it.tipo == TipoLancamento.DESPESA }
            FiltroLancamento.RECEITAS -> doMes.filter { it.tipo == TipoLancamento.RECEITA }
        }.sortedByDescending { it.data }

        return LancamentoListaUiState(
            carregando = false,
            nomePropriedade = propriedade.nome,
            temPropriedade = true,
            mes = mes,
            filtro = filtro,
            lancamentos = filtrados,
            quantidade = filtrados.size,
            // O saldo considera o mês inteiro, não o filtro: filtrar por
            // despesas não deveria fazer o saldo do mês parecer negativo.
            saldoCentavos = doMes.sumOf { it.valorComSinal },
            propriedades = propriedades
        )
    }

    fun aoAvancarMes() { _mes.value = _mes.value.plusMonths(1) }
    fun aoVoltarMes() { _mes.value = _mes.value.minusMonths(1) }
    fun aoMudarFiltro(filtro: FiltroLancamento) { _filtro.value = filtro }
    fun aoSelecionarPropriedade(id: String) = SelecaoAtual.selecionar(id)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LancamentoListaViewModel(
                    lancamentoRepository = Repositorios.lancamentos,
                    propriedadeRepository = Repositorios.propriedades
                )
            }
        }
    }
}
