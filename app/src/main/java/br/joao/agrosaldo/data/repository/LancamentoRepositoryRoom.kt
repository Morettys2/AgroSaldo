package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.local.Transacao
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.paraDominio
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.data.local.paraExcluido
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.LancamentoExcluido
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Implementação de [LancamentoRepository] com Room, restrita ao usuário da [sessao]. A
 * [transacao] mantém a checagem de posse e a gravação atômicas.
 */
class LancamentoRepositoryRoom(
    private val dao: LancamentoDao,
    private val sessao: SessaoRepository,
    private val transacao: Transacao,
    private val relogio: Clock
) : LancamentoRepository {

    /** Reinicia a consulta a cada troca de usuário (`flatMapLatest`); lista vazia sem sessão. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observarTodos(): Flow<List<Lancamento>> = sessao.usuarioId.flatMapLatest { usuarioId ->
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
     * Grava numa transação, depois de conferir que a propriedade e o lançamento (pelo id) são do
     * usuário.
     *
     * @throws IllegalArgumentException se a propriedade ou o lançamento com o mesmo id é de outra conta.
     * @throws IllegalStateException sem sessão.
     */
    override suspend fun salvar(lancamento: Lancamento) = gravar(lancamento, agora = relogio.millis())

    /** Regrava o lançamento apagado com o `atualizado_em` que ele tinha: volta ao mesmo lugar da lista. */
    override suspend fun restaurar(excluido: LancamentoExcluido) = gravar(excluido.lancamento, agora = excluido.gravadoEm.toEpochMilli())

    /** Checagem de posse e upsert numa transação, com [agora] em `atualizado_em`. */
    private suspend fun gravar(lancamento: Lancamento, agora: Long) {
        val usuarioId = usuarioConectado()
        transacao.executar {
            require(dao.propriedadeEhDoUsuario(lancamento.propriedadeId, usuarioId)) {
                "Escolha uma das suas propriedades."
            }
            // O upsert casa só pelo id: sem esta checagem, um id repetido moveria o lançamento de outra conta.
            require(!dao.existeSemSerDoUsuario(lancamento.id, usuarioId)) {
                "Este lançamento pertence a outra conta."
            }
            dao.salvar(lancamento.paraEntidade(agora = agora))
        }
    }

    /** Lê e apaga na mesma transação e devolve o que saiu, com o instante da última gravação. */
    override suspend fun excluir(id: String): LancamentoExcluido? {
        val usuarioId = usuarioConectado()
        return transacao.executar {
            val apagado = dao.buscarPorId(id, usuarioId)?.paraExcluido()
            dao.excluir(id, usuarioId)
            apagado
        }
    }

    /** Id do usuário da sessão; lança IllegalStateException se não há ninguém conectado. */
    private fun usuarioConectado(): String = checkNotNull(sessao.usuarioId.value) { "Nenhum usuário conectado." }
}
