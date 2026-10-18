package br.joao.agrosaldo.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Exigência de autenticação do aparelho (biometria ou credencial do sistema)
 * ao abrir o app. Ajuste por aparelho, não por conta; excluído do backup.
 */
interface BloqueioRepository {
    val ativo: StateFlow<Boolean>
    fun definir(ativo: Boolean)
}
