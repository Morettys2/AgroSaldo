package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.model.Usuario
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Contrato de acesso às contas locais; a implementação com Room fica no app. */
interface UsuarioRepository {

    /** Observa a conta pelo id. */
    fun observar(id: String): Flow<Usuario?>

    /** Lê a conta pelo id. */
    suspend fun buscarPorId(id: String): Usuario?

    /** [email] deve vir normalizado ([br.joao.agrosaldo.domain.conta.RegrasDeConta.normalizarEmail]). */
    suspend fun buscarPorEmail(email: String): Usuario?

    /**
     * Insere a conta; devolve false se o e-mail já existe. Na mesma transação, atribui à conta
     * as propriedades sem dono.
     */
    suspend fun criar(usuario: Usuario): Boolean

    /** Substitui só o hash da senha (troca de senha ou novo hash com custo maior). */
    suspend fun atualizarSenha(id: String, senhaHash: String)

    /** Registra o aceite de uma versão do termo de privacidade. */
    suspend fun registrarConsentimento(id: String, em: Instant, versao: Int)

    /** Remove a conta e, em cascata, suas propriedades e lançamentos. */
    suspend fun excluir(id: String)
}
