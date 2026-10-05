package br.joao.agrosaldo.feature.propriedade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.pertenceAo
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.abs

/** Uma propriedade com o resultado dela no mês — o que o comparativo precisa. */
data class PropriedadeComSaldo(
    val propriedade: Propriedade,
    val saldoCentavos: Long,
    /** Tamanho da barra, relativo ao maior saldo em módulo. De 0 a 1. */
    val proporcao: Float
)

data class PropriedadeListaUiState(
    val carregando: Boolean = true,
    val mes: YearMonth = YearMonth.now(),
    val itens: List<PropriedadeComSaldo> = emptyList(),
    val totalCentavos: Long = 0L,
    val erro: String? = null
)

/**
 * ViewModel da tela de propriedades — comparativo entre elas (RF11).
 *
 * Nenhuma propriedade guarda o próprio saldo. Ele é calculado aqui a partir
 * dos lançamentos do mês, e recalculado sozinho sempre que um lançamento
 * muda em qualquer tela do app.
 *
 * Guardar o saldo dentro da propriedade seria duplicar informação: bastaria
 * esquecer de atualizar num lugar para os números discordarem.
 */
class PropriedadeListaViewModel(
    private val propriedadeRepository: PropriedadeRepository,
    private val lancamentoRepository: LancamentoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PropriedadeListaUiState())
    val uiState: StateFlow<PropriedadeListaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                propriedadeRepository.observarTodas(),
                lancamentoRepository.observarTodos(),
                SelecaoAtual.mes
            ) { propriedades, lancamentos, mes ->

                val saldos = propriedades.map { propriedade ->
                    propriedade to lancamentos
                        .filter { it.propriedadeId == propriedade.id && it.data.pertenceAo(mes) }
                        .sumOf { it.valorComSinal }
                }

                val maior = saldos.maxOfOrNull { abs(it.second) }?.takeIf { it > 0 } ?: 1L

                PropriedadeListaUiState(
                    carregando = false,
                    mes = mes,
                    itens = saldos.map { (propriedade, saldo) ->
                        PropriedadeComSaldo(
                            propriedade = propriedade,
                            saldoCentavos = saldo,
                            proporcao = abs(saldo).toFloat() / maior
                        )
                    },
                    totalCentavos = saldos.sumOf { it.second }
                )
            }
                .catch { e ->
                    _uiState.update {
                        it.copy(carregando = false, erro = e.message ?: "Erro ao carregar")
                    }
                }
                .collect { estado -> _uiState.value = estado }
        }
    }

    // O mês é o mesmo em todas as abas: mudar aqui muda lá também.
    fun aoAvancarMes() = SelecaoAtual.avancarMes()
    fun aoVoltarMes() = SelecaoAtual.voltarMes()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PropriedadeListaViewModel(
                    propriedadeRepository = Repositorios.propriedades,
                    lancamentoRepository = Repositorios.lancamentos
                )
            }
        }
    }
}
