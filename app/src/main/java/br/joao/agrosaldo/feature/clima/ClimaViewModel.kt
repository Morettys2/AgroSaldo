package br.joao.agrosaldo.feature.clima

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.TextosDaChuva
import br.joao.agrosaldo.core.util.emGraus
import br.joao.agrosaldo.core.util.emMilimetros
import br.joao.agrosaldo.core.util.horaMinuto
import br.joao.agrosaldo.core.util.rotuloDoDia
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.clima.AvaliacaoChuva
import br.joao.agrosaldo.domain.clima.CondicaoTempo
import br.joao.agrosaldo.domain.clima.LimitesChuva
import br.joao.agrosaldo.domain.clima.Previsao
import br.joao.agrosaldo.domain.clima.RegraDeChuva
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.ResultadoAtualizacao
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
    val avisoAtivo: Boolean = true
)

/**
 * Previsão do tempo da propriedade selecionada (RF12, RF13).
 *
 * A UI observa sempre o cache local ([ClimaRepository.observar]); a rede só atualiza
 * o cache, e automaticamente apenas quando ele tem mais de 1 hora. Funciona offline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClimaViewModel(
    private val propriedadeRepository: PropriedadeRepository,
    private val clima: ClimaRepository,
    private val ajustes: AjustesAvisoRepository,
    private val relogio: () -> Long = System::currentTimeMillis,
    private val zona: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _atualizando = MutableStateFlow(false)
    private val _mensagem = MutableStateFlow<String?>(null)

    /**
     * Busca em andamento (no máximo uma). Uma nova busca cancela a anterior; [geracao]
     * impede que uma busca antiga que termine depois altere o estado da atual.
     */
    private var busca: Job? = null
    private var geracao = 0

    private val _uiState = MutableStateFlow(ClimaUiState())
    val uiState: StateFlow<ClimaUiState> = _uiState.asStateFlow()

    /** Lista de propriedades e a selecionada em [SelecaoAtual] (fallback: a primeira). */
    private val selecao: Flow<Pair<List<Propriedade>, Propriedade?>> =
        combine(propriedadeRepository.observarTodas(), SelecaoAtual.propriedadeId) { lista, id ->
            lista to (lista.firstOrNull { it.id == id } ?: lista.firstOrNull())
        }

    init {
        // A cada troca de propriedade, atualiza o cache se estiver vencido.
        viewModelScope.launch {
            selecao.map { it.second }.distinctUntilChanged().collectLatest { propriedade ->
                // A mensagem pertencia à propriedade anterior.
                _mensagem.value = null
                if (propriedade != null) buscar(propriedade, forcar = false)
            }
        }

        viewModelScope.launch {
            val comPrevisao = selecao.flatMapLatest { (lista, propriedade) ->
                if (propriedade == null) {
                    flowOf(Triple(lista, null, null))
                } else {
                    clima.observar(propriedade).map { previsao -> Triple(lista, propriedade, previsao) }
                }
            }
            combine(comPrevisao, ajustes.ajustes, _atualizando, _mensagem) { (lista, propriedade, previsao), aj, atualizando, mensagem ->
                montar(lista, propriedade, previsao, aj.limites, aj.ativo, atualizando, mensagem)
            }.collect { _uiState.value = it }
        }
    }

    fun aoSelecionarPropriedade(id: String) = SelecaoAtual.selecionar(id)

    /** Atualização manual: ignora a validade do cache. */
    fun aoAtualizar() {
        val id = _uiState.value.propriedadeId
        val propriedade = _uiState.value.propriedades.firstOrNull { it.id == id } ?: return
        // Ignora toques repetidos enquanto uma busca está em andamento.
        if (_atualizando.value) return
        buscar(propriedade, forcar = true)
    }

    private fun buscar(propriedade: Propriedade, forcar: Boolean) {
        val minha = ++geracao
        busca?.cancel()
        busca = viewModelScope.launch {
            _atualizando.value = true
            // finally cobre o cancelamento (troca de propriedade); sem ele
            // "atualizando" poderia ficar preso em true.
            try {
                val resultado = if (forcar) clima.atualizar(propriedade) else clima.atualizarSeNecessario(propriedade)
                if (minha == geracao) {
                    _mensagem.value = when (resultado) {
                        ResultadoAtualizacao.SemConexao -> MENSAGEM_SEM_INTERNET
                        is ResultadoAtualizacao.Falha -> MENSAGEM_FALHA
                        is ResultadoAtualizacao.Sucesso, null -> null
                    }
                }
            } finally {
                if (minha == geracao) _atualizando.value = false
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
        mensagem: String?
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
            mensagem = mensagem,
            avisoAtivo = avisoAtivo
        )
        if (previsao == null) return base

        val agora = Instant.ofEpochMilli(relogio())
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
            atualizadaEm = "Atualizada às ${previsao.obtidaEm.horaMinuto(zona)}"
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

    companion object {
        const val MENSAGEM_SEM_INTERNET = "Sem internet. Mostrando a última previsão salva."
        const val MENSAGEM_FALHA = "Não deu para atualizar a previsão agora. Tente de novo em instantes."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ClimaViewModel(Repositorios.propriedades, Repositorios.clima, Repositorios.ajustesAviso)
            }
        }
    }
}
