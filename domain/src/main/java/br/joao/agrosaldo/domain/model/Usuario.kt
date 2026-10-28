package br.joao.agrosaldo.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Modelo de domínio da conta local. [senhaHash] está no formato de
 * [br.joao.agrosaldo.domain.conta.HashDeSenha] (a senha em texto puro nunca é guardada);
 * [consentimentoEm] e [versaoTermo] registram o aceite do termo de privacidade (LGPD).
 * O `init` valida os campos obrigatórios e o tamanho de nome e e-mail.
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
        require(nome.length <= NOME_MAXIMO) { "O nome pode ter no máximo $NOME_MAXIMO caracteres." }
        require(email.length <= EMAIL_MAXIMO) { "O e-mail pode ter no máximo $EMAIL_MAXIMO caracteres." }
    }

    companion object {
        /** Limites validados no `init`. */
        const val NOME_MAXIMO = 80

        /** Tamanho máximo de um endereço de e-mail (RFC 5321). */
        const val EMAIL_MAXIMO = 254
    }
}

/**
 * Versão atual do termo de privacidade. Ao subir o número, quem aceitou uma versão anterior
 * precisa aceitar de novo no próximo acesso.
 */
object TermoDePrivacidade {
    const val VERSAO_ATUAL = 3
}
