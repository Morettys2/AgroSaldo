package br.joao.agrosaldo.feature.lancamento

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.pertenceAo
import br.joao.agrosaldo.core.util.rotuloRelativo
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.CoroutineContext

/** Filtro por tipo (RF09). */
enum class FiltroLancamento { TUDO, DESPESAS, RECEITAS }

/** Lançamentos de um dia, com rótulo relativo ("Hoje", "Ontem", "2 de outubro"). */
data class GrupoDoDia(
    val data: LocalDate,
    val rotulo: String,
    val lancamentos: List<Lancamento>
)

data class LancamentoListaUiState(
    val carregando: Boolean = true,
    val nomePropriedade: String = "",
    val temPropriedade: Boolean = false,
    val mes: YearMonth = YearMonth.now(),
    val filtro: FiltroLancamento = FiltroLancamento.TUDO,
    /** Filtro de categoria; null = todas. */
    val categoria: CategoriaLancamento? = null,
    /** Categorias com lançamento no recorte atual (mais a selecionada), para os chips. */
    val categoriasDoMes: List<CategoriaLancamento> = emptyList(),
    val lancamentos: List<Lancamento> = emptyList(),
    val quantidade: Int = 0,
    /** Soma com sinal dos lançamentos filtrados, em centavos. Exibida só com filtro ativo. */
    val totalFiltradoCentavos: Long = 0L,
    val saldoCentavos: Long = 0L,
    /** Totais do mês inteiro (ignoram filtros), em centavos. */
    val receitasCentavos: Long = 0L,
    val despesasCentavos: Long = 0L,
    val propriedades: List<Propriedade> = emptyList(),
    /** [lancamentos] agrupados por dia, do mais recente ao mais antigo. */
    val grupos: List<GrupoDoDia> = emptyList(),
    /** Meses do seletor do cabeçalho, em ordem crescente. */
    val meses: List<YearMonth> = emptyList(),
    /** Texto de busca; quando preenchido, a lista ignora o mês e busca em todos. */
    val busca: String = ""
) {
    val temFiltro: Boolean get() = filtro != FiltroLancamento.TUDO || categoria != null
    val buscando: Boolean get() = busca.isNotBlank()
}

/**
 * Lançamento com a descrição já normalizada para a busca, calculada uma vez por emissão do
 * repositório e não a cada tecla.
 */
private class LancamentoParaBusca(val lancamento: Lancamento) {
    val descricao: String = LancamentoListaViewModel.normalizar(lancamento.descricao)
}

/** Filtros agrupados para atualização atômica. */
private data class Filtros(
    val tipo: FiltroLancamento = FiltroLancamento.TUDO,
    val categoria: CategoriaLancamento? = null,
    val busca: String = ""
)

/**
 * Lista de lançamentos da propriedade e mês selecionados em [SelecaoAtual], com filtros
 * por tipo/categoria e busca textual. O estado é recalculado por inteiro a cada emissão
 * de qualquer fonte (lançamentos, propriedades, seleção, mês, filtros).
 *
 * @param processamento contexto em que o estado é montado, fora da main thread.
 */
class LancamentoListaViewModel(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository,
    private val hoje: () -> LocalDate = LocalDate::now,
    private val processamento: CoroutineContext = Dispatchers.Default
) : ViewModel() {

    private val _filtros = MutableStateFlow(Filtros())

    /** Incrementado ao retomar a tela para recalcular rótulos relativos após a meia-noite. */
    private val _voltas = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(LancamentoListaUiState())
    val uiState: StateFlow<LancamentoListaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                lancamentoRepository.observarTodos().map { lista -> lista.map(::LancamentoParaBusca) },
                propriedadeRepository.observarTodas(),
                SelecaoAtual.propriedadeId,
                SelecaoAtual.mes,
                _filtros.combine(_voltas) { filtros, _ -> filtros }
            ) { lancamentos, propriedades, selecionadaId, mes, filtros ->
                montarEstado(lancamentos, propriedades, selecionadaId, mes, filtros)
            }
                .flowOn(processamento)
                .collect { estado -> _uiState.value = estado }
        }

        // Mudança de mês (vinda de outra tela) limpa a busca, que ocultaria o mês escolhido.
        // drop(1) ignora o valor inicial do StateFlow.
        viewModelScope.launch {
            SelecaoAtual.mes.drop(1).collect { aoFecharBusca() }
        }
    }

    /** Chamado em ON_RESUME: acompanha a virada do mês e recalcula os rótulos relativos ("Hoje"). */
    fun aoVoltarParaATela() {
        SelecaoAtual.acompanharMesCorrente(YearMonth.from(hoje()))
        _voltas.update { it + 1 }
    }

    private fun montarEstado(
        todosLancamentos: List<LancamentoParaBusca>,
        propriedades: List<Propriedade>,
        selecionadaId: String?,
        mes: YearMonth,
        filtros: Filtros
    ): LancamentoListaUiState {

        // Sem seleção válida, usa a primeira propriedade.
        val propriedade = propriedades.firstOrNull { it.id == selecionadaId }
            ?: propriedades.firstOrNull()

        val dataDeHoje = hoje()

        if (propriedade == null) {
            return LancamentoListaUiState(
                carregando = false,
                temPropriedade = false,
                mes = mes,
                filtro = filtros.tipo,
                categoria = filtros.categoria,
                busca = filtros.busca
            )
        }

        val daPropriedade = todosLancamentos.filter { it.lancamento.propriedadeId == propriedade.id }
        val doMes = daPropriedade.map { it.lancamento }.filter { it.data.pertenceAo(mes) }

        // A busca ignora o mês e considera todos os lançamentos da propriedade.
        val termo = normalizar(filtros.busca)
        val base = if (termo.isEmpty()) {
            doMes
        } else {
            daPropriedade.filter { combinaComBusca(it, termo) }.map { it.lancamento }
        }

        val doTipo = when (filtros.tipo) {
            FiltroLancamento.TUDO -> base
            FiltroLancamento.DESPESAS -> base.filter { it.tipo == TipoLancamento.DESPESA }
            FiltroLancamento.RECEITAS -> base.filter { it.tipo == TipoLancamento.RECEITA }
        }

        val filtrados = doTipo
            .filter { filtros.categoria == null || it.categoria == filtros.categoria }
            .sortedByDescending { it.data }

        // Chips só das categorias presentes, para não oferecer filtro que resulte em lista vazia.
        // A categoria selecionada é mantida mesmo sem lançamentos, para poder ser desmarcada.
        val categoriasDoMes = (doTipo.map { it.categoria } + listOfNotNull(filtros.categoria))
            .distinct()
            .sortedBy { it.ordinal }

        return LancamentoListaUiState(
            carregando = false,
            nomePropriedade = propriedade.nome,
            temPropriedade = true,
            mes = mes,
            filtro = filtros.tipo,
            categoria = filtros.categoria,
            categoriasDoMes = categoriasDoMes,
            lancamentos = filtrados,
            quantidade = filtrados.size,
            totalFiltradoCentavos = filtrados.sumOf { it.valorComSinal },
            // Saldo e totais são do mês inteiro, independentes de filtro e busca.
            saldoCentavos = doMes.sumOf { it.valorComSinal },
            receitasCentavos = doMes.filter { it.ehReceita }.sumOf { it.valorCentavos },
            despesasCentavos = doMes.filter { !it.ehReceita }.sumOf { it.valorCentavos },
            propriedades = propriedades,
            grupos = agruparPorDia(filtrados, dataDeHoje),
            meses = mesesDoCabecalho(mes, dataDeHoje),
            busca = filtros.busca
        )
    }

    private fun agruparPorDia(lancamentos: List<Lancamento>, hoje: LocalDate): List<GrupoDoDia> =
        // groupBy preserva a ordem de entrada (já decrescente por data).
        lancamentos.groupBy { it.data }.map { (data, doDia) ->
            GrupoDoDia(data = data, rotulo = data.rotuloRelativo(hoje), lancamentos = doDia)
        }

    private fun combinaComBusca(item: LancamentoParaBusca, termo: String): Boolean =
        item.descricao.contains(termo) ||
            CATEGORIAS_NORMALIZADAS.getValue(item.lancamento.categoria).contains(termo)

    // Mês global em SelecaoAtual, compartilhado entre as abas.
    fun aoAvancarMes() = SelecaoAtual.avancarMes()
    fun aoVoltarMes() = SelecaoAtual.voltarMes()
    /** Troca o filtro de tipo e desmarca a categoria se ela não pertencer ao novo tipo. */
    fun aoMudarFiltro(filtro: FiltroLancamento) = _filtros.update { atual ->
        val categoria = atual.categoria?.takeIf { combina(it, filtro) }
        atual.copy(tipo = filtro, categoria = categoria)
    }

    /** Alterna a categoria: selecionar a já marcada a desmarca. */
    fun aoMudarCategoria(categoria: CategoriaLancamento) = _filtros.update { atual ->
        if (atual.categoria == categoria) atual.copy(categoria = null) else atual.copy(categoria = categoria)
    }

    /** Limpa tipo e categoria; preserva a busca. */
    fun aoLimparFiltros() = _filtros.update { Filtros(busca = it.busca) }

    fun aoBuscar(texto: String) = _filtros.update { it.copy(busca = texto.take(TAMANHO_MAXIMO_BUSCA)) }

    fun aoFecharBusca() = _filtros.update { it.copy(busca = "") }

    fun aoSelecionarMes(mes: YearMonth) = SelecaoAtual.selecionarMes(mes)

    private fun combina(categoria: CategoriaLancamento, filtro: FiltroLancamento) = when (filtro) {
        FiltroLancamento.TUDO -> true
        FiltroLancamento.DESPESAS -> categoria.tipo == TipoLancamento.DESPESA
        FiltroLancamento.RECEITAS -> categoria.tipo == TipoLancamento.RECEITA
    }
    fun aoSelecionarPropriedade(id: String) = SelecaoAtual.selecionar(id)

    companion object {
        /** Faixa de meses do seletor, relativa ao mês atual. */
        const val MESES_PARA_TRAS = 24L
        const val MESES_PARA_FRENTE = 12L
        private const val TAMANHO_MAXIMO_BUSCA = 60

        /**
         * Meses de [MESES_PARA_TRAS] atrás até [MESES_PARA_FRENTE] à frente (limite da RN07).
         * A faixa é estendida para incluir [selecionado] se ele estiver fora dela.
         */
        fun mesesDoCabecalho(selecionado: YearMonth, hoje: LocalDate): List<YearMonth> {
            val atual = YearMonth.from(hoje)
            val primeiro = minOf(atual.minusMonths(MESES_PARA_TRAS), selecionado)
            val ultimo = maxOf(atual.plusMonths(MESES_PARA_FRENTE), selecionado)
            return generateSequence(primeiro) { it.plusMonths(1) }.takeWhile { it <= ultimo }.toList()
        }

        /** Normaliza para busca: trim, sem acentos (NFD + remoção de marcas) e minúsculas. */
        fun normalizar(texto: String): String =
            Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replace(MARCAS_DE_ACENTO, "")
                .lowercase()

        private val MARCAS_DE_ACENTO = Regex("\\p{Mn}+")

        private val CATEGORIAS_NORMALIZADAS: Map<CategoriaLancamento, String> =
            CategoriaLancamento.entries.associateWith { normalizar(it.rotulo) }

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
