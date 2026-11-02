package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.local.dao.UsuarioDao
import br.joao.agrosaldo.data.local.paraDominio
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.domain.model.Usuario
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Implementação de [UsuarioRepository] com Room. Uma conta nova começa sem propriedades. */
class UsuarioRepositoryRoom(private val dao: UsuarioDao) : UsuarioRepository {

    override fun observar(id: String): Flow<Usuario?> = dao.observar(id).map { it?.paraDominio() }

    override suspend fun buscarPorId(id: String): Usuario? = dao.buscarPorId(id)?.paraDominio()

    override suspend fun buscarPorEmail(email: String): Usuario? = dao.buscarPorEmail(email)?.paraDominio()

    // -1: OnConflictStrategy.IGNORE descartou a linha (e-mail duplicado).
    override suspend fun criar(usuario: Usuario): Boolean = dao.inserir(usuario.paraEntidade()) != -1L

    override suspend fun atualizarSenha(id: String, senhaHash: String) = dao.atualizarSenha(id, senhaHash)

    override suspend fun registrarConsentimento(id: String, em: Instant, versao: Int) =
        dao.registrarConsentimento(id, em.toEpochMilli(), versao)

    override suspend fun excluir(id: String) = dao.excluir(id)

    override suspend fun contar(): Int = dao.contar()
}
