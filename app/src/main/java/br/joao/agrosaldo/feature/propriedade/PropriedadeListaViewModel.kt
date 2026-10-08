package br.joao.agrosaldo.feature.propriedade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.pertenceAo
import br.joao.agrosaldo.data.Repositorios
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.abs

/** Propriedade com o saldo do mês selecionado, em centavos. */
data class PropriedadeComSaldo(
    val propriedade: Propriedade,
    val saldoCentavos: Long,
    /** |saldo| relativo ao maior |saldo| entre as propriedades, em 0..1. */
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
 * Lista de propriedades com comparativo de saldo do mês (RF11). O saldo não é persistido:
 * é derivado dos lançamentos a cada emissão de propriedades, lançamentos ou mês.
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
                // catch encerraria o Flow e a tela pararia de atualizar: mostra o erro e assina de novo.
                .retryWhen { erro, tentativa ->
                    _uiState.update {
                        it.copy(carregando = false, erro = erro.mensagemParaUsuario(MENSAGEM_ERRO))
                    }
                    // Espera crescente (1 s, 2 s, 4 s... até 30 s) para um erro persistente não virar laço.
                    delay(minOf(ESPERA_INICIAL_MS shl tentativa.coerceAtMost(5L).toInt(), ESPERA_MAXIMA_MS))
                    true
                }
                .collect { estado -> _uiState.value = estado }
        }
    }

    // Mês global em SelecaoAtual, compartilhado entre as abas.
    fun aoAvancarMes() = SelecaoAtual.avancarMes()
    fun aoVoltarMes() = SelecaoAtual.voltarMes()

    companion object {
        private const val MENSAGEM_ERRO = "Não foi possível carregar as propriedades. Tentando de novo…"
        private const val ESPERA_INICIAL_MS = 1_000L
        private const val ESPERA_MAXIMA_MS = 30_000L

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
