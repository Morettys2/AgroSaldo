package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.paraDominio
import br.joao.agrosaldo.data.local.paraEntidade
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementação do contrato usando o Room.
 *
 * Esta é a prova do padrão Repository: o ViewModel continua conversando com
 * a mesma interface PropriedadeRepository de antes. Ele não sabe — e não
 * precisa saber — que os dados agora vêm de um arquivo SQLite em vez da
 * memória. Nenhuma tela foi alterada para esta troca.
 *
 * Não precisa trocar de thread aqui: funções suspend e Flow do Room já rodam
 * fora da thread principal por conta própria.
 */
class PropriedadeRepositoryRoom(
    private val dao: PropriedadeDao
) : PropriedadeRepository {

    override fun observarTodas(): Flow<List<Propriedade>> =
        dao.observarTodas().map { entidades -> entidades.map { it.paraDominio() } }

    override suspend fun buscarPorId(id: String): Propriedade? =
        dao.buscarPorId(id)?.paraDominio()

    override suspend fun salvar(propriedade: Propriedade) =
        dao.salvar(propriedade.paraEntidade())

    override suspend fun excluir(id: String) =
        dao.excluir(id)
}
