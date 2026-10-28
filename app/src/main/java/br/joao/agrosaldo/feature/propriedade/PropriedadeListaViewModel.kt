package br.joao.agrosaldo.feature.propriedade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.repetirAposErro
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.financeiro.ResumoFinanceiro
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Propriedade com o saldo do mês selecionado, em centavos. */
data class PropriedadeComSaldo(
    val propriedade: Propriedade,
    val saldoCentavos: Long,
    /** |saldo| relativo ao maior |saldo| entre as propriedades, em 0..1. */
    val proporcao: Float
)

data class PropriedadeListaUiState(
    val carregando: Boolean = true,
    /** Sem valor padrão: vem da seleção, que segue o relógio injetado. */
    val mes: YearMonth,
    val itens: List<PropriedadeComSaldo> = emptyList(),
    val totalCentavos: Long = 0L,
    val erro: String? = null
)

/**
 * Lista de propriedades com comparativo de saldo do mês. O saldo não é persistido:
 * é derivado dos lançamentos a cada emissão de propriedades, lançamentos ou mês.
 */
@HiltViewModel
class PropriedadeListaViewModel @Inject constructor(
    private val propriedadeRepository: PropriedadeRepository,
    private val lancamentoRepository: LancamentoRepository,
    /** Propriedade e mês escolhidos, compartilhados com as outras telas. */
    private val selecao: SelecaoAtual
) : ViewModel() {

    private val _uiState = MutableStateFlow(PropriedadeListaUiState(mes = selecao.mes.value))
    val uiState: StateFlow<PropriedadeListaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                propriedadeRepository.observarTodas(),
                lancamentoRepository.observarTodos(),
                selecao.mes
            ) { propriedades, lancamentos, mes ->

                val saldos = propriedades.map { propriedade ->
                    propriedade to ResumoFinanceiro.resumoDoMes(lancamentos, propriedade.id, mes).saldoCentavos
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
                .repetirAposErro { erro ->
                    _uiState.update { it.copy(carregando = false, erro = erro.mensagemParaUsuario(MENSAGEM_ERRO)) }
                }
                .collect { estado -> _uiState.value = estado }
        }
    }

    // Mês global em SelecaoAtual, compartilhado entre as abas.
    fun aoAvancarMes() = selecao.avancarMes()
    fun aoVoltarMes() = selecao.voltarMes()

    companion object {
        private const val MENSAGEM_ERRO = "Não foi possível carregar as propriedades. Tentando de novo…"
    }
}
