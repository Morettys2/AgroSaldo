package br.joao.agrosaldo.feature.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.joao.agrosaldo.core.util.TextosDaChuva
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.repetirAposErro
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.clima.LimitesChuva
import br.joao.agrosaldo.domain.clima.Previsao
import br.joao.agrosaldo.domain.clima.RegraDeChuva
import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.domain.financeiro.GastoPorCategoria
import br.joao.agrosaldo.domain.financeiro.ResumoFinanceiro
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.UsuarioLogadoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

data class InicioUiState(
    val carregando: Boolean = true,
    val temPropriedade: Boolean = false,
    val nomePropriedade: String = "",
    val propriedades: List<Propriedade> = emptyList(),
    val mes: YearMonth = YearMonth.now(),
    val saldoCentavos: Long = 0L,
    val receitasCentavos: Long = 0L,
    val despesasCentavos: Long = 0L,
    /** Receitas / (receitas + despesas), em 0..1; 0.5 quando não há movimento. */
    val proporcaoReceitas: Float = 0.5f,
    val gastosPorCategoria: List<GastoPorCategoria> = emptyList(),
    val ultimosLancamentos: List<Lancamento> = emptyList(),
    /** Nulo enquanto não há previsão em cache para a propriedade. */
    val chuva: CartaoChuvaUi? = null,
    /** Iniciais do usuário para o botão da conta (ex.: "MS"); vazio se o nome não estiver disponível. */
    val iniciais: String = "",
    /** Referência para os rótulos relativos de data. */
    val hoje: LocalDate = LocalDate.now(),
    /** Falha ao ler os dados; a leitura é tentada de novo sozinha e a mensagem some quando volta. */
    val erro: String? = null
)

/**
 * Textos do cartão de chuva. Ex.: [rotulo] "Previsão · próximas 12 h",
 * [frase] "Chuva às 18h. Dá para adiar o pivô.", [detalhe] "92% de chance · 5 mm".
 */
data class CartaoChuvaUi(val rotulo: String, val frase: String, val detalhe: String, val vaiChover: Boolean)

/**
 * Tela inicial: saldo, receitas/despesas e maiores gastos do mês da propriedade
 * selecionada, últimos lançamentos e cartão de chuva.
 *
 * Os totais são agregados em memória a partir de todos os lançamentos a cada emissão.
 * Possível melhoria: mover a agregação para SQL (SUM/GROUP BY) se o volume de lançamentos crescer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InicioViewModel @Inject constructor(
    private val lancamentoRepository: LancamentoRepository,
    private val propriedadeRepository: PropriedadeRepository,
    private val clima: ClimaRepository,
    private val ajustes: AjustesAvisoRepository,
    /** Propriedade e mês escolhidos, compartilhados com as outras telas. */
    private val selecao: SelecaoAtual,
    /** Quem está conectado; o nome gera as iniciais do cabeçalho. */
    usuarioLogado: UsuarioLogadoRepository,
    private val relogio: Clock
) : ViewModel() {

    private fun hoje(): LocalDate = LocalDate.now(relogio)

    private val _uiState = MutableStateFlow(InicioUiState())
    val uiState: StateFlow<InicioUiState> = _uiState.asStateFlow()

    /**
     * Incrementado a cada retorno à tela para recalcular o que depende do relógio
     * (rótulos "Hoje"/"Ontem" e a frase da chuva).
     */
    private val voltasATela = MutableStateFlow(0)
    private var jaApareceu = false
    private var propriedadeDaPrevisao: Propriedade? = null

    init {
        viewModelScope.launch {
            combine(
                lancamentoRepository.observarTodos(),
                propriedadeRepository.observarTodas(),
                selecao.propriedadeId,
                selecao.mes,
                voltasATela
            ) { lancamentos, propriedades, selecionadaId, mes, _ ->
                montarEstado(lancamentos, propriedades, selecionadaId, mes)
            }.repetirAposErro { erro ->
                _uiState.update { it.copy(carregando = false, erro = erro.mensagemParaUsuario(MENSAGEM_ERRO)) }
            }.collect { estado ->
                // chuva e iniciais são mantidas por outros coletores; preserva os valores atuais.
                val atual = _uiState.value
                _uiState.value = estado.copy(chuva = atual.chuva, iniciais = atual.iniciais)
            }
        }

        viewModelScope.launch {
            usuarioLogado.observarNome()
                .map { nome -> if (nome.isNullOrBlank()) "" else RegrasDeConta.iniciais(nome) }
                // Sem isto, um erro do banco escaparia do launch e fecharia o app.
                .repetirAposErro { _uiState.update { it.copy(iniciais = "") } }
                .collect { iniciais -> _uiState.update { it.copy(iniciais = iniciais) } }
        }

        // Previsão vem do cache; atualizarSeNecessario busca nova se estiver vencida.
        val propriedadeAtual = combine(propriedadeRepository.observarTodas(), selecao.propriedadeId) { lista, id ->
            ResumoFinanceiro.propriedadeAtual(lista, id)
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
                .combine(voltasATela) { (previsao, aj), _ -> previsao?.let { cartaoChuva(it, aj.limites) } }
                .collect { cartao -> _uiState.update { it.copy(chuva = cartao) } }
        }
    }

    /**
     * Chamado em ON_RESUME. Acompanha a virada do mês, recalcula o que depende do relógio e
     * atualiza a previsão se vencida. Fora a virada do mês, a primeira chamada é ignorada,
     * pois o init já cobre esse caso.
     */
    fun aoVoltarParaATela() {
        selecao.acompanharMesCorrente(YearMonth.from(hoje()))
        if (!jaApareceu) {
            jaApareceu = true
            return
        }
        voltasATela.update { it + 1 }
        val propriedade = propriedadeDaPrevisao ?: return
        viewModelScope.launch { clima.atualizarSeNecessario(propriedade) }
    }

    private fun cartaoChuva(previsao: Previsao, limites: LimitesChuva): CartaoChuvaUi {
        val instante = relogio.instant()
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
                    if (TextosDaChuva.jaComecou(inicio, agora)) {
                        "Chuva já nesta hora"
                    } else {
                        "Chuva ${TextosDaChuva.quando(inicio, agora)}"
                    }
                } ?: "Chuva prevista"
                CartaoChuvaUi(
                    rotulo = rotulo,
                    frase = "$quando. Dá para adiar o pivô.",
                    detalhe = TextosDaChuva.chanceEVolume(avaliacao),
                    vaiChover = true
                )
            }
            // Chance relevante mas abaixo dos limites do usuário: evita exibir "Sem chuva"
            // ao lado de uma probabilidade alta.
            TextosDaChuva.podeChoverAbaixoDoLimite(avaliacao) -> CartaoChuvaUi(
                rotulo = rotulo,
                frase = "Pode chover, mas abaixo do seu limite.",
                detalhe = "Até ${TextosDaChuva.chanceEVolume(avaliacao)} · seu limite: ${TextosDaChuva.limites(limites)}",
                vaiChover = false
            )
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
        val propriedade = ResumoFinanceiro.propriedadeAtual(propriedades, selecionadaId)
            ?: return InicioUiState(carregando = false, temPropriedade = false, mes = mes, hoje = hoje())

        val doMes = ResumoFinanceiro.doMes(todosLancamentos, propriedade.id, mes)
        val totais = ResumoFinanceiro.totais(doMes)

        return InicioUiState(
            carregando = false,
            temPropriedade = true,
            nomePropriedade = propriedade.nome,
            propriedades = propriedades,
            mes = mes,
            saldoCentavos = totais.saldoCentavos,
            receitasCentavos = totais.receitasCentavos,
            despesasCentavos = totais.despesasCentavos,
            proporcaoReceitas = totais.proporcaoReceitas,
            gastosPorCategoria = ResumoFinanceiro.rankingDeGastos(doMes, quantos = MAXIMO_CATEGORIAS),
            ultimosLancamentos = doMes.sortedByDescending { it.data }.take(MAXIMO_ULTIMOS_LANCAMENTOS),
            hoje = hoje()
        )
    }

    // Mês global em SelecaoAtual, compartilhado entre as abas.
    fun aoAvancarMes() = selecao.avancarMes()
    fun aoVoltarMes() = selecao.voltarMes()
    fun aoSelecionarPropriedade(id: String) = selecao.selecionar(id)

    companion object {
        /** Quantas categorias o cartão "Maiores gastos" mostra. */
        const val MAXIMO_CATEGORIAS = 3
        const val MAXIMO_ULTIMOS_LANCAMENTOS = 4

        const val MENSAGEM_ERRO = "Não foi possível carregar os lançamentos. Tentando de novo…"
    }
}
