package br.joao.agrosaldo.feature.clima

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.domain.clima.LimitesChuva
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import br.joao.agrosaldo.domain.repository.AjustesAviso
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Um sítio na lista "Avisar sobre", com o interruptor dele. */
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
     * Com um sítio só, o interruptor geral já resolve: a lista não aparece.
     * A não ser que esse sítio esteja desligado (o outro foi excluído depois):
     * aí a lista aparece, senão não haveria como religar.
     */
    val mostrarSitios: Boolean get() = sitios.size >= 2 || sitios.any { !it.avisa }
}

/**
 * ViewModel da tela 18 — Aviso de chuva.
 *
 * Os controles mexem só no estado da tela; nada é gravado até tocar em
 * "Salvar". Assim dá para brincar com os valores e desistir voltando.
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

    // Ids que estavam desligados quando a tela abriu. Guardados à parte para
    // não perder o de um sítio que não está na lista (outra conta, ou apagado
    // e desfeito depois).
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

    /** O controle deslizante manda Float; aqui vira múltiplo de 5 (55%, 60%, 65%...). */
    fun aoMudarProbabilidade(valor: Float) = _uiState.update {
        val arredondado = ((valor / 5f).roundToInt() * 5).coerceIn(PROBABILIDADE_MIN, PROBABILIDADE_MAX)
        it.copy(probabilidadeMinima = arredondado)
    }

    /** Passos de meio milímetro. */
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
        // Desligado de verdade: nada roda em segundo plano, nem gasta bateria.
        if (estado.ativo) agenda.ligar() else agenda.desligar()
        _uiState.update { it.copy(salvo = true) }
    }

    /** Ferramenta de teste: dispara a verificação de verdade agora. */
    fun aoVerificarAgora() = agenda.verificarAgora()

    companion object {
        const val PROBABILIDADE_MIN = 30
        const val PROBABILIDADE_MAX = 90
        const val CHUVA_MIN = 1.0
        const val CHUVA_MAX = 10.0

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AjustesAvisoViewModel(Repositorios.ajustesAviso, Repositorios.agendaAviso, Repositorios.propriedades) }
        }
    }
}
