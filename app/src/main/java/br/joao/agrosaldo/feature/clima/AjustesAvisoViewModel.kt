package br.joao.agrosaldo.feature.clima

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.joao.agrosaldo.core.util.tentar
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.clima.AvaliacaoChuva
import br.joao.agrosaldo.domain.clima.AvisoDeChuva
import br.joao.agrosaldo.domain.clima.LimitesChuva
import br.joao.agrosaldo.domain.clima.MotivoChuva
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import br.joao.agrosaldo.domain.repository.AjustesAviso
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Propriedade na lista de avisos, com o estado do seu interruptor. */
data class SitioDoAviso(val id: String, val nome: String, val avisa: Boolean)

data class AjustesAvisoUiState(
    val ativo: Boolean = true,
    val probabilidadeMinima: Int = 60,
    val chuvaMinimaMm: Double = 2.0,
    val janelaHoras: Int = 12,
    val sitios: List<SitioDoAviso> = emptyList(),
    val salvo: Boolean = false
) {
    /**
     * A lista só aparece com 2+ propriedades, ou se alguma estiver desligada
     * (caso contrário não haveria como religá-la após excluir as demais).
     */
    val mostrarSitios: Boolean get() = sitios.size >= 2 || sitios.any { !it.avisa }
}

/**
 * Ajustes do aviso de chuva. Alterações ficam só no estado da UI até [aoSalvar];
 * sair sem salvar descarta tudo.
 */
@HiltViewModel
class AjustesAvisoViewModel @Inject constructor(
    private val ajustes: AjustesAvisoRepository,
    private val agenda: AgendaDeVerificacao,
    private val propriedades: PropriedadeRepository,
    private val selecao: SelecaoAtual,
    private val relogio: Clock
) : ViewModel() {

    /**
     * Aviso com dados fictícios para a notificação de exemplo (ferramenta de teste do debug). Usa a
     * propriedade escolhida no app, para o toque abrir o Clima como num aviso real, e o relógio injetado.
     */
    fun avisoDeExemplo(): AvisoDeChuva = AvisoDeChuva(
        propriedadeId = selecao.propriedadeId.value ?: "exemplo",
        nomePropriedade = "Propriedade de exemplo",
        avaliacao = AvaliacaoChuva(
            vaiChover = true,
            probabilidadeMaxima = PROBABILIDADE_DO_EXEMPLO,
            chuvaTotalMm = CHUVA_DO_EXEMPLO_MM,
            inicioPrevisto = LocalDateTime.now(relogio).truncatedTo(ChronoUnit.HOURS).plusHours(HORAS_ATE_A_CHUVA_DE_EXEMPLO),
            motivo = MotivoChuva.AMBOS,
            temDados = true
        )
    )

    private val _uiState = MutableStateFlow(
        ajustes.ajustes.value.let {
            AjustesAvisoUiState(
                ativo = it.ativo,
                probabilidadeMinima = it.limites.probabilidadeMinima,
                chuvaMinimaMm = it.limites.chuvaMinimaMm,
                janelaHoras = it.limites.janelaHoras
            )
        }
    )
    val uiState: StateFlow<AjustesAvisoUiState> = _uiState.asStateFlow()

    // Ids desligados ao abrir. Preservados no salvamento mesmo que não estejam na
    // lista atual (lista que não carregou ou exclusão desfeita).
    private val semAvisoAoAbrir = ajustes.ajustes.value.propriedadesSemAviso

    init {
        viewModelScope.launch {
            // Banco travado: a lista fica vazia, e salvar preserva os sítios desligados (ver aoSalvar).
            val lista = tentar { propriedades.observarTodas().first() }.getOrDefault(emptyList())
            _uiState.update { estado ->
                estado.copy(sitios = lista.map { SitioDoAviso(it.id, it.nome, avisa = it.id !in semAvisoAoAbrir) })
            }
        }
    }

    fun aoMudarAtivo(ativo: Boolean) = _uiState.update { it.copy(ativo = ativo) }

    /** Arredonda para múltiplo de 5, limitado a [PROBABILIDADE_MIN]..[PROBABILIDADE_MAX]. */
    fun aoMudarProbabilidade(valor: Float) = _uiState.update {
        val arredondado = ((valor / PASSO_PROBABILIDADE.toFloat()).roundToInt() * PASSO_PROBABILIDADE).coerceIn(
            PROBABILIDADE_MIN,
            PROBABILIDADE_MAX
        )
        it.copy(probabilidadeMinima = arredondado)
    }

    /** Arredonda para passos de 0,5 mm, limitado a [CHUVA_MIN]..[CHUVA_MAX]. */
    fun aoMudarChuva(valor: Float) = _uiState.update {
        val arredondado = ((valor * 2).roundToInt() / 2.0).coerceIn(CHUVA_MIN, CHUVA_MAX)
        it.copy(chuvaMinimaMm = arredondado)
    }

    fun aoMudarSitio(id: String, avisa: Boolean) = _uiState.update { estado ->
        estado.copy(sitios = estado.sitios.map { if (it.id == id) it.copy(avisa = avisa) else it })
    }

    fun aoSalvar() {
        val estado = _uiState.value
        val idsNaTela = estado.sitios.map { it.id }.toSet()
        val semAviso = semAvisoAoAbrir.filterNot { it in idsNaTela }.toSet() +
            estado.sitios.filterNot { it.avisa }.map { it.id }
        ajustes.salvar(
            AjustesAviso(
                ativo = estado.ativo,
                limites = LimitesChuva(
                    probabilidadeMinima = estado.probabilidadeMinima,
                    chuvaMinimaMm = estado.chuvaMinimaMm,
                    janelaHoras = estado.janelaHoras
                ),
                propriedadesSemAviso = semAviso
            )
        )
        // Desativado cancela o trabalho periódico em segundo plano.
        if (estado.ativo) agenda.ligar() else agenda.desligar()
        _uiState.update { it.copy(salvo = true) }
    }

    /** Dispara a verificação de chuva imediatamente (uso de teste/diagnóstico). */
    fun aoVerificarAgora() = agenda.verificarAgora()

    companion object {
        const val PASSO_PROBABILIDADE = 5
        const val PROBABILIDADE_MIN = 30
        const val PROBABILIDADE_MAX = 90
        const val CHUVA_MIN = 1.0
        const val CHUVA_MAX = 10.0

        // Valores do aviso de exemplo.
        private const val PROBABILIDADE_DO_EXEMPLO = 85
        private const val CHUVA_DO_EXEMPLO_MM = 6.0
        private const val HORAS_ATE_A_CHUVA_DE_EXEMPLO = 3L
    }
}
