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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth

/** Filtro por tipo (RF09). */
enum class FiltroLancamento { TUDO, DESPESAS, RECEITAS }

/** Os lançamentos de um dia, com o nome do dia: "Hoje", "Ontem", "2 de outubro". */
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
    /** Null = todas as categorias. */
    val categoria: CategoriaLancamento? = null,
    /** As categorias que têm lançamento no mês, para os chips do filtro. */
    val categoriasDoMes: List<CategoriaLancamento> = emptyList(),
    val lancamentos: List<Lancamento> = emptyList(),
    val quantidade: Int = 0,
    /** Soma dos lançamentos que aparecem, com sinal. Só faz sentido com filtro. */
    val totalFiltradoCentavos: Long = 0L,
    val saldoCentavos: Long = 0L,
    /** Entradas e saídas do mês inteiro, para o canto do cabeçalho. */
    val receitasCentavos: Long = 0L,
    val despesasCentavos: Long = 0L,
    val propriedades: List<Propriedade> = emptyList(),
    /** A mesma lista de [lancamentos], separada por dia. */
    val grupos: List<GrupoDoDia> = emptyList(),
    /** As pílulas de mês do cabeçalho, da mais antiga para a mais nova. */
    val meses: List<YearMonth> = emptyList(),
    /** O texto da busca. Preenchido, a lista mostra todos os meses. */
    val busca: String = ""
) {
    val temFiltro: Boolean get() = filtro != FiltroLancamento.TUDO || categoria != null
    val buscando: Boolean get() = busca.isNotBlank()
}

/** Os filtros juntos, para mudarem num passo só. */
private data class Filtros(
    val tipo: FiltroLancamento = FiltroLancamento.TUDO,
    val categoria: CategoriaLancamento? = null,
    val busca: String = ""
)

/**
 * ViewModel da lista de lançamentos.
 *
 * O ponto interessante aqui é o `combine`: cinco fontes que mudam por conta
 * própria — lançamentos, propriedades, propriedade selecionada, mês e filtros —
 * viram um único estado de tela.
 *
 * Sem ele, cada uma dessas mudanças exigiria recalcular a tela na mão e as
 * combinações ficariam fáceis de esquecer. Com ele, mexer em qualquer uma
 * refaz o estado inteiro automaticamente.
 */
class LancamentoListaViewModel(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository,
    private val hoje: () -> LocalDate = LocalDate::now
) : ViewModel() {

    private val _filtros = MutableStateFlow(Filtros())

    /** Sobe quando a tela volta a aparecer: "Hoje" e "Ontem" mudam à meia-noite. */
    private val _voltas = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(LancamentoListaUiState())
    val uiState: StateFlow<LancamentoListaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                lancamentoRepository.observarTodos(),
                propriedadeRepository.observarTodas(),
                SelecaoAtual.propriedadeId,
                SelecaoAtual.mes,
                _filtros.combine(_voltas) { filtros, _ -> filtros }
            ) { lancamentos, propriedades, selecionadaId, mes, filtros ->
                montarEstado(lancamentos, propriedades, selecionadaId, mes, filtros)
            }.collect { estado ->
                _uiState.value = estado
            }
        }

        // O mês mudou por fora (tocou num mês do Histórico, ou no Início):
        // a pessoa quer ver aquele mês, e uma busca antiga esconderia ele.
        // Durante a busca as pílulas de mês somem, então daqui nunca vem.
        viewModelScope.launch {
            SelecaoAtual.mes.drop(1).collect { aoFecharBusca() }
        }
    }

    fun aoVoltarParaATela() = _voltas.update { it + 1 }

    private fun montarEstado(
        todosLancamentos: List<Lancamento>,
        propriedades: List<Propriedade>,
        selecionadaId: String?,
        mes: YearMonth,
        filtros: Filtros
    ): LancamentoListaUiState {

        // Se nada foi escolhido ainda, assume a primeira da lista.
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

        val daPropriedade = todosLancamentos.filter { it.propriedadeId == propriedade.id }
        val doMes = daPropriedade.filter { it.data.pertenceAo(mes) }

        // Com busca, procura em todos os meses: quem procura "trator" quer
        // achar o abastecimento, não lembrar em que mês foi.
        val termo = normalizar(filtros.busca)
        val base = if (termo.isEmpty()) doMes else daPropriedade.filter { combinaComBusca(it, termo) }

        val doTipo = when (filtros.tipo) {
            FiltroLancamento.TUDO -> base
            FiltroLancamento.DESPESAS -> base.filter { it.tipo == TipoLancamento.DESPESA }
            FiltroLancamento.RECEITAS -> base.filter { it.tipo == TipoLancamento.RECEITA }
        }

        val filtrados = doTipo
            .filter { filtros.categoria == null || it.categoria == filtros.categoria }
            .sortedByDescending { it.data }

        // Chips só das categorias que aparecem no mês: oferecer "Sementes"
        // num mês sem semente levaria a uma lista vazia. A escolhida fica
        // mesmo sem lançamento (trocou de mês), senão não daria para desmarcar.
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
            // O saldo considera o mês inteiro, não o filtro: filtrar por
            // despesas não deveria fazer o saldo do mês parecer negativo.
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
        // groupBy mantém a ordem de chegada, e a lista já vem do mais novo
        // para o mais antigo: os grupos saem na mesma ordem.
        lancamentos.groupBy { it.data }.map { (data, doDia) ->
            GrupoDoDia(data = data, rotulo = data.rotuloRelativo(hoje), lancamentos = doDia)
        }

    private fun combinaComBusca(lancamento: Lancamento, termo: String): Boolean =
        normalizar(lancamento.descricao).contains(termo) ||
            normalizar(lancamento.categoria.rotulo).contains(termo)

    // O mês é o mesmo em todas as abas: mudar aqui muda lá também.
    fun aoAvancarMes() = SelecaoAtual.avancarMes()
    fun aoVoltarMes() = SelecaoAtual.voltarMes()
    /**
     * Trocar o tipo desmarca a categoria que não combina com ele: com
     * "Receitas" e "Adubo" marcados ao mesmo tempo, a lista ficaria sempre vazia.
     */
    fun aoMudarFiltro(filtro: FiltroLancamento) = _filtros.update { atual ->
        val categoria = atual.categoria?.takeIf { combina(it, filtro) }
        atual.copy(tipo = filtro, categoria = categoria)
    }

    /** Tocar na categoria já marcada desmarca. */
    fun aoMudarCategoria(categoria: CategoriaLancamento) = _filtros.update { atual ->
        if (atual.categoria == categoria) atual.copy(categoria = null) else atual.copy(categoria = categoria)
    }

    /** Limpa tipo e categoria. A busca fica: ela tem o próprio botão de fechar. */
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
        /** Quantos meses para trás e para a frente aparecem nas pílulas. */
        const val MESES_PARA_TRAS = 24L
        const val MESES_PARA_FRENTE = 12L
        private const val TAMANHO_MAXIMO_BUSCA = 60

        /**
         * De dois anos atrás até um ano à frente (o limite de data do
         * lançamento, RN07). Se o mês escolhido estiver fora disso (veio
         * do histórico de um ano antigo), a faixa estica para incluí-lo.
         */
        fun mesesDoCabecalho(selecionado: YearMonth, hoje: LocalDate): List<YearMonth> {
            val atual = YearMonth.from(hoje)
            val primeiro = minOf(atual.minusMonths(MESES_PARA_TRAS), selecionado)
            val ultimo = maxOf(atual.plusMonths(MESES_PARA_FRENTE), selecionado)
            return generateSequence(primeiro) { it.plusMonths(1) }.takeWhile { it <= ultimo }.toList()
        }

        /** "Adubação" e "adubacao" são a mesma busca: sem acento e sem maiúscula. */
        fun normalizar(texto: String): String =
            Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replace(MARCAS_DE_ACENTO, "")
                .lowercase()

        private val MARCAS_DE_ACENTO = Regex("\\p{Mn}+")

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
