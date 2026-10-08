package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.paraDominio
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** [LancamentoRepository] em Room, restrito ao usuário da [sessao]. */
class LancamentoRepositoryRoom(
    private val dao: LancamentoDao,
    private val sessao: SessaoRepository
) : LancamentoRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarTodos(): Flow<List<Lancamento>> =
        sessao.usuarioId.flatMapLatest { usuarioId ->
            if (usuarioId == null) {
                flowOf(emptyList())
            } else {
                dao.observarDoUsuario(usuarioId).map { entidades -> entidades.map { it.paraDominio() } }
            }
        }

    override suspend fun buscarPorId(id: String): Lancamento? {
        val usuarioId = sessao.usuarioId.value ?: return null
        return dao.buscarPorId(id, usuarioId)?.paraDominio()
    }

    /**
     * @throws IllegalArgumentException se a propriedade não pertence ao usuário
     *   (mesmo tipo das validações do modelo, já tratado pelo formulário).
     * @throws IllegalStateException sem sessão.
     */
    override suspend fun salvar(lancamento: Lancamento) {
        val usuarioId = usuarioConectado()
        require(dao.propriedadeEhDoUsuario(lancamento.propriedadeId, usuarioId)) {
            "Escolha uma das suas propriedades."
        }
        dao.salvar(lancamento.paraEntidade())
    }

    override suspend fun excluir(id: String) =
        dao.excluir(id, usuarioConectado())

    private fun usuarioConectado(): String =
        checkNotNull(sessao.usuarioId.value) { "Nenhum usuário conectado." }
}
