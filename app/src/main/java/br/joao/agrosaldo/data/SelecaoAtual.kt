package br.joao.agrosaldo.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.YearMonth

/**
 * Propriedade e mês selecionados, compartilhados entre as telas (fora de
 * ViewModel para que todas vejam a mesma seleção).
 *
 * A propriedade é persistida via callback registrado em [configurar] pelo
 * [Repositorios]; o mês não é persistido e sempre começa no mês corrente.
 */
object SelecaoAtual {

    private val _propriedadeId = MutableStateFlow<String?>(null)
    val propriedadeId: StateFlow<String?> = _propriedadeId.asStateFlow()

    private val _mes = MutableStateFlow(YearMonth.now())
    val mes: StateFlow<YearMonth> = _mes.asStateFlow()

    private var aoMudarPropriedade: (String?) -> Unit = {}

    /**
     * Chamado uma vez na inicialização.
     *
     * @param inicial id persistido da última seleção.
     * @param aoMudar callback de persistência, chamado a cada mudança.
     */
    fun configurar(inicial: String?, aoMudar: (String?) -> Unit) {
        _propriedadeId.value = inicial
        aoMudarPropriedade = aoMudar
    }

    fun selecionar(id: String) = mudarPropriedade(id)

    fun limparSeFor(id: String) {
        if (_propriedadeId.value == id) mudarPropriedade(null)
    }

    fun selecionarMes(mes: YearMonth) {
        _mes.value = mes
    }

    fun avancarMes() {
        _mes.value = _mes.value.plusMonths(1)
    }

    fun voltarMes() {
        _mes.value = _mes.value.minusMonths(1)
    }

    /** Reseta a seleção (troca de conta). */
    fun limpar() {
        mudarPropriedade(null)
        _mes.value = YearMonth.now()
    }

    private fun mudarPropriedade(id: String?) {
        if (_propriedadeId.value == id) return
        _propriedadeId.value = id
        aoMudarPropriedade(id)
    }
}
