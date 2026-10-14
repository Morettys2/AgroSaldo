package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.domain.model.Usuario
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Fake em memória para testes. */
class UsuarioRepositoryMemoria(iniciais: List<Usuario> = emptyList()) : UsuarioRepository {

    private val usuariosGuardados = MutableStateFlow(iniciais)

    val todos: List<Usuario> get() = usuariosGuardados.value

    override fun observar(id: String): Flow<Usuario?> = usuariosGuardados.map { lista -> lista.firstOrNull { it.id == id } }

    override suspend fun buscarPorId(id: String): Usuario? = usuariosGuardados.value.firstOrNull { it.id == id }

    override suspend fun buscarPorEmail(email: String): Usuario? = usuariosGuardados.value.firstOrNull { it.email == email }

    override suspend fun criar(usuario: Usuario): Boolean {
        if (usuariosGuardados.value.any { it.email == usuario.email || it.id == usuario.id }) return false
        usuariosGuardados.update { it + usuario }
        return true
    }

    override suspend fun atualizarSenha(id: String, senhaHash: String) {
        usuariosGuardados.update { lista -> lista.map { if (it.id == id) it.copy(senhaHash = senhaHash) else it } }
    }

    override suspend fun registrarConsentimento(id: String, em: Instant, versao: Int) {
        usuariosGuardados.update { lista ->
            lista.map { if (it.id == id) it.copy(consentimentoEm = em, versaoTermo = versao) else it }
        }
    }

    override suspend fun excluir(id: String) {
        usuariosGuardados.update { lista -> lista.filterNot { it.id == id } }
    }
}
