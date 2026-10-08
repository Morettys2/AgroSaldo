package br.joao.agrosaldo.domain.repository

import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.PropriedadeExcluida
import kotlinx.coroutines.flow.Flow

/**
 * Contrato do repositório — ainda parte do MODEL.
 *
 * O ViewModel depende DESTA interface, nunca da implementação. É o que permite
 * trocar o guardador de dados sem tocar em ViewModel nem em tela.
 *
 * Hoje a implementação é em memória. Quando o Room entrar, só nasce uma classe
 * nova que implementa esta mesma interface — e nada mais no app muda.
 * É esse o argumento pra defender o padrão Repository na apresentação.
 */
interface PropriedadeRepository {

    /** Fluxo reativo: quem observar recebe a lista nova sozinho a cada mudança. */
    fun observarTodas(): Flow<List<Propriedade>>

    suspend fun buscarPorId(id: String): Propriedade?

    suspend fun salvar(propriedade: Propriedade)

    suspend fun excluir(id: String)

    /**
     * RN02 — exclui a propriedade e os lançamentos dela de uma vez só (tudo
     * ou nada). Devolve o que foi apagado, para o "Desfazer"; null se a
     * propriedade não existe.
     */
    suspend fun excluirComLancamentos(id: String): PropriedadeExcluida?

    /** Desfaz o [excluirComLancamentos]: volta tudo, também de uma vez só. */
    suspend fun restaurar(excluida: PropriedadeExcluida)
}
