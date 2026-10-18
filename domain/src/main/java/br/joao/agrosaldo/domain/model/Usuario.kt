package br.joao.agrosaldo.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Conta local do aparelho.
 *
 * [senhaHash] está no formato de [br.joao.agrosaldo.domain.conta.HashDeSenha];
 * a senha em texto puro nunca é armazenada.
 * [consentimentoEm] e [versaoTermo] registram o aceite LGPD (RNF10).
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
 * Versão do termo de privacidade. Incrementar a cada mudança relevante do
 * texto: quem aceitou uma versão anterior vê o termo de novo no próximo acesso.
 *
 * Histórico:
 * - 1: texto inicial (conta local e LGPD).
 * - 2: uso opcional da localização no cadastro da propriedade.
 * - 3: backup do Android e o que a exclusão da conta apaga.
 */
object TermoDePrivacidade {
    const val VERSAO_ATUAL = 3
}
