package br.joao.agrosaldo.feature.clima

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.TextosDaChuva
import br.joao.agrosaldo.core.util.diaEHora
import br.joao.agrosaldo.core.util.emGraus
import br.joao.agrosaldo.core.util.emMilimetros
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.repetirAposErro
import br.joao.agrosaldo.core.util.rotuloDoDia
import br.joao.agrosaldo.data.RelogioDoAparelho
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.di.dependencias
import br.joao.agrosaldo.domain.clima.AvaliacaoChuva
import br.joao.agrosaldo.domain.clima.CondicaoTempo
import br.joao.agrosaldo.domain.clima.LimitesChuva
import br.joao.agrosaldo.domain.clima.Previsao
import br.joao.agrosaldo.domain.clima.RegraDeChuva
import br.joao.agrosaldo.domain.financeiro.ResumoFinanceiro
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.ResultadoAtualizacao
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

/** Linha da previsão diária, com valores já formatados para exibição. */
data class DiaUi(
    val rotulo: String,
    val condicao: CondicaoTempo,
    val maxima: String,
    val minima: String,
    val probabilidade: Int,
    val chuva: String
)

data class ClimaUiState(
    val carregando: Boolean = true,
    val temPropriedade: Boolean = false,
    val propriedadeId: String = "",
    val nomePropriedade: String = "",
    val propriedades: List<Propriedade> = emptyList(),
    val temPrevisao: Boolean = false,
    val temperaturaAtual: String? = null,
    val condicaoAtual: CondicaoTempo = CondicaoTempo.DESCONHECIDA,
    /** Resumo do cartão principal, ex.: "Chuva prevista a partir das 18h". */
    val resumo: String = "",
    val janelaHoras: Int = 12,
    /** Fim da janela de avaliação, ex.: "até as 7h de amanhã". */
    val janelaAte: String = "",
    /** Maior probabilidade de chuva na janela, 0–100. */
    val probabilidadeJanela: Int = 0,
    /** Ex.: "85% · 6 mm"; só "85%" quando não há volume previsto. */
    val chanceEVolume: String = "",
    val vaiChover: Boolean = false,
    val recomendacaoTitulo: String = "",
    val recomendacaoTexto: String = "",
    val dias: List<DiaUi> = emptyList(),
    val atualizadaEm: String? = null,
    val atualizando: Boolean = false,
    /** Erro da última atualização (sem conexão ou falha do serviço). */
    val mensagem: String? = null,
    val avisoAtivo: Boolean = true,
    /** Falha ao ler os dados salvos; a leitura é tentada de novo sozinha e a mensagem some quando volta. */
    val erro: String? = null
)

/**
 * Previsão do tempo da propriedade selecionada (RF12, RF13).
 *
 * A UI observa sempre o cache local ([ClimaRepository.observar]); a rede só atualiza
 * o cache, e automaticamente apenas quando ele tem mais de 1 hora. Funciona offline.
 * Ao voltar para a tela ([aoVoltarParaATela]), recalcula o que depende do relógio.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClimaViewModel(
    private val propriedadeRepository: PropriedadeRepository,
    private val clima: ClimaRepository,
    private val ajustes: AjustesAvisoRepository,
    /** Propriedade e mês escolhidos, compartilhados com as outras telas. */
    private val selecao: SelecaoAtual,
    private val relogio: Clock = RelogioDoAparelho
) : ViewModel() {

    private val atualizandoAgora = MutableStateFlow(false)

    /** Motivo da última atualização ter falhado; o texto depende de haver previsão salva. */
    private val ultimaFalha = MutableStateFlow<FalhaDeAtualizacao?>(null)

    /** Incrementado a cada retorno à tela para recalcular o que depende do relógio. */
    private val voltasATela = MutableStateFlow(0)
    private var jaApareceu = false

    /**
     * Busca em andamento (no máximo uma). Uma nova busca cancela a anterior; [geracao]
     * impede que uma busca antiga que termine depois altere o estado da atual.
     */
    private var busca: Job? = null
    private var geracao = 0

    private val _uiState = MutableStateFlow(ClimaUiState())
    val uiState: StateFlow<ClimaUiState> = _uiState.asStateFlow()

    /** Lista de propriedades e a selecionada em [SelecaoAtual] (fallback: a primeira). */
    private val listaESelecionada: Flow<Pair<List<Propriedade>, Propriedade?>> =
        combine(propriedadeRepository.observarTodas(), selecao.propriedadeId) { lista, id ->
            lista to ResumoFinanceiro.propriedadeAtual(lista, id)
        }

    init {
        // A cada troca de propriedade, atualiza o cache se estiver vencido.
        viewModelScope.launch {
            listaESelecionada.map { it.second }.distinctUntilChanged().collectLatest { propriedade ->
                // A mensagem pertencia à propriedade anterior.
                ultimaFalha.value = null
                if (propriedade != null) buscar(propriedade, forcar = false)
            }
        }

        viewModelScope.launch {
            val comPrevisao = listaESelecionada.flatMapLatest { (lista, propriedade) ->
                if (propriedade == null) {
                    flowOf(Triple(lista, null, null))
                } else {
                    clima.observar(propriedade).map { previsao -> Triple(lista, propriedade, previsao) }
                }
            }
            combine(comPrevisao, ajustes.ajustes, atualizandoAgora, ultimaFalha, voltasATela) {
                    (lista, propriedade, previsao),
                    aj,
                    atualizando,
                    falha,
                    _
                ->
                montar(lista, propriedade, previsao, aj.limites, aj.ativo, atualizando, falha)
            }.repetirAposErro { erro ->
                _uiState.update { it.copy(carregando = false, erro = erro.mensagemParaUsuario(MENSAGEM_ERRO)) }
            }.collect { _uiState.value = it }
        }
    }

    fun aoSelecionarPropriedade(id: String) = selecao.selecionar(id)

    /** Atualização manual: ignora a validade do cache. */
    fun aoAtualizar() {
        val propriedade = propriedadeExibida() ?: return
        // Ignora toques repetidos enquanto uma busca está em andamento.
        if (atualizandoAgora.value) return
        buscar(propriedade, forcar = true)
    }

    /**
     * Chamado em ON_RESUME. Recalcula o que depende do relógio ("Hoje", hora da chuva) e
     * atualiza a previsão se o cache venceu. A primeira chamada é ignorada, pois o init já
     * cobre esse caso.
     */
    fun aoVoltarParaATela() {
        if (!jaApareceu) {
            jaApareceu = true
            return
        }
        voltasATela.update { it + 1 }
        val propriedade = propriedadeExibida() ?: return
        if (!atualizandoAgora.value) buscar(propriedade, forcar = false)
    }

    private fun propriedadeExibida(): Propriedade? {
        val estado = _uiState.value
        return estado.propriedades.firstOrNull { it.id == estado.propriedadeId }
    }

    private fun buscar(propriedade: Propriedade, forcar: Boolean) {
        val minha = ++geracao
        busca?.cancel()
        busca = viewModelScope.launch {
            atualizandoAgora.value = true
            // finally cobre o cancelamento (troca de propriedade); sem ele
            // "atualizando" poderia ficar preso em true.
            try {
                val resultado = if (forcar) clima.atualizar(propriedade) else clima.atualizarSeNecessario(propriedade)
                if (minha == geracao) {
                    ultimaFalha.value = when (resultado) {
                        ResultadoAtualizacao.SemConexao -> FalhaDeAtualizacao.SEM_INTERNET
                        is ResultadoAtualizacao.Indisponivel, is ResultadoAtualizacao.Falha -> FalhaDeAtualizacao.SERVICO
                        is ResultadoAtualizacao.Sucesso, null -> null
                    }
                }
            } finally {
                if (minha == geracao) atualizandoAgora.value = false
            }
        }
    }

    private fun montar(
        propriedades: List<Propriedade>,
        propriedade: Propriedade?,
        previsao: Previsao?,
        limites: LimitesChuva,
        avisoAtivo: Boolean,
        atualizando: Boolean,
        falha: FalhaDeAtualizacao?
    ): ClimaUiState {
        if (propriedade == null) {
            return ClimaUiState(carregando = false, temPropriedade = false, propriedades = propriedades)
        }
        val base = ClimaUiState(
            carregando = false,
            temPropriedade = true,
            propriedadeId = propriedade.id,
            nomePropriedade = propriedade.nome,
            propriedades = propriedades,
            janelaHoras = limites.janelaHoras,
            atualizando = atualizando,
            mensagem = when (falha) {
                FalhaDeAtualizacao.SEM_INTERNET ->
                    if (previsao != null) MENSAGEM_SEM_INTERNET else MENSAGEM_SEM_INTERNET_SEM_PREVISAO
                FalhaDeAtualizacao.SERVICO -> MENSAGEM_FALHA
                null -> null
            },
            avisoAtivo = avisoAtivo
        )
        if (previsao == null) return base

        val agora = relogio.instant()
        val avaliacao = RegraDeChuva.avaliar(previsao, agora, limites)
        val agoraLocal = previsao.horaLocal(agora)
        val hoje = agoraLocal.toLocalDate()
        val fimJanela = agoraLocal.truncatedTo(ChronoUnit.HOURS).plusHours(limites.janelaHoras.toLong())
        val janelaAte = TextosDaChuva.ate(fimJanela, agoraLocal)
        val (titulo, texto) = recomendacao(avaliacao, limites)

        return base.copy(
            temPrevisao = true,
            temperaturaAtual = previsao.temperaturaAtual?.emGraus(),
            condicaoAtual = previsao.condicaoAtual,
            resumo = resumo(avaliacao, previsao, limites, agoraLocal),
            janelaAte = janelaAte,
            probabilidadeJanela = avaliacao.probabilidadeMaxima,
            chanceEVolume = TextosDaChuva.chanceEVolumeCurto(avaliacao),
            vaiChover = avaliacao.vaiChover,
            recomendacaoTitulo = titulo,
            recomendacaoTexto = texto,
            dias = previsao.dias.filter { !it.data.isBefore(hoje) }.take(7).map { dia ->
                DiaUi(
                    rotulo = dia.data.rotuloDoDia(hoje),
                    condicao = dia.condicao,
                    maxima = dia.temperaturaMaxima?.emGraus() ?: "–",
                    minima = dia.temperaturaMinima?.emGraus() ?: "–",
                    probabilidade = dia.probabilidadeChuva,
                    chuva = dia.chuvaMm.emMilimetros()
                )
            },
            // O cache vale até 2 dias: sem o dia, "às 14:05" de ontem pareceria de hoje.
            atualizadaEm = "Atualizada ${previsao.obtidaEm.diaEHora(agora, relogio.zone)}"
        )
    }

    private fun resumo(avaliacao: AvaliacaoChuva, previsao: Previsao, limites: LimitesChuva, agora: LocalDateTime): String = when {
        !avaliacao.temDados -> "Previsão desatualizada"
        avaliacao.vaiChover && avaliacao.inicioPrevisto != null ->
            "Chuva prevista ${TextosDaChuva.aPartirDe(avaliacao.inicioPrevisto, agora)}"
        avaliacao.vaiChover -> "Chuva prevista nas próximas ${limites.janelaHoras} horas"
        else -> previsao.condicaoAtual.rotulo
    }

    /** Título e texto da recomendação de irrigação conforme a avaliação e os limites. */
    private fun recomendacao(avaliacao: AvaliacaoChuva, limites: LimitesChuva): Pair<String, String> {
        val limitesTexto = "${limites.probabilidadeMinima}% de chance ou ${limites.chuvaMinimaMm.emMilimetros()}"
        val previsto = TextosDaChuva.chanceEVolume(avaliacao, separador = " e ")
        return when {
            !avaliacao.temDados ->
                "Previsão desatualizada" to "Atualize para ver as próximas ${limites.janelaHoras} horas."
            avaliacao.vaiChover ->
                "Dá para adiar a irrigação do pivô" to
                    "Previsão de $previsto nas próximas ${limites.janelaHoras} horas. " +
                    "Passou dos limites que você configurou ($limitesTexto)."
            TextosDaChuva.podeChoverAbaixoDoLimite(avaliacao) ->
                "Pode chover, mas abaixo do seu limite" to
                    "Nas próximas ${limites.janelaHoras} horas: até $previsto. " +
                    "O aviso só sai a partir dos limites que você configurou ($limitesTexto)."
            else ->
                "Sem chuva relevante à vista" to
                    "Nas próximas ${limites.janelaHoras} horas: até $previsto. " +
                    "Abaixo dos limites que você configurou ($limitesTexto)."
        }
    }

    private enum class FalhaDeAtualizacao { SEM_INTERNET, SERVICO }

    companion object {
        const val MENSAGEM_SEM_INTERNET = "Sem internet. Mostrando a última previsão salva."
        const val MENSAGEM_SEM_INTERNET_SEM_PREVISAO = "Sem internet. Tente de novo quando o celular tiver sinal."
        const val MENSAGEM_ERRO = "Não foi possível carregar a previsão salva. Tentando de novo…"
        const val MENSAGEM_FALHA = "Não deu para atualizar a previsão agora. Tente de novo em instantes."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ClimaViewModel(
                    selecao = dependencias.selecao,
                    propriedadeRepository = dependencias.propriedades,
                    clima = dependencias.clima,
                    ajustes = dependencias.ajustesAviso
                )
            }
        }
    }
}
