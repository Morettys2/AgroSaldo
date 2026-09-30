package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.domain.model.Usuario
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Fake para os testes, no mesmo estilo dos outros repositórios em memória. */
class UsuarioRepositoryMemoria(
    iniciais: List<Usuario> = emptyList()
) : UsuarioRepository {

    private val _usuarios = MutableStateFlow(iniciais)

    val todos: List<Usuario> get() = _usuarios.value

    override fun observar(id: String): Flow<Usuario?> =
        _usuarios.map { lista -> lista.firstOrNull { it.id == id } }

    override suspend fun buscarPorId(id: String): Usuario? =
        _usuarios.value.firstOrNull { it.id == id }

    override suspend fun buscarPorEmail(email: String): Usuario? =
        _usuarios.value.firstOrNull { it.email == email }

    override suspend fun criar(usuario: Usuario): Boolean {
        if (_usuarios.value.any { it.email == usuario.email || it.id == usuario.id }) return false
        _usuarios.update { it + usuario }
        return true
    }

    override suspend fun atualizarSenha(id: String, senhaHash: String) {
        _usuarios.update { lista -> lista.map { if (it.id == id) it.copy(senhaHash = senhaHash) else it } }
    }

    override suspend fun excluir(id: String) {
        _usuarios.update { lista -> lista.filterNot { it.id == id } }
    }
}
