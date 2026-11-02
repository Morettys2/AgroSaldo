package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.DadosDeExemplo
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.LancamentoExcluido
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Fake em memória para testes, na mesma ordem do repositório Room: mais recentes primeiro, por data
 * e depois pela última gravação. A ordem das gravações faz o papel do `atualizado_em`.
 *
 * Sem [sessao], não filtra por conta (o jeito mais simples para testes de ViewModel). Com [sessao],
 * confere sessão e posse como o Room: lista vazia e busca nula sem sessão, IllegalStateException ao
 * gravar ou excluir sem sessão e IllegalArgumentException ao gravar o que é de outra conta. Os
 * [iniciais] ficam com quem estiver conectado ao criar o fake.
 */
class LancamentoRepositoryMemoria(iniciais: List<Lancamento> = DadosDeExemplo.lancamentos(), private val sessao: SessaoRepository? = null) :
    LancamentoRepository {

    /** O lançamento, a conta dona e a ordem da gravação. */
    private class Guardado(val lancamento: Lancamento, val dono: String?, val gravacao: Long)

    private var ultimaGravacao = 0L
    private val guardados = MutableStateFlow(iniciais.map { Guardado(it, sessao?.usuarioId?.value, ++ultimaGravacao) })

    /** Ligado pelo [PropriedadeRepositoryMemoria] que recebe este fake: diz de quem é cada propriedade. */
    internal var propriedades: PropriedadeRepositoryMemoria? = null

    override fun observarTodos(): Flow<List<Lancamento>> {
        val visiveis = if (sessao == null) {
            guardados
        } else {
            combine(sessao.usuarioId, guardados) { usuarioId, lista -> lista.filter { usuarioId != null && it.dono == usuarioId } }
        }
        return visiveis.map { lista -> lista.sortedWith(MAIS_RECENTES_PRIMEIRO).map { it.lancamento } }
    }

    override suspend fun buscarPorId(id: String): Lancamento? = visiveisAgora().firstOrNull { it.lancamento.id == id }?.lancamento

    override suspend fun salvar(lancamento: Lancamento) = gravar(lancamento, ++ultimaGravacao)

    /** Regrava com a gravação que o lançamento tinha: volta ao mesmo lugar da lista. */
    override suspend fun restaurar(excluido: LancamentoExcluido) = gravar(excluido.lancamento, excluido.gravadoEm.toEpochMilli())

    override suspend fun excluir(id: String): LancamentoExcluido? {
        usuarioConectado()
        val apagado = visiveisAgora().firstOrNull { it.lancamento.id == id } ?: return null
        guardados.update { lista -> lista.filterNot { it.lancamento.id == id } }
        return apagado.paraExcluido()
    }

    /** Remove e devolve os lançamentos da propriedade; simula o CASCADE para [PropriedadeRepositoryMemoria]. */
    internal fun tirarDaPropriedade(propriedadeId: String): List<LancamentoExcluido> {
        val daPropriedade = guardados.value.filter { it.lancamento.propriedadeId == propriedadeId }
        guardados.update { lista -> lista.filterNot { it.lancamento.propriedadeId == propriedadeId } }
        return daPropriedade.map { it.paraExcluido() }
    }

    internal fun devolver(lancamentos: List<LancamentoExcluido>) {
        val dono = usuarioConectado()
        guardados.update { lista ->
            lista.filterNot { guardado -> lancamentos.any { it.lancamento.id == guardado.lancamento.id } } +
                lancamentos.map { Guardado(it.lancamento, dono, it.gravadoEm.toEpochMilli()) }
        }
    }

    private fun gravar(lancamento: Lancamento, gravacao: Long) {
        val dono = usuarioConectado()
        if (dono != null) {
            require(propriedades?.ehDoUsuario(lancamento.propriedadeId, dono) ?: true) { "Escolha uma das suas propriedades." }
            require(guardados.value.none { it.lancamento.id == lancamento.id && it.dono != dono }) {
                "Este lançamento pertence a outra conta."
            }
        }
        guardados.update { lista -> lista.filterNot { it.lancamento.id == lancamento.id } + Guardado(lancamento, dono, gravacao) }
    }

    /** O que a sessão atual enxerga (tudo, sem [sessao]). */
    private fun visiveisAgora(): List<Guardado> {
        if (sessao == null) return guardados.value
        val usuarioId = sessao.usuarioId.value ?: return emptyList()
        return guardados.value.filter { it.dono == usuarioId }
    }

    /** Sem [sessao], null (o fake não filtra); com ela, o id conectado ou IllegalStateException. */
    private fun usuarioConectado(): String? = sessao?.let { checkNotNull(it.usuarioId.value) { "Nenhum usuário conectado." } }

    private fun Guardado.paraExcluido() = LancamentoExcluido(lancamento, Instant.ofEpochMilli(gravacao))

    private companion object {
        val MAIS_RECENTES_PRIMEIRO = compareByDescending<Guardado> { it.lancamento.data }.thenByDescending { it.gravacao }
    }
}
