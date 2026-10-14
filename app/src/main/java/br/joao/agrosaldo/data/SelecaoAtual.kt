package br.joao.agrosaldo.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.YearMonth

/**
 * Propriedade e mês selecionados, compartilhados entre as telas: escolher a propriedade
 * no Início vale também em Lançamentos, Clima e Histórico.
 *
 * Existe UMA instância por app, criada em [br.joao.agrosaldo.di.Dependencias] e passada
 * no construtor de cada ViewModel que precisa dela. Antes era um `object` global, que os
 * ViewModels acessavam direto e os testes precisavam zerar à mão; como classe, cada teste
 * cria a sua e nenhum estado vaza de um teste para outro.
 *
 * A propriedade é persistida pelo callback [aoMudarPropriedade]; o mês não é persistido,
 * começa no mês corrente e acompanha a virada do mês via [acompanharMesCorrente].
 *
 * @param propriedadeInicial id gravado da última seleção.
 * @param aoMudarPropriedade chamado a cada mudança de propriedade (para gravar).
 */
class SelecaoAtual(
    propriedadeInicial: String? = null,
    private val aoMudarPropriedade: (String?) -> Unit = {},
    mesInicial: YearMonth = YearMonth.now()
) {

    private val _propriedadeId = MutableStateFlow(propriedadeInicial)
    val propriedadeId: StateFlow<String?> = _propriedadeId.asStateFlow()

    private val _mes = MutableStateFlow(mesInicial)
    val mes: StateFlow<YearMonth> = _mes.asStateFlow()

    /** Mês do calendário na última sincronização; [mes] igual a ele significa que ninguém escolheu outro. */
    private var mesCorrente: YearMonth = mesInicial

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
