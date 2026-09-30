package br.joao.agrosaldo.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Qual propriedade o usuário está olhando agora.
 *
 * Tela Início e tela de Lançamentos precisam concordar sobre isso, então o
 * dado não pode viver dentro de um ViewModel — cada tela tem o seu.
 *
 * É exatamente o tipo de informação que vai para o DataStore na fase 5
 * (preferência do usuário, sobrevive ao app fechar). Por enquanto vive em
 * memória, com a mesma interface de Flow — trocar depois não afeta ninguém.
 */
object SelecaoAtual {

    private val _propriedadeId = MutableStateFlow<String?>(null)
    val propriedadeId: StateFlow<String?> = _propriedadeId.asStateFlow()

    fun selecionar(id: String) {
        _propriedadeId.value = id
    }

    fun limparSeFor(id: String) {
        if (_propriedadeId.value == id) _propriedadeId.value = null
    }
}
