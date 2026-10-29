package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.domain.model.LancamentoExcluido
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.PropriedadeExcluida
import br.joao.agrosaldo.domain.model.emOrdemAlfabetica
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Fake em memória para testes de ViewModel, sem filtro por usuário. Entrega a lista na mesma ordem
 * do repositório Room (alfabética do português), para "a primeira propriedade" valer igual nos dois.
 */
class PropriedadeRepositoryMemoria(
    iniciais: List<Propriedade> = DadosDeExemplo.propriedades(),
    /** Se informado, [excluirComLancamentos] remove também os lançamentos (simula o CASCADE). */
    private val lancamentos: LancamentoRepositoryMemoria? = null
) : PropriedadeRepository {

    private val propriedadesGuardadas = MutableStateFlow(iniciais)

    override fun observarTodas(): Flow<List<Propriedade>> = propriedadesGuardadas.map { it.emOrdemAlfabetica() }

    override suspend fun buscarPorId(id: String): Propriedade? = propriedadesGuardadas.value.firstOrNull { it.id == id }

    override suspend fun salvar(propriedade: Propriedade) {
        propriedadesGuardadas.update { lista ->
            if (lista.any { it.id == propriedade.id }) {
                lista.map { if (it.id == propriedade.id) propriedade else it }
            } else {
                lista + propriedade
            }
        }
    }

    override suspend fun excluirComLancamentos(id: String): PropriedadeExcluida? {
        val propriedade = buscarPorId(id) ?: return null
        val dela = lancamentos?.tirarDaPropriedade(id).orEmpty()
        propriedadesGuardadas.update { lista -> lista.filterNot { it.id == id } }
        // O fake não guarda o instante de gravação; tudo sai com Instant.EPOCH.
        return PropriedadeExcluida(propriedade, Instant.EPOCH, dela.map { LancamentoExcluido(it, Instant.EPOCH) })
    }

    override suspend fun restaurar(excluida: PropriedadeExcluida) {
        salvar(excluida.propriedade)
        lancamentos?.devolver(excluida.lancamentos.map { it.lancamento })
    }
}
