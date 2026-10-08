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
 * Implementação do contrato usando o Room.
 *
 * Esta é a prova do padrão Repository: o ViewModel continua conversando com
 * a mesma interface PropriedadeRepository de antes. Ele não sabe — e não
 * precisa saber — que os dados vêm de um arquivo SQLite, nem que existe
 * login. Nenhuma tela de propriedade foi alterada para o login entrar.
 *
 * Quem sabe de quem são os dados é este repositório: ele lê o usuário da
 * [sessao] e passa para o DAO em toda consulta.
 */
class PropriedadeRepositoryRoom(
    private val dao: PropriedadeDao,
    private val lancamentoDao: LancamentoDao,
    private val sessao: SessaoRepository,
    private val transacao: Transacao = Transacao.DIRETA
) : PropriedadeRepository {

    /**
     * flatMapLatest: quando o usuário muda, a consulta do anterior é
     * cancelada e começa a do novo. Quem estiver observando recebe a lista
     * certa sem saber que houve troca.
     */
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
        // O upsert atualizaria a linha de qualquer dono se o id batesse.
        // Esta checagem garante que uma conta não sobrescreve a da outra.
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
            // A propriedade primeiro: os lançamentos apontam para ela.
            salvar(excluida.propriedade)
            lancamentoDao.salvarTodos(excluida.lancamentos.map { it.paraEntidade() })
        }
    }

    private fun usuarioConectado(): String =
        checkNotNull(sessao.usuarioId.value) { "Nenhum usuário conectado." }
}
