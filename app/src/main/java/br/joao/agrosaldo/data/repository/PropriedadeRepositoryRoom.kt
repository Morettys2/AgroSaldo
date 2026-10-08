package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.local.Transacao
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.paraDominio
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.PropriedadeExcluida
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [PropriedadeRepository] em Room. Toda consulta é filtrada pelo usuário da
 * [sessao]; escritas sem sessão lançam IllegalStateException.
 */
class PropriedadeRepositoryRoom(
    private val dao: PropriedadeDao,
    private val lancamentoDao: LancamentoDao,
    private val sessao: SessaoRepository,
    private val transacao: Transacao = Transacao.DIRETA
) : PropriedadeRepository {

    /** Reinicia a consulta a cada troca de usuário; lista vazia sem sessão. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarTodas(): Flow<List<Propriedade>> =
        sessao.usuarioId.flatMapLatest { usuarioId ->
            if (usuarioId == null) {
                flowOf(emptyList())
            } else {
                dao.observarDoUsuario(usuarioId).map { entidades -> entidades.map { it.paraDominio() } }
            }
        }

    override suspend fun buscarPorId(id: String): Propriedade? {
        val usuarioId = sessao.usuarioId.value ?: return null
        return dao.buscarPorId(id, usuarioId)?.paraDominio()
    }

    override suspend fun salvar(propriedade: Propriedade) {
        val usuarioId = usuarioConectado()
        // O upsert casa só pelo id; impede sobrescrever propriedade de outra conta.
        val dono = dao.donoDe(propriedade.id)
        require(dono == null || dono == usuarioId) { "Esta propriedade pertence a outra conta." }
        dao.salvar(propriedade.paraEntidade(usuarioId = usuarioId))
    }

    override suspend fun excluir(id: String) =
        dao.excluir(id, usuarioConectado())

    override suspend fun excluirComLancamentos(id: String): PropriedadeExcluida? {
        val usuarioId = usuarioConectado()
        return transacao.executar {
            val propriedade = dao.buscarPorId(id, usuarioId) ?: return@executar null
            val lancamentos = lancamentoDao.daPropriedade(id, usuarioId)
            lancamentoDao.excluirDaPropriedade(id, usuarioId)
            dao.excluir(id, usuarioId)
            PropriedadeExcluida(propriedade.paraDominio(), lancamentos.map { it.paraDominio() })
        }
    }

    override suspend fun restaurar(excluida: PropriedadeExcluida) {
        transacao.executar {
            // Propriedade antes dos lançamentos por causa da foreign key.
            salvar(excluida.propriedade)
            lancamentoDao.salvarTodos(excluida.lancamentos.map { it.paraEntidade() })
        }
    }

    private fun usuarioConectado(): String =
        checkNotNull(sessao.usuarioId.value) { "Nenhum usuário conectado." }
}
