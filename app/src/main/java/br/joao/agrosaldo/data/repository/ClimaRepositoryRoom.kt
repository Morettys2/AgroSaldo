package br.joao.agrosaldo.data.repository

import br.joao.agrosaldo.data.clima.FontePrevisao
import br.joao.agrosaldo.data.clima.LeitorOpenMeteo
import br.joao.agrosaldo.data.clima.OpenMeteo
import br.joao.agrosaldo.data.clima.RespostaInvalidaException
import br.joao.agrosaldo.data.local.dao.PrevisaoDao
import br.joao.agrosaldo.data.local.entidade.PrevisaoEntity
import br.joao.agrosaldo.domain.clima.PoliticaDeAtualizacao
import br.joao.agrosaldo.domain.clima.Previsao
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.ResultadoAtualizacao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * Cache primeiro: lê sempre do Room; a internet só troca o que está guardado.
 */
class ClimaRepositoryRoom(
    private val fonte: FontePrevisao,
    private val dao: PrevisaoDao,
    private val relogio: () -> Long = System::currentTimeMillis
) : ClimaRepository {

    // Início e Clima podem pedir atualização ao mesmo tempo. O Mutex faz o
    // segundo esperar o primeiro e então perceber que já está atualizado.
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
            // Lê ANTES de guardar: resposta quebrada não pode apagar a boa que já estava no banco.
            val previsao = LeitorOpenMeteo.ler(json, agora)
            dao.salvar(
                PrevisaoEntity(
                    propriedadeId = propriedade.id,
                    latitude = latitude,
                    longitude = longitude,
                    respostaJson = json,
                    obtidaEm = agora.toEpochMilli()
                )
            )
            ResultadoAtualizacao.Sucesso(previsao)
        } catch (e: IOException) {
            ResultadoAtualizacao.SemConexao
        } catch (e: RespostaInvalidaException) {
            ResultadoAtualizacao.Falha(e.message ?: "Resposta inválida.")
        }
    }

    /**
     * O cache só vale se for das coordenadas ATUAIS da propriedade e tiver
     * menos de 2 dias. Se o produtor corrigiu a localização do sítio, a
     * previsão antiga era de outro lugar — melhor não mostrar nada.
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

    private fun agora(): Instant = Instant.ofEpochMilli(relogio())
}
