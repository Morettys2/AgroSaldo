package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.local.Transacao
import br.joao.agrosaldo.data.local.dao.UsuarioDao
import br.joao.agrosaldo.data.local.paraDominio
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.domain.model.Usuario
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementação de [UsuarioRepository] com Room. O cadastro roda na [transacao]: a conta e a
 * atribuição das propriedades sem dono entram juntas, ou nenhuma entra.
 */
class UsuarioRepositoryRoom(private val dao: UsuarioDao, private val transacao: Transacao) : UsuarioRepository {

    override fun observar(id: String): Flow<Usuario?> = dao.observar(id).map { it?.paraDominio() }

    override suspend fun buscarPorId(id: String): Usuario? = dao.buscarPorId(id)?.paraDominio()

    override suspend fun buscarPorEmail(email: String): Usuario? = dao.buscarPorEmail(email)?.paraDominio()

    override suspend fun criar(usuario: Usuario): Boolean = transacao.executar {
        // -1: OnConflictStrategy.IGNORE descartou a linha (e-mail duplicado).
        val inserido = dao.inserir(usuario.paraEntidade()) != -1L
        if (inserido) dao.adotarPropriedadesSemDono(usuario.id)
        inserido
    }

    override suspend fun atualizarSenha(id: String, senhaHash: String) = dao.atualizarSenha(id, senhaHash)

    override suspend fun registrarConsentimento(id: String, em: Instant, versao: Int) =
        dao.registrarConsentimento(id, em.toEpochMilli(), versao)

    override suspend fun excluir(id: String) = dao.excluir(id)
}
