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
 * [Repositorios]; o mês não é persistido, começa no mês corrente e acompanha a
 * virada do mês via [acompanharMesCorrente].
 */
object SelecaoAtual {

    private val _propriedadeId = MutableStateFlow<String?>(null)
    val propriedadeId: StateFlow<String?> = _propriedadeId.asStateFlow()

    private val _mes = MutableStateFlow(YearMonth.now())
    val mes: StateFlow<YearMonth> = _mes.asStateFlow()

    /** Mês do calendário na última sincronização; [mes] igual a ele significa que ninguém escolheu outro. */
    private var mesCorrente: YearMonth = _mes.value

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

    /**
     * Virada do mês com o processo vivo: se o calendário avançou desde a última chamada e a
     * seleção estava no mês corrente de então, passa para [agora]. Um mês escolhido pela
     * pessoa é mantido. Chamado ao voltar para as telas (ON_RESUME).
     */
    fun acompanharMesCorrente(agora: YearMonth = YearMonth.now()) {
        if (agora <= mesCorrente) return
        if (_mes.value == mesCorrente) _mes.value = agora
        mesCorrente = agora
    }

    /** Reseta a seleção (troca de conta). */
    fun limpar(agora: YearMonth = YearMonth.now()) {
        mudarPropriedade(null)
        mesCorrente = agora
        _mes.value = agora
    }

    private fun mudarPropriedade(id: String?) {
        if (_propriedadeId.value == id) return
        _propriedadeId.value = id
        aoMudarPropriedade(id)
    }
}
