package br.joao.agrosaldo.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Quem usa o app neste aparelho.
 *
 * A senha nunca aparece aqui: só o [senhaHash], que é o resultado do
 * PBKDF2 (veja [br.joao.agrosaldo.domain.conta.HashDeSenha]). Nem o próprio
 * app consegue descobrir a senha a partir dele.
 *
 * [consentimentoEm] e [versaoTermo] são a prova do aceite da LGPD (RNF10):
 * quando o usuário aceitou e qual versão do texto ele leu. Se o texto mudar,
 * a versão sobe e dá para saber quem aceitou a versão antiga.
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
 * Versão do texto de privacidade.
 *
 * Toda vez que o texto muda de um jeito que importa para o usuário, a versão
 * sobe — e quem aceitou a anterior vê o termo de novo no próximo acesso.
 *
 *   1 — texto inicial (login e LGPD)
 *   2 — o app passa a poder usar a localização do celular, só quando a
 *       pessoa toca em "Usar minha localização" no cadastro do sítio
 */
object TermoDePrivacidade {
    const val VERSAO_ATUAL = 2
}
