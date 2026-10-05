package br.joao.agrosaldo.feature.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.emMilimetros
import br.joao.agrosaldo.core.util.horaCurta
import br.joao.agrosaldo.core.util.pertenceAo
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.clima.LimitesChuva
import br.joao.agrosaldo.domain.clima.Previsao
import br.joao.agrosaldo.domain.clima.RegraDeChuva
import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

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
    val ultimosLancamentos: List<Lancamento> = emptyList(),
    /** Null enquanto não há previsão guardada para a propriedade. */
    val chuva: CartaoChuvaUi? = null,
    /** "FM", no botão redondo da conta. Vazio enquanto o nome não chegou. */
    val iniciais: String = "",
    /** Para escrever "Hoje" e "Ontem" nos últimos lançamentos. */
    val hoje: LocalDate = LocalDate.now()
)

/**
 * O cartão azul do Início, em três linhas:
 *
 *   Previsão · próximas 12 h              ← rotulo
 *   Chuva às 18h. Dá para adiar o pivô.   ← frase (a que importa)
 *   92% de chance · 5 mm                  ← detalhe
 */
data class CartaoChuvaUi(
    val rotulo: String,
    val frase: String,
    val detalhe: String,
    val vaiChover: Boolean
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
@OptIn(ExperimentalCoroutinesApi::class)
class InicioViewModel(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository,
    private val clima: ClimaRepository,
    private val ajustes: AjustesAvisoRepository,
    private val relogio: () -> Long = System::currentTimeMillis,
    /** O nome de quem está conectado, para as iniciais do botão da conta. */
    nomeDoUsuario: Flow<String?> = flowOf(null),
    private val hoje: () -> LocalDate = LocalDate::now
) : ViewModel() {

    private val _uiState = MutableStateFlow(InicioUiState())
    val uiState: StateFlow<InicioUiState> = _uiState.asStateFlow()

    /**
     * Sobe a cada vez que a pessoa volta para a tela. "Hoje", "Ontem" e a
     * frase da chuva dependem da hora: sem isto, o cartão montado às 23h30
     * ("Chuva amanhã às 1h") continuaria igual de manhã, um dia errado.
     */
    private val _voltas = MutableStateFlow(0)
    private var jaApareceu = false
    private var propriedadeDaPrevisao: Propriedade? = null

    init {
        viewModelScope.launch {
            combine(
                lancamentoRepository.observarTodos(),
                propriedadeRepository.observarTodas(),
                SelecaoAtual.propriedadeId,
                SelecaoAtual.mes,
                _voltas
            ) { lancamentos, propriedades, selecionadaId, mes, _ ->
                montarEstado(lancamentos, propriedades, selecionadaId, mes)
            }.collect { estado ->
                // O cartão de chuva e as iniciais vêm de outros fluxos (abaixo);
                // preserva o que já estava.
                val atual = _uiState.value
                _uiState.value = estado.copy(chuva = atual.chuva, iniciais = atual.iniciais)
            }
        }

        viewModelScope.launch {
            nomeDoUsuario
                .map { nome -> if (nome.isNullOrBlank()) "" else RegrasDeConta.iniciais(nome) }
                .collect { iniciais -> _uiState.update { it.copy(iniciais = iniciais) } }
        }

        // Previsão da propriedade selecionada, lida do cache. Se estiver velha
        // (mais de 1 h), busca uma nova em segundo plano.
        val propriedadeAtual = combine(propriedadeRepository.observarTodas(), SelecaoAtual.propriedadeId) { lista, id ->
            lista.firstOrNull { it.id == id } ?: lista.firstOrNull()
        }.distinctUntilChanged()

        viewModelScope.launch {
            propriedadeAtual.collectLatest { propriedade ->
                propriedadeDaPrevisao = propriedade
                if (propriedade != null) clima.atualizarSeNecessario(propriedade)
            }
        }

        viewModelScope.launch {
            propriedadeAtual
                .flatMapLatest { propriedade -> if (propriedade == null) flowOf(null) else clima.observar(propriedade) }
                .combine(ajustes.ajustes) { previsao, aj -> previsao to aj }
                .combine(_voltas) { (previsao, aj), _ -> previsao?.let { cartaoChuva(it, aj.limites) } }
                .collect { cartao -> _uiState.update { it.copy(chuva = cartao) } }
        }
    }

    /**
     * A tela voltou a aparecer (abriu o app de novo, voltou de outra aba).
     * Refaz o que depende da hora e, se a previsão tiver mais de 1 h, busca
     * outra. A primeira vez é ignorada: ao abrir, o init já fez as duas coisas.
     */
    fun aoVoltarParaATela() {
        if (!jaApareceu) {
            jaApareceu = true
            return
        }
        _voltas.update { it + 1 }
        val propriedade = propriedadeDaPrevisao ?: return
        viewModelScope.launch { clima.atualizarSeNecessario(propriedade) }
    }

    private fun cartaoChuva(previsao: Previsao, limites: LimitesChuva): CartaoChuvaUi {
        val instante = Instant.ofEpochMilli(relogio())
        val avaliacao = RegraDeChuva.avaliar(previsao, instante, limites)
        val rotulo = "Previsão · próximas ${limites.janelaHoras} h"
        return when {
            !avaliacao.temDados -> CartaoChuvaUi(
                rotulo = rotulo,
                frase = "Previsão desatualizada.",
                detalhe = "Toque para atualizar",
                vaiChover = false
            )
            avaliacao.vaiChover -> {
                val agora = previsao.horaLocal(instante)
                val quando = avaliacao.inicioPrevisto?.let { inicio ->
                    // A janela pode passar da meia-noite (e ir até 48 h):
                    // "às 2h" sozinho confundiria com a madrugada de hoje.
                    when (ChronoUnit.DAYS.between(agora.toLocalDate(), inicio.toLocalDate())) {
                        0L -> if (inicio.hour == agora.hour) "Chuva já nesta hora" else "Chuva às ${inicio.horaCurta()}"
                        1L -> "Chuva amanhã às ${inicio.horaCurta()}"
                        else -> "Chuva depois de amanhã às ${inicio.horaCurta()}"
                    }
                } ?: "Chuva prevista"
                CartaoChuvaUi(
                    rotulo = rotulo,
                    frase = "$quando. Dá para adiar o pivô.",
                    detalhe = "${avaliacao.probabilidadeMaxima}% de chance · ${avaliacao.chuvaTotalMm.emMilimetros()}",
                    vaiChover = true
                )
            }
            else -> CartaoChuvaUi(
                rotulo = rotulo,
                frase = "Sem chuva nas próximas ${limites.janelaHoras} h.",
                detalhe = "Até ${avaliacao.probabilidadeMaxima}% de chance · toque para ver a semana",
                vaiChover = false
            )
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
            return InicioUiState(carregando = false, temPropriedade = false, mes = mes, hoje = hoje())
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
            ultimosLancamentos = doMes.sortedByDescending { it.data }.take(4),
            hoje = hoje()
        )
    }

    // O mês é o mesmo em todas as abas: mudar aqui muda lá também.
    fun aoAvancarMes() = SelecaoAtual.avancarMes()
    fun aoVoltarMes() = SelecaoAtual.voltarMes()
    fun aoSelecionarPropriedade(id: String) = SelecaoAtual.selecionar(id)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InicioViewModel(
                    lancamentoRepository = Repositorios.lancamentos,
                    propriedadeRepository = Repositorios.propriedades,
                    clima = Repositorios.clima,
                    ajustes = Repositorios.ajustesAviso,
                    nomeDoUsuario = Repositorios.conta.observarUsuarioLogado().map { it?.nome }
                )
            }
        }
    }
}
