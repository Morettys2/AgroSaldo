package br.joao.agrosaldo.feature.propriedade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.joao.agrosaldo.core.util.mensagemParaUsuario
import br.joao.agrosaldo.core.util.repetirAposErro
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.domain.financeiro.PropriedadeComSaldo
import br.joao.agrosaldo.domain.financeiro.ResumoFinanceiro
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                // O comparativo (saldo, proporção e total) é regra do domínio; aqui só se junta.
                val comparativo = ResumoFinanceiro.comparativoDePropriedades(propriedades, lancamentos, mes)
                PropriedadeListaUiState(
                    carregando = false,
                    mes = mes,
                    itens = comparativo.itens,
                    totalCentavos = comparativo.totalCentavos
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
