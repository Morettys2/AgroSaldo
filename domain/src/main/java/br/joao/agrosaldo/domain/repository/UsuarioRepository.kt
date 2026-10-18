package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.model.Usuario
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Contas locais do aparelho. */
interface UsuarioRepository {

    fun observar(id: String): Flow<Usuario?>

    suspend fun buscarPorId(id: String): Usuario?

    /** [email] deve vir normalizado ([br.joao.agrosaldo.domain.conta.RegrasDeConta.normalizarEmail]). */
    suspend fun buscarPorEmail(email: String): Usuario?

    /**
     * Insere a conta; retorna false se o e-mail já existir.
     *
     * Propriedades sem dono (criadas antes da existência de contas ou dados de
     * exemplo) são atribuídas a esta conta.
     */
    suspend fun criar(usuario: Usuario): Boolean

    /** Substitui apenas o hash (troca de senha ou rehash com custo maior). */
    suspend fun atualizarSenha(id: String, senhaHash: String)

    /** Registra o aceite de uma nova versão do termo. */
    suspend fun registrarConsentimento(id: String, em: Instant, versao: Int)

    /** Remove a conta e, em cascata, suas propriedades e lançamentos. */
    suspend fun excluir(id: String)
}
