package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.domain.repository.SessaoRepository
import br.joao.agrosaldo.domain.repository.UsuarioLogadoRepository
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** [UsuarioLogadoRepository] a partir da sessão e do cadastro de contas. */
class UsuarioLogadoRoom(private val sessao: SessaoRepository, private val usuarios: UsuarioRepository) : UsuarioLogadoRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarNome(): Flow<String?> = sessao.usuarioId.flatMapLatest { id ->
        if (id == null) flowOf(null) else usuarios.observar(id).map { it?.nome }
    }
}
