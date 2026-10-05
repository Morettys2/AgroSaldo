package br.joao.agrosaldo.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * O "olho" do Início: esconde os valores em dinheiro do app inteiro.
 *
 * Serve para abrir o app perto de outras pessoas — no banco, na cooperativa,
 * mostrando a previsão para um vizinho — sem expor o saldo. Fica gravado:
 * quem escondeu uma vez continua com os valores escondidos ao voltar.
 */
interface PrivacidadeRepository {
    val valoresOcultos: StateFlow<Boolean>
    fun definir(ocultos: Boolean)
}
