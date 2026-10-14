package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Fake em memória para testes, sem filtro por usuário. */
class LancamentoRepositoryMemoria(iniciais: List<Lancamento> = DadosDeExemplo.lancamentos()) : LancamentoRepository {

    private val lancamentosGuardados = MutableStateFlow(iniciais)

    override fun observarTodos(): Flow<List<Lancamento>> = lancamentosGuardados.asStateFlow()

    override suspend fun buscarPorId(id: String): Lancamento? = lancamentosGuardados.value.firstOrNull { it.id == id }

    override suspend fun salvar(lancamento: Lancamento) {
        lancamentosGuardados.update { lista ->
            if (lista.any { it.id == lancamento.id }) {
                lista.map { if (it.id == lancamento.id) lancamento else it }
            } else {
                lista + lancamento
            }
        }
    }

    override suspend fun excluir(id: String) {
        lancamentosGuardados.update { lista -> lista.filterNot { it.id == id } }
    }

    /** Remove e retorna os lançamentos da propriedade; simula o CASCADE para [PropriedadeRepositoryMemoria]. */
    internal fun tirarDaPropriedade(propriedadeId: String): List<Lancamento> {
        val daPropriedade = lancamentosGuardados.value.filter { it.propriedadeId == propriedadeId }
        lancamentosGuardados.update { lista -> lista.filterNot { it.propriedadeId == propriedadeId } }
        return daPropriedade
    }

    internal fun devolver(lancamentos: List<Lancamento>) {
        lancamentosGuardados.update { lista -> lista.filterNot { l -> lancamentos.any { it.id == l.id } } + lancamentos }
    }
}
