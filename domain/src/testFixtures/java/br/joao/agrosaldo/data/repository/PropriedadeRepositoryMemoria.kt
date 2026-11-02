package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.PropriedadeExcluida
import br.joao.agrosaldo.domain.model.emOrdemAlfabetica
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Fake em memória para testes. Entrega a lista na mesma ordem do repositório Room (alfabética do
 * português), para "a primeira propriedade" valer igual nos dois.
 *
 * Sem [sessao], não filtra por conta (o jeito mais simples para testes de ViewModel). Com [sessao],
 * confere sessão e posse como o Room: lista vazia e busca nula sem sessão, IllegalStateException ao
 * gravar ou excluir sem sessão e IllegalArgumentException ao gravar a propriedade de outra conta. As
 * [iniciais] ficam com quem estiver conectado ao criar o fake.
 */
class PropriedadeRepositoryMemoria(
    iniciais: List<Propriedade> = DadosDeExemplo.propriedades(),
    /** Se informado, [excluirComLancamentos] remove também os lançamentos (simula o CASCADE). */
    private val lancamentos: LancamentoRepositoryMemoria? = null,
    private val sessao: SessaoRepository? = null
) : PropriedadeRepository {

    /** A propriedade e a conta dona. */
    private class Guardada(val propriedade: Propriedade, val dono: String?)

    private val guardadas = MutableStateFlow(iniciais.map { Guardada(it, sessao?.usuarioId?.value) })

    init {
        lancamentos?.propriedades = this
    }

    override fun observarTodas(): Flow<List<Propriedade>> {
        val visiveis = if (sessao == null) {
            guardadas
        } else {
            combine(sessao.usuarioId, guardadas) { usuarioId, lista -> lista.filter { usuarioId != null && it.dono == usuarioId } }
        }
        return visiveis.map { lista -> lista.map { it.propriedade }.emOrdemAlfabetica() }
    }

    override suspend fun buscarPorId(id: String): Propriedade? = visiveisAgora().firstOrNull { it.propriedade.id == id }?.propriedade

    override suspend fun salvar(propriedade: Propriedade) {
        val dono = usuarioConectado()
        if (dono != null) {
            require(guardadas.value.none { it.propriedade.id == propriedade.id && it.dono != dono }) {
                "Esta propriedade pertence a outra conta."
            }
        }
        guardadas.update { lista -> lista.filterNot { it.propriedade.id == propriedade.id } + Guardada(propriedade, dono) }
    }

    override suspend fun excluirComLancamentos(id: String): PropriedadeExcluida? {
        usuarioConectado()
        val propriedade = visiveisAgora().firstOrNull { it.propriedade.id == id }?.propriedade ?: return null
        val dela = lancamentos?.tirarDaPropriedade(id).orEmpty()
        guardadas.update { lista -> lista.filterNot { it.propriedade.id == id } }
        // O fake não guarda o instante de gravação da propriedade; ela sai com Instant.EPOCH.
        return PropriedadeExcluida(propriedade, Instant.EPOCH, dela)
    }

    override suspend fun restaurar(excluida: PropriedadeExcluida) {
        salvar(excluida.propriedade)
        lancamentos?.devolver(excluida.lancamentos)
    }

    /** Se a propriedade é da conta; sem [sessao], o fake não confere posse. */
    internal fun ehDoUsuario(propriedadeId: String, usuarioId: String): Boolean =
        sessao == null || guardadas.value.any { it.propriedade.id == propriedadeId && it.dono == usuarioId }

    /** O que a sessão atual enxerga (tudo, sem [sessao]). */
    private fun visiveisAgora(): List<Guardada> {
        if (sessao == null) return guardadas.value
        val usuarioId = sessao.usuarioId.value ?: return emptyList()
        return guardadas.value.filter { it.dono == usuarioId }
    }

    /** Sem [sessao], null (o fake não filtra); com ela, o id conectado ou IllegalStateException. */
    private fun usuarioConectado(): String? = sessao?.let { checkNotNull(it.usuarioId.value) { "Nenhum usuário conectado." } }
}
