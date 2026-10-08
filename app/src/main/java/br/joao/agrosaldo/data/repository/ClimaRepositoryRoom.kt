package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.clima.FontePrevisao
import br.joao.agrosaldo.data.clima.LeitorOpenMeteo
import br.joao.agrosaldo.data.clima.OpenMeteo
import br.joao.agrosaldo.data.clima.RespostaInvalidaException
import br.joao.agrosaldo.data.clima.ServicoIndisponivelException
import br.joao.agrosaldo.data.local.dao.PrevisaoDao
import br.joao.agrosaldo.data.local.entidade.PrevisaoEntity
import br.joao.agrosaldo.domain.clima.PoliticaDeAtualizacao
import br.joao.agrosaldo.domain.clima.Previsao
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.ResultadoAtualizacao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * [ClimaRepository] cache-first: a leitura vem sempre do Room; a rede só
 * substitui o JSON guardado. O cache armazena a resposta bruta da API.
 */
class ClimaRepositoryRoom(
    private val fonte: FontePrevisao,
    private val dao: PrevisaoDao,
    private val relogio: () -> Long = System::currentTimeMillis
) : ClimaRepository {

    // Serializa atualizações concorrentes (ex.: Início e Clima): a segunda
    // encontra o cache já renovado em [atualizarSeNecessario].
    private val trava = Mutex()

    override fun observar(propriedade: Propriedade): Flow<Previsao?> =
        dao.observar(propriedade.id).map { it?.paraPrevisaoValida(propriedade) }

    override suspend fun emCache(propriedade: Propriedade): Previsao? =
        dao.buscar(propriedade.id)?.paraPrevisaoValida(propriedade)

    override suspend fun atualizar(propriedade: Propriedade): ResultadoAtualizacao =
        trava.withLock { buscarEGuardar(propriedade) }

    override suspend fun atualizarSeNecessario(propriedade: Propriedade): ResultadoAtualizacao? =
        trava.withLock {
            val guardada = dao.buscar(propriedade.id)?.paraPrevisaoValida(propriedade)
            if (PoliticaDeAtualizacao.precisaAtualizar(guardada?.obtidaEm, agora())) buscarEGuardar(propriedade) else null
        }

    private suspend fun buscarEGuardar(propriedade: Propriedade): ResultadoAtualizacao {
        val latitude = OpenMeteo.arredondar(propriedade.latitude)
        val longitude = OpenMeteo.arredondar(propriedade.longitude)
        val agora = agora()

        return try {
            val json = fonte.baixar(latitude, longitude)
            // Valida antes de gravar: resposta inválida não sobrescreve o cache bom.
            val previsao = LeitorOpenMeteo.ler(json, agora)
            val guardada = guardar(
                PrevisaoEntity(
                    propriedadeId = propriedade.id,
                    latitude = latitude,
                    longitude = longitude,
                    respostaJson = json,
                    obtidaEm = agora.toEpochMilli()
                )
            )
            if (guardada) ResultadoAtualizacao.Sucesso(previsao) else ResultadoAtualizacao.Falha(MENSAGEM_PROPRIEDADE_EXCLUIDA)
        } catch (e: IOException) {
            ResultadoAtualizacao.SemConexao
        } catch (e: ServicoIndisponivelException) {
            ResultadoAtualizacao.Indisponivel(e.message ?: "Serviço indisponível.")
        } catch (e: RespostaInvalidaException) {
            ResultadoAtualizacao.Falha(e.message ?: "Resposta inválida.")
        }
    }

    /**
     * Cache válido só se corresponde às coordenadas atuais (arredondadas) da
     * propriedade e não passou de [PoliticaDeAtualizacao.VALIDADE_MAXIMA_CACHE].
     * Coordenadas alteradas invalidam a previsão anterior.
     */
    private fun PrevisaoEntity.paraPrevisaoValida(propriedade: Propriedade): Previsao? {
        if (latitude != OpenMeteo.arredondar(propriedade.latitude)) return null
        if (longitude != OpenMeteo.arredondar(propriedade.longitude)) return null
        val obtida = Instant.ofEpochMilli(obtidaEm)
        if (Duration.between(obtida, agora()) > PoliticaDeAtualizacao.VALIDADE_MAXIMA_CACHE) return null
        return try {
            LeitorOpenMeteo.ler(respostaJson, obtida)
        } catch (e: RespostaInvalidaException) {
            null
        }
    }

    /**
     * Grava a previsão. Se a propriedade foi excluída durante o download, o upsert
     * viola a chave estrangeira (SQLiteConstraintException) e a previsão é descartada.
     *
     * @return false se a propriedade não existe mais; outros erros do banco são relançados.
     */
    private suspend fun guardar(previsao: PrevisaoEntity): Boolean =
        try {
            dao.salvar(previsao)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            if (dao.propriedadeExiste(previsao.propriedadeId)) throw e
            false
        }

    private fun agora(): Instant = Instant.ofEpochMilli(relogio())

    private companion object {
        const val MENSAGEM_PROPRIEDADE_EXCLUIDA = "A propriedade foi excluída."
    }
}
