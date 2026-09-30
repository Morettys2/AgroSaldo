package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.model.Usuario
import kotlinx.coroutines.flow.Flow

/** Contrato de acesso às contas de usuário guardadas no aparelho. */
interface UsuarioRepository {

    fun observar(id: String): Flow<Usuario?>

    suspend fun buscarPorId(id: String): Usuario?

    /** O e-mail chega aqui já normalizado (minúsculo, sem espaços). */
    suspend fun buscarPorEmail(email: String): Usuario?

    /**
     * Grava a conta nova. Devolve false se o e-mail já estiver em uso.
     *
     * As propriedades que já existiam no aparelho sem dono — de antes do
     * login existir, ou os dados de exemplo — passam a ser desta conta.
     * Sem isso, quem atualizasse o app perderia de vista tudo o que já tinha
     * cadastrado.
     */
    suspend fun criar(usuario: Usuario): Boolean

    /** Troca só o hash da senha (alteração de senha e atualização do custo do hash). */
    suspend fun atualizarSenha(id: String, senhaHash: String)

    /** Apaga a conta e, em cascata, as propriedades e os lançamentos dela. */
    suspend fun excluir(id: String)
}
