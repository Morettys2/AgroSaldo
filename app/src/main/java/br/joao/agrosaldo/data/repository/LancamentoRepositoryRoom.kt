package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.paraDominio
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LancamentoRepositoryRoom(
    private val dao: LancamentoDao
) : LancamentoRepository {

    override fun observarTodos(): Flow<List<Lancamento>> =
        dao.observarTodos().map { entidades -> entidades.map { it.paraDominio() } }

    override suspend fun buscarPorId(id: String): Lancamento? =
        dao.buscarPorId(id)?.paraDominio()

    override suspend fun salvar(lancamento: Lancamento) =
        dao.salvar(lancamento.paraEntidade())

    override suspend fun excluir(id: String) =
        dao.excluir(id)

    override suspend fun excluirDaPropriedade(propriedadeId: String) =
        dao.excluirDaPropriedade(propriedadeId)
}
