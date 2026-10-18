package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.domain.clima.Previsao
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.ResultadoAtualizacao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Fake para testes. O cache é um mapa por id de propriedade; [respostas],
 * [semInternet] e [indisponivel] definem o resultado de [atualizar] (sem resposta
 * configurada: [ResultadoAtualizacao.Falha], como quando a propriedade é excluída).
 */
class ClimaRepositoryMemoria(iniciais: Map<String, Previsao> = emptyMap()) : ClimaRepository {

    private val previsoesGuardadas = MutableStateFlow(iniciais)

    /** Previsão devolvida por [atualizar], por id de propriedade. */
    var respostas: Map<String, Previsao> = emptyMap()
    var semInternet: Boolean = false
    var indisponivel: Boolean = false
    var atualizacoes: Int = 0
        private set

    override fun observar(propriedade: Propriedade): Flow<Previsao?> = previsoesGuardadas.map { it[propriedade.id] }

    override suspend fun emCache(propriedade: Propriedade): Previsao? = previsoesGuardadas.value[propriedade.id]

    override suspend fun atualizar(propriedade: Propriedade): ResultadoAtualizacao {
        atualizacoes++
        if (semInternet) return ResultadoAtualizacao.SemConexao
        if (indisponivel) return ResultadoAtualizacao.Indisponivel("HTTP 503")
        val nova = respostas[propriedade.id] ?: return ResultadoAtualizacao.Falha("sem resposta de teste")
        previsoesGuardadas.update { it + (propriedade.id to nova) }
        return ResultadoAtualizacao.Sucesso(nova)
    }

    override suspend fun atualizarSeNecessario(propriedade: Propriedade): ResultadoAtualizacao? =
        if (previsoesGuardadas.value.containsKey(propriedade.id)) null else atualizar(propriedade)
}
