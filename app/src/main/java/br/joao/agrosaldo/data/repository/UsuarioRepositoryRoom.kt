package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.local.dao.UsuarioDao
import br.joao.agrosaldo.data.local.paraDominio
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.domain.model.Usuario
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UsuarioRepositoryRoom(
    private val dao: UsuarioDao
) : UsuarioRepository {

    override fun observar(id: String): Flow<Usuario?> =
        dao.observar(id).map { it?.paraDominio() }

    override suspend fun buscarPorId(id: String): Usuario? =
        dao.buscarPorId(id)?.paraDominio()

    override suspend fun buscarPorEmail(email: String): Usuario? =
        dao.buscarPorEmail(email)?.paraDominio()

    override suspend fun criar(usuario: Usuario): Boolean {
        // -1 é o jeito do Room dizer "o IGNORE não inseriu": e-mail repetido.
        val inserido = dao.inserir(usuario.paraEntidade()) != -1L
        if (inserido) dao.adotarPropriedadesSemDono(usuario.id)
        return inserido
    }

    override suspend fun atualizarSenha(id: String, senhaHash: String) =
        dao.atualizarSenha(id, senhaHash)

    override suspend fun excluir(id: String) =
        dao.excluir(id)
}
