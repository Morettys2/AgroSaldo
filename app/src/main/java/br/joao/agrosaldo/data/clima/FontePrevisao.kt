package br.joao.agrosaldo.data.clima

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Fonte do JSON bruto da previsão; substituível por fake nos testes. */
fun interface FontePrevisao {
    /**
     * @throws IOException falha de rede ou timeout.
     * @throws ServicoIndisponivelException HTTP 429 ou 5xx.
     * @throws RespostaInvalidaException outra resposta HTTP diferente de 200.
     */
    suspend fun baixar(latitude: Double, longitude: Double): String
}

/**
 * GET HTTPS na Open-Meteo via HttpURLConnection. Uma única chamada não
 * justifica uma biblioteca de rede.
 */
class OpenMeteoHttp : FontePrevisao {

    override suspend fun baixar(latitude: Double, longitude: Double): String {
        val conexao = URL(OpenMeteo.endereco(latitude, longitude)).openConnection() as HttpURLConnection
        return chamadaBloqueante(TEMPO_TOTAL_MS, interromper = conexao::disconnect) { ler(conexao) }
    }

    private fun ler(conexao: HttpURLConnection): String {
        conexao.connectTimeout = TEMPO_CONEXAO_MS
        conexao.readTimeout = TEMPO_LEITURA_MS
        conexao.setRequestProperty("Accept", "application/json")
        conexao.setRequestProperty("User-Agent", "AgroSaldo-Android")

        val codigo = conexao.responseCode
        if (codigo != HttpURLConnection.HTTP_OK) {
            // Servidor respondeu: não é falta de conexão.
            val corpo = conexao.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw OpenMeteo.erroHttp(codigo, corpo)
        }
        return conexao.inputStream.bufferedReader().use { it.readText() }
    }

    private companion object {
        const val TEMPO_CONEXAO_MS = 10_000
        const val TEMPO_LEITURA_MS = 15_000

        /** Limite do download inteiro; os dois acima valem para cada operação. */
        const val TEMPO_TOTAL_MS = 30_000L
    }
}

/**
 * Executa [bloqueante] em [Dispatchers.IO] com limite total de [tempoTotalMs].
 *
 * E/S de rede bloqueante não atende à interrupção da thread. No cancelamento, ou quando o
 * tempo acaba, [interromper] é chamado (ex.: `HttpURLConnection.disconnect`) para destravá-la
 * na hora; ele também é chamado ao final, com sucesso ou erro.
 *
 * @throws IOException se o tempo total acabar. O cancelamento de quem chamou continua
 *   chegando como CancellationException.
 */
internal suspend fun <T : Any> chamadaBloqueante(tempoTotalMs: Long, interromper: () -> Unit, bloqueante: () -> T): T =
    withTimeoutOrNull(tempoTotalMs) {
        withContext(Dispatchers.IO) {
            // UNDISPATCHED: o finally fica armado antes de bloqueante() começar.
            val vigia = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    interromper()
                }
            }
            try {
                bloqueante()
            } catch (e: Exception) {
                // Se o erro veio de interromper(), o que vale é o cancelamento.
                ensureActive()
                throw e
            } finally {
                vigia.cancel()
            }
        }
    } ?: throw IOException("Tempo total de ${tempoTotalMs / 1_000} s esgotado.")
