package br.joao.agrosaldo.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Modelo de domínio da conta local. [senhaHash] está no formato de
 * [br.joao.agrosaldo.domain.conta.HashDeSenha] (a senha em texto puro nunca é guardada);
 * [consentimentoEm] e [versaoTermo] registram o aceite do termo de privacidade (LGPD).
 * O `init` valida os campos obrigatórios.
 */
data class Usuario(
    val id: String = UUID.randomUUID().toString(),
    val nome: String,
    val email: String,
    val senhaHash: String,
    val consentimentoEm: Instant,
    val versaoTermo: Int,
    val criadoEm: Instant
) {
    init {
        require(nome.isNotBlank()) { "O nome não pode ser vazio." }
        require(email.isNotBlank()) { "O e-mail não pode ser vazio." }
        require(senhaHash.isNotBlank()) { "A senha precisa estar protegida." }
    }
}

/**
 * Versão atual do termo de privacidade. Ao subir o número, quem aceitou uma versão anterior
 * precisa aceitar de novo no próximo acesso.
 */
object TermoDePrivacidade {
    const val VERSAO_ATUAL = 3
}
