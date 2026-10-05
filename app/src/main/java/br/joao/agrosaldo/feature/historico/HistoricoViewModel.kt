package br.joao.agrosaldo.feature.historico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import java.time.LocalDate
import java.time.YearMonth

/** Um mês do gráfico: quanto entrou e quanto saiu. */
data class MesDoHistorico(
    val mes: YearMonth,
    val receitasCentavos: Long,
    val despesasCentavos: Long,
    /** Mês que ainda não chegou. Aparece apagado no gráfico. */
    val futuro: Boolean
) {
    val saldoCentavos: Long get() = receitasCentavos - despesasCentavos
    val temMovimento: Boolean get() = receitasCentavos > 0L || despesasCentavos > 0L
}

/** Uma categoria de gasto somada no ano, com a barra relativa à maior. */
data class GastoDoAno(
    val categoria: CategoriaLancamento,
    val totalCentavos: Long,
    val proporcao: Float
)

data class HistoricoUiState(
    val carregando: Boolean = true,
    val temPropriedade: Boolean = false,
    val nomePropriedade: String = "",
    val propriedades: List<Propriedade> = emptyList(),
    val ano: Int = LocalDate.now().year,
    /** Sempre os 12 meses, de janeiro a dezembro. */
    val meses: List<MesDoHistorico> = emptyList(),
    val receitasCentavos: Long = 0L,
    val despesasCentavos: Long = 0L,
    /** O maior valor de um mês (entrada ou saída): a altura cheia do gráfico. */
    val maiorValorMensal: Long = 0L,
    val melhorMes: MesDoHistorico? = null,
    /** Só existe quando há pelo menos dois meses com movimento. */
    val piorMes: MesDoHistorico? = null,
    val gastosDoAno: List<GastoDoAno> = emptyList(),
    val podeAvancar: Boolean = false
) {
    val saldoCentavos: Long get() = receitasCentavos - despesasCentavos
    val temMovimento: Boolean get() = meses.any { it.temMovimento }
}

/**
 * Histórico do ano (RF10): os 12 meses de uma propriedade lado a lado.
 *
 * Por que ano-calendário, e não "últimos 12 meses": é como o produtor fecha
 * as contas com o contador e declara o Imposto de Renda rural. "Quanto o
 * sítio deu em 2026?" tem resposta direta aqui.
 *
 * Como nas outras telas, nada é guardado: tudo sai dos lançamentos na hora,
 * então um lançamento novo já aparece no gráfico.
 */
class HistoricoViewModel(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository,
    private val hoje: () -> LocalDate = LocalDate::now
) : ViewModel() {

    // Abre no ano do mês que a pessoa estava olhando no Início.
    private val _ano = MutableStateFlow(SelecaoAtual.mes.value.year)

    private val _uiState = MutableStateFlow(HistoricoUiState(ano = _ano.value))
    val uiState: StateFlow<HistoricoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                lancamentoRepository.observarTodos(),
                propriedadeRepository.observarTodas(),
                SelecaoAtual.propriedadeId,
                _ano
            ) { lancamentos, propriedades, selecionadaId, ano ->
                montarEstado(lancamentos, propriedades, selecionadaId, ano)
            }.collect { _uiState.value = it }
        }
    }

    private fun montarEstado(
        todosLancamentos: List<Lancamento>,
        propriedades: List<Propriedade>,
        selecionadaId: String?,
        ano: Int
    ): HistoricoUiState {
        val propriedade = propriedades.firstOrNull { it.id == selecionadaId } ?: propriedades.firstOrNull()
        val podeAvancar = ano < anoMaximo()

        if (propriedade == null) {
            return HistoricoUiState(carregando = false, ano = ano, podeAvancar = podeAvancar)
        }

        val doAno = todosLancamentos.filter { it.propriedadeId == propriedade.id && it.data.year == ano }
        val mesAtual = YearMonth.from(hoje())

        // Agrupa uma vez só, em vez de filtrar a lista inteira 12 vezes.
        val porMes = doAno.groupBy { YearMonth.from(it.data) }
        val meses = (1..12).map { numero ->
            val mes = YearMonth.of(ano, numero)
            val itens = porMes[mes].orEmpty()
            MesDoHistorico(
                mes = mes,
                receitasCentavos = itens.filter { it.ehReceita }.sumOf { it.valorCentavos },
                despesasCentavos = itens.filter { !it.ehReceita }.sumOf { it.valorCentavos },
                futuro = mes.isAfter(mesAtual)
            )
        }

        val comMovimento = meses.filter { it.temMovimento }
        val melhor = comMovimento.maxByOrNull { it.saldoCentavos }
        // "Mês mais fraco" só faz sentido comparando: com um mês só, ele seria o melhor e o pior.
        val pior = if (comMovimento.size >= 2) {
            // Empate com o melhor: não há "mais fraco" para mostrar.
            comMovimento.minByOrNull { it.saldoCentavos }?.takeIf { melhor != null && it.saldoCentavos < melhor.saldoCentavos }
        } else {
            null
        }

        val gastos = doAno
            .filter { it.tipo == TipoLancamento.DESPESA }
            .groupBy { it.categoria }
            .map { (categoria, itens) -> categoria to itens.sumOf { it.valorCentavos } }
            .sortedByDescending { it.second }
            .take(MAXIMO_CATEGORIAS)
        val maiorGasto = gastos.firstOrNull()?.second ?: 0L

        return HistoricoUiState(
            carregando = false,
            temPropriedade = true,
            nomePropriedade = propriedade.nome,
            propriedades = propriedades,
            ano = ano,
            meses = meses,
            receitasCentavos = meses.sumOf { it.receitasCentavos },
            despesasCentavos = meses.sumOf { it.despesasCentavos },
            maiorValorMensal = meses.maxOfOrNull { maxOf(it.receitasCentavos, it.despesasCentavos) } ?: 0L,
            melhorMes = melhor,
            piorMes = pior,
            gastosDoAno = gastos.map { (categoria, total) ->
                GastoDoAno(categoria, total, if (maiorGasto == 0L) 0f else total.toFloat() / maiorGasto)
            },
            podeAvancar = podeAvancar
        )
    }

    /** Até o ano que vem: dá para lançar até 12 meses à frente (RN07). */
    private fun anoMaximo(): Int = hoje().year + 1

    fun aoVoltarAno() {
        _ano.value = _ano.value - 1
    }

    fun aoAvancarAno() {
        if (_ano.value < anoMaximo()) _ano.value = _ano.value + 1
    }

    fun aoSelecionarPropriedade(id: String) = SelecaoAtual.selecionar(id)

    /** Deixa o mês escolhido para a aba Lançamentos, que a tela abre em seguida. */
    fun aoAbrirMes(mes: YearMonth) = SelecaoAtual.selecionarMes(mes)

    companion object {
        const val MAXIMO_CATEGORIAS = 5

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HistoricoViewModel(
                    lancamentoRepository = Repositorios.lancamentos,
                    propriedadeRepository = Repositorios.propriedades
                )
            }
        }
    }
}
