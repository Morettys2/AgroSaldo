package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.PropriedadeExcluida
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Fake em memória para testes de ViewModel, sem filtro por usuário. */
class PropriedadeRepositoryMemoria(
    iniciais: List<Propriedade> = DadosDeExemplo.propriedades(),
    /** Se informado, [excluirComLancamentos] remove também os lançamentos (simula o CASCADE). */
    private val lancamentos: LancamentoRepositoryMemoria? = null
) : PropriedadeRepository {

    private val _propriedades = MutableStateFlow(iniciais)

    override fun observarTodas(): Flow<List<Propriedade>> = _propriedades.asStateFlow()

    override suspend fun buscarPorId(id: String): Propriedade? =
        _propriedades.value.firstOrNull { it.id == id }

    override suspend fun salvar(propriedade: Propriedade) {
        _propriedades.update { lista ->
            if (lista.any { it.id == propriedade.id }) {
                lista.map { if (it.id == propriedade.id) propriedade else it }
            } else {
                lista + propriedade
            }
        }
    }

    override suspend fun excluir(id: String) {
        _propriedades.update { lista -> lista.filterNot { it.id == id } }
    }

    override suspend fun excluirComLancamentos(id: String): PropriedadeExcluida? {
        val propriedade = buscarPorId(id) ?: return null
        val dela = lancamentos?.tirarDaPropriedade(id).orEmpty()
        excluir(id)
        return PropriedadeExcluida(propriedade, dela)
    }

    override suspend fun restaurar(excluida: PropriedadeExcluida) {
        salvar(excluida.propriedade)
        lancamentos?.devolver(excluida.lancamentos)
    }
}
