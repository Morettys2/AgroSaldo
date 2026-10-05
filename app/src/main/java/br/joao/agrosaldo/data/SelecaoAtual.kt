package br.joao.agrosaldo.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.YearMonth

/**
 * O que o produtor está olhando agora: qual propriedade e qual mês.
 *
 * Início, Lançamentos, Sítios e Clima precisam concordar sobre isso. Escolher
 * setembro no Início e abrir Lançamentos tem que mostrar setembro — por isso o
 * dado não pode viver dentro de um ViewModel, cada tela tem o seu.
 *
 * A propriedade escolhida é lembrada entre uma abertura e outra do app (quem
 * tem dois sítios não quer escolher o mesmo toda vez). O mês, não: abrir o app
 * sempre começa no mês corrente, que é o que a pessoa quer ver primeiro.
 *
 * Este objeto não sabe ONDE a escolha é guardada. Quem sabe é o
 * [Repositorios], que chama [configurar] ao abrir o app. Assim ele continua
 * Kotlin puro e os testes rodam sem Android.
 */
object SelecaoAtual {

    private val _propriedadeId = MutableStateFlow<String?>(null)
    val propriedadeId: StateFlow<String?> = _propriedadeId.asStateFlow()

    private val _mes = MutableStateFlow(YearMonth.now())
    val mes: StateFlow<YearMonth> = _mes.asStateFlow()

    private var aoMudarPropriedade: (String?) -> Unit = {}

    /**
     * Chamado uma vez, ao abrir o app.
     *
     * @param inicial a propriedade que estava escolhida da última vez.
     * @param aoMudar onde gravar cada nova escolha.
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

    /** Ao trocar de conta: a propriedade escolhida era do usuário anterior. */
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
