package br.joao.agrosaldo.data.clima

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Fonte do JSON bruto da previsão; substituível por fake nos testes. */
fun interface FontePrevisao {
    /**
     * @throws IOException falha de rede ou timeout.
     * @throws RespostaInvalidaException resposta HTTP diferente de 200.
     */
    suspend fun baixar(latitude: Double, longitude: Double): String
}

/**
 * GET HTTPS na Open-Meteo via HttpURLConnection. Uma única chamada não
 * justifica uma biblioteca de rede.
 */
class OpenMeteoHttp : FontePrevisao {

    override suspend fun baixar(latitude: Double, longitude: Double): String =
        withContext(Dispatchers.IO) {
            val conexao = URL(OpenMeteo.endereco(latitude, longitude)).openConnection() as HttpURLConnection
            try {
                conexao.connectTimeout = TEMPO_CONEXAO_MS
                conexao.readTimeout = TEMPO_LEITURA_MS
                conexao.setRequestProperty("Accept", "application/json")
                conexao.setRequestProperty("User-Agent", "AgroSaldo-Android")

                val codigo = conexao.responseCode
                if (codigo != HttpURLConnection.HTTP_OK) {
                    // Servidor respondeu: tratado como resposta inválida, não como falta de conexão.
                    val corpo = conexao.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw RespostaInvalidaException("HTTP $codigo ${corpo.take(200)}")
                }
                conexao.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conexao.disconnect()
            }
        }

    private companion object {
        const val TEMPO_CONEXAO_MS = 10_000
        const val TEMPO_LEITURA_MS = 15_000
    }
}
