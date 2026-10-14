package br.joao.agrosaldo.feature.clima

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.di.dependencias
import br.joao.agrosaldo.domain.clima.LimitesChuva
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import br.joao.agrosaldo.domain.repository.AjustesAviso
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
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
class AjustesAvisoViewModel(
    private val ajustes: AjustesAvisoRepository,
    private val agenda: AgendaDeVerificacao,
    private val propriedades: PropriedadeRepository
) : ViewModel() {

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
    // lista atual (propriedades de outra conta ou exclusão desfeita).
    private val semAvisoAoAbrir = ajustes.ajustes.value.propriedadesSemAviso

    init {
        viewModelScope.launch {
            val lista = propriedades.observarTodas().first()
            _uiState.update { estado ->
                estado.copy(sitios = lista.map { SitioDoAviso(it.id, it.nome, avisa = it.id !in semAvisoAoAbrir) })
            }
        }
    }

    fun aoMudarAtivo(ativo: Boolean) = _uiState.update { it.copy(ativo = ativo) }

    /** Arredonda para múltiplo de 5, limitado a [PROBABILIDADE_MIN]..[PROBABILIDADE_MAX]. */
    fun aoMudarProbabilidade(valor: Float) = _uiState.update {
        val arredondado = ((valor / 5f).roundToInt() * 5).coerceIn(PROBABILIDADE_MIN, PROBABILIDADE_MAX)
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
        const val PROBABILIDADE_MIN = 30
        const val PROBABILIDADE_MAX = 90
        const val CHUVA_MIN = 1.0
        const val CHUVA_MAX = 10.0

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AjustesAvisoViewModel(dependencias.ajustesAviso, dependencias.agendaAviso, dependencias.propriedades) }
        }
    }
}
