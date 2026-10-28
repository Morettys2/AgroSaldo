package br.joao.agrosaldo.feature.historico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.repetirAposErro
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.financeiro.GastoPorCategoria
import br.joao.agrosaldo.domain.financeiro.ResumoFinanceiro
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Totais de um mês, em centavos. */
data class MesDoHistorico(
    val mes: YearMonth,
    val receitasCentavos: Long,
    val despesasCentavos: Long,
    /** Mês posterior ao atual (exibido esmaecido no gráfico). */
    val futuro: Boolean
) {
    val saldoCentavos: Long get() = receitasCentavos - despesasCentavos
    val temMovimento: Boolean get() = receitasCentavos > 0L || despesasCentavos > 0L
}

data class HistoricoUiState(
    val carregando: Boolean = true,
    val temPropriedade: Boolean = false,
    val nomePropriedade: String = "",
    val propriedades: List<Propriedade> = emptyList(),
    /** Sem valor padrão: vem do mês escolhido, que segue o relógio injetado. */
    val ano: Int,
    /** Sempre 12 itens, de janeiro a dezembro. */
    val meses: List<MesDoHistorico> = emptyList(),
    val receitasCentavos: Long = 0L,
    val despesasCentavos: Long = 0L,
    /** Maior receita ou despesa mensal do ano; define a escala (100%) do gráfico. */
    val maiorValorMensal: Long = 0L,
    /** Entre os meses não futuros com movimento. */
    val melhorMes: MesDoHistorico? = null,
    /** Nulo se houver menos de dois meses não futuros com movimento ou se o menor saldo empatar com o maior. */
    val piorMes: MesDoHistorico? = null,
    val gastosDoAno: List<GastoPorCategoria> = emptyList(),
    val podeAvancar: Boolean = false,
    /** Falha ao ler os dados; a leitura é tentada de novo sozinha e a mensagem some quando volta. */
    val erro: String? = null
) {
    val saldoCentavos: Long get() = receitasCentavos - despesasCentavos
    val temMovimento: Boolean get() = meses.any { it.temMovimento }

    /** Meses da lista "mês a mês": os futuros só aparecem se tiverem lançamento agendado. */
    val mesesNaLista: List<MesDoHistorico> get() = meses.filter { !it.futuro || it.temMovimento }
}

/**
 * Histórico anual da propriedade selecionada, por ano-calendário (alinhado ao
 * fechamento contábil e ao IR rural), não pelos últimos 12 meses. Os totais são
 * derivados dos lançamentos a cada emissão; nada é persistido.
 */
@HiltViewModel
class HistoricoViewModel @Inject constructor(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository,
    /** Propriedade e mês escolhidos, compartilhados com as outras telas. */
    private val selecao: SelecaoAtual,
    private val relogio: Clock
) : ViewModel() {

    private fun hoje(): LocalDate = LocalDate.now(relogio)

    // Ano inicial: o do mês selecionado globalmente.
    private val anoEscolhido = MutableStateFlow(selecao.mes.value.year)

    private val _uiState = MutableStateFlow(HistoricoUiState(ano = anoEscolhido.value))
    val uiState: StateFlow<HistoricoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                lancamentoRepository.observarTodos(),
                propriedadeRepository.observarTodas(),
                selecao.propriedadeId,
                anoEscolhido
            ) { lancamentos, propriedades, selecionadaId, ano ->
                montarEstado(lancamentos, propriedades, selecionadaId, ano)
            }.repetirAposErro { erro ->
                _uiState.update { it.copy(carregando = false, erro = erro.mensagemParaUsuario(MENSAGEM_ERRO)) }
            }.collect { _uiState.value = it }
        }
    }

    private fun montarEstado(
        todosLancamentos: List<Lancamento>,
        propriedades: List<Propriedade>,
        selecionadaId: String?,
        ano: Int
    ): HistoricoUiState {
        val propriedade = ResumoFinanceiro.propriedadeAtual(propriedades, selecionadaId)
        val podeAvancar = ano < anoMaximo()

        if (propriedade == null) {
            return HistoricoUiState(carregando = false, ano = ano, podeAvancar = podeAvancar)
        }

        val doAno = todosLancamentos.filter { it.propriedadeId == propriedade.id && it.data.year == ano }
        val mesAtual = YearMonth.from(hoje())

        val porMes = doAno.groupBy { YearMonth.from(it.data) }
        val meses = (1..MESES_DO_ANO).map { numero ->
            val mes = YearMonth.of(ano, numero)
            val totais = ResumoFinanceiro.totais(porMes[mes].orEmpty())
            MesDoHistorico(
                mes = mes,
                receitasCentavos = totais.receitasCentavos,
                despesasCentavos = totais.despesasCentavos,
                futuro = mes.isAfter(mesAtual)
            )
        }

        // Mês futuro só tem lançamentos agendados; não entra na comparação.
        val comMovimento = meses.filter { it.temMovimento && !it.futuro }
        val melhor = comMovimento.maxByOrNull { it.saldoCentavos }
        val pior = if (comMovimento.size >= 2) {
            comMovimento.minByOrNull { it.saldoCentavos }?.takeIf { melhor != null && it.saldoCentavos < melhor.saldoCentavos }
        } else {
            null
        }

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
            gastosDoAno = ResumoFinanceiro.rankingDeGastos(doAno, quantos = MAXIMO_CATEGORIAS),
            podeAvancar = podeAvancar
        )
    }

    /** Ano atual + 1, pois lançamentos podem ter até 12 meses à frente. */
    private fun anoMaximo(): Int = hoje().year + 1

    fun aoVoltarAno() {
        anoEscolhido.value = anoEscolhido.value - 1
    }

    fun aoAvancarAno() {
        if (anoEscolhido.value < anoMaximo()) anoEscolhido.value = anoEscolhido.value + 1
    }

    fun aoSelecionarPropriedade(id: String) = selecao.selecionar(id)

    /** Seleciona o mês em [SelecaoAtual] antes de a tela navegar para Lançamentos. */
    fun aoAbrirMes(mes: YearMonth) = selecao.selecionarMes(mes)

    companion object {
        const val MAXIMO_CATEGORIAS = 5
        const val MESES_DO_ANO = 12

        const val MENSAGEM_ERRO = "Não foi possível carregar o histórico. Tentando de novo…"
    }
}
