package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.RelogioDoAparelho
import br.joao.agrosaldo.data.local.Transacao
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.paraDominio
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.PropriedadeExcluida
import br.joao.agrosaldo.domain.model.emOrdemAlfabetica
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import java.time.Clock
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
    private val transacao: Transacao,
    private val relogio: Clock = RelogioDoAparelho
) : PropriedadeRepository {

    /**
     * Reinicia a consulta a cada troca de usuário; lista vazia sem sessão. Em ordem alfabética
     * pt-BR ([emOrdemAlfabetica]): a primeira é a propriedade padrão das telas.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarTodas(): Flow<List<Propriedade>> = sessao.usuarioId.flatMapLatest { usuarioId ->
        if (usuarioId == null) {
            flowOf(emptyList())
        } else {
            dao.observarDoUsuario(usuarioId).map { entidades ->
                entidades.map { it.paraDominio() }.emOrdemAlfabetica()
            }
        }
    }

    override suspend fun buscarPorId(id: String): Propriedade? {
        val usuarioId = sessao.usuarioId.value ?: return null
        return dao.buscarPorId(id, usuarioId)?.paraDominio()
    }

    override suspend fun salvar(propriedade: Propriedade) {
        val usuarioId = usuarioConectado()
        transacao.executar { gravar(propriedade, usuarioId) }
    }

    /** Checagem de posse e upsert; quem chama abre a transação. */
    private suspend fun gravar(propriedade: Propriedade, usuarioId: String) {
        // O upsert casa só pelo id; impede sobrescrever a propriedade de outra conta ou adotar uma sem dono.
        require(!dao.existeSemSerDoUsuario(propriedade.id, usuarioId)) { "Esta propriedade pertence a outra conta." }
        dao.salvar(propriedade.paraEntidade(usuarioId = usuarioId, agora = relogio.millis()))
    }

    override suspend fun excluir(id: String) = dao.excluir(id, usuarioConectado())

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
        val usuarioId = usuarioConectado()
        transacao.executar {
            // Propriedade antes dos lançamentos por causa da foreign key.
            gravar(excluida.propriedade, usuarioId)
            lancamentoDao.salvarTodos(excluida.lancamentos.map { it.paraEntidade(agora = relogio.millis()) })
        }
    }

    private fun usuarioConectado(): String = checkNotNull(sessao.usuarioId.value) { "Nenhum usuário conectado." }
}
