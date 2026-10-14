package br.joao.agrosaldo.data.preferencias

import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import br.joao.agrosaldo.domain.repository.AjustesAviso
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository

/** [ArmazenamentoDeAjustes] em um mapa, para testes. */
class ArmazenamentoMemoria(iniciais: Map<String, Any> = emptyMap()) : ArmazenamentoDeAjustes {
    val dados: MutableMap<String, Any> = iniciais.toMutableMap()

    override fun todos(): Map<String, Any?> = dados.toMap()

    override fun editar(gravar: Map<String, Any>, remover: Collection<String>) {
        remover.forEach { dados.remove(it) }
        dados.putAll(gravar)
    }
}

/**
 * Fake em memória para testes: [AjustesAvisoPorConta] sobre [ArmazenamentoMemoria]. Sem [sessao]
 * informada, os dados são de uma conta fixa, já conectada, que começa com os ajustes [inicial].
 */
class AjustesAvisoMemoria private constructor(private val porConta: AjustesAvisoPorConta) : AjustesAvisoRepository by porConta {

    constructor(
        inicial: AjustesAviso = AjustesAviso(),
        sessao: SessaoRepository = SessaoMemoria(CONTA_PADRAO)
    ) : this(AjustesAvisoPorConta(ArmazenamentoMemoria(), sessao)) {
        porConta.salvar(inicial)
    }

    private companion object {
        const val CONTA_PADRAO = "conta-de-teste"
    }
}

/** Fake que só registra as chamadas recebidas. */
class AgendaFalsa : AgendaDeVerificacao {
    var ligada: Boolean? = null
        private set

    override fun ligar() {
        ligada = true
    }

    override fun desligar() {
        ligada = false
    }

    var verificacoesManuais = 0
        private set

    override fun verificarAgora() {
        verificacoesManuais++
    }
}
