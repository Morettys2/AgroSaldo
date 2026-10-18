package br.joao.agrosaldo.feature.lancamento

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.repetirAposErro
import br.joao.agrosaldo.core.util.rotuloRelativo
import br.joao.agrosaldo.data.RelogioDoAparelho
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.Despachantes
import br.joao.agrosaldo.domain.DespachantesDoSistema
import br.joao.agrosaldo.domain.financeiro.ResumoFinanceiro
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Filtro por tipo (RF09). */
enum class FiltroLancamento { TUDO, DESPESAS, RECEITAS }

/** Lançamentos de um dia, com rótulo relativo ("Hoje", "Ontem", "2 de outubro"). */
data class GrupoDoDia(val data: LocalDate, val rotulo: String, val lancamentos: List<Lancamento>)

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
    val busca: String = "",
    /** Falha ao ler os dados; a leitura é tentada de novo sozinha e a mensagem some quando volta. */
    val erro: String? = null
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
 * @param despachantes o estado é montado em [Despachantes.calculo], fora da main thread.
 */
@HiltViewModel
class LancamentoListaViewModel @Inject constructor(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository,
    /** Propriedade e mês escolhidos, compartilhados com as outras telas. */
    private val selecao: SelecaoAtual,
    private val relogio: Clock = RelogioDoAparelho,
    private val despachantes: Despachantes = DespachantesDoSistema
) : ViewModel() {

    private fun hoje(): LocalDate = LocalDate.now(relogio)

    private val filtrosAtivos = MutableStateFlow(Filtros())

    /** Incrementado ao retomar a tela para recalcular rótulos relativos após a meia-noite. */
    private val voltasATela = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(LancamentoListaUiState())
    val uiState: StateFlow<LancamentoListaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                lancamentoRepository.observarTodos().map { lista -> lista.map(::LancamentoParaBusca) },
                propriedadeRepository.observarTodas(),
                selecao.propriedadeId,
                selecao.mes,
                filtrosAtivos.combine(voltasATela) { filtros, _ -> filtros }
            ) { lancamentos, propriedades, selecionadaId, mes, filtros ->
                montarEstado(lancamentos, propriedades, selecionadaId, mes, filtros)
            }
                .flowOn(despachantes.calculo)
                .repetirAposErro { erro ->
                    _uiState.update { it.copy(carregando = false, erro = erro.mensagemParaUsuario(MENSAGEM_ERRO)) }
                }
                .collect { estado -> _uiState.value = estado }
        }

        // Mudança de mês (vinda de outra tela) limpa a busca, que ocultaria o mês escolhido.
        // drop(1) ignora o valor inicial do StateFlow.
        viewModelScope.launch {
            selecao.mes.drop(1).collect { aoFecharBusca() }
        }
    }

    /** Chamado em ON_RESUME: acompanha a virada do mês e recalcula os rótulos relativos ("Hoje"). */
    fun aoVoltarParaATela() {
        selecao.acompanharMesCorrente(YearMonth.from(hoje()))
        voltasATela.update { it + 1 }
    }

    private fun montarEstado(
        todosLancamentos: List<LancamentoParaBusca>,
        propriedades: List<Propriedade>,
        selecionadaId: String?,
        mes: YearMonth,
        filtros: Filtros
    ): LancamentoListaUiState {
        // Sem seleção válida, usa a primeira propriedade.
        val propriedade = ResumoFinanceiro.propriedadeAtual(propriedades, selecionadaId)

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
        val doMes = ResumoFinanceiro.doMes(daPropriedade.map { it.lancamento }, propriedade.id, mes)
        val totaisDoMes = ResumoFinanceiro.totais(doMes)

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
            totalFiltradoCentavos = ResumoFinanceiro.totais(filtrados).saldoCentavos,
            // Saldo e totais são do mês inteiro, independentes de filtro e busca.
            saldoCentavos = totaisDoMes.saldoCentavos,
            receitasCentavos = totaisDoMes.receitasCentavos,
            despesasCentavos = totaisDoMes.despesasCentavos,
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

    private fun combinaComBusca(item: LancamentoParaBusca, termo: String): Boolean = item.descricao.contains(termo) ||
        CATEGORIAS_NORMALIZADAS.getValue(item.lancamento.categoria).contains(termo)

    // Mês global em SelecaoAtual, compartilhado entre as abas.
    fun aoAvancarMes() = selecao.avancarMes()
    fun aoVoltarMes() = selecao.voltarMes()

    /** Troca o filtro de tipo e desmarca a categoria se ela não pertencer ao novo tipo. */
    fun aoMudarFiltro(filtro: FiltroLancamento) = filtrosAtivos.update { atual ->
        val categoria = atual.categoria?.takeIf { combina(it, filtro) }
        atual.copy(tipo = filtro, categoria = categoria)
    }

    /** Alterna a categoria: selecionar a já marcada a desmarca. */
    fun aoMudarCategoria(categoria: CategoriaLancamento) = filtrosAtivos.update { atual ->
        if (atual.categoria == categoria) atual.copy(categoria = null) else atual.copy(categoria = categoria)
    }

    /** Limpa tipo e categoria; preserva a busca. */
    fun aoLimparFiltros() = filtrosAtivos.update { Filtros(busca = it.busca) }

    fun aoBuscar(texto: String) = filtrosAtivos.update { it.copy(busca = texto.take(TAMANHO_MAXIMO_BUSCA)) }

    fun aoFecharBusca() = filtrosAtivos.update { it.copy(busca = "") }

    fun aoSelecionarMes(mes: YearMonth) = selecao.selecionarMes(mes)

    private fun combina(categoria: CategoriaLancamento, filtro: FiltroLancamento) = when (filtro) {
        FiltroLancamento.TUDO -> true
        FiltroLancamento.DESPESAS -> categoria.tipo == TipoLancamento.DESPESA
        FiltroLancamento.RECEITAS -> categoria.tipo == TipoLancamento.RECEITA
    }
    fun aoSelecionarPropriedade(id: String) = selecao.selecionar(id)

    companion object {
        /** Faixa de meses do seletor, relativa ao mês atual. */
        const val MESES_PARA_TRAS = 24L
        const val MESES_PARA_FRENTE = 12L
        private const val TAMANHO_MAXIMO_BUSCA = 60

        const val MENSAGEM_ERRO = "Não foi possível carregar os lançamentos. Tentando de novo…"

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
        fun normalizar(texto: String): String = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
            .replace(MARCAS_DE_ACENTO, "")
            .lowercase()

        private val MARCAS_DE_ACENTO = Regex("\\p{Mn}+")

        private val CATEGORIAS_NORMALIZADAS: Map<CategoriaLancamento, String> =
            CategoriaLancamento.entries.associateWith { normalizar(it.rotulo) }
    }
}
