package br.joao.agrosaldo.data.clima

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** De onde vem o JSON da previsão. Interface para os testes não usarem a internet. */
fun interface FontePrevisao {
    /** @throws IOException sem internet. @throws RespostaInvalidaException resposta de erro. */
    suspend fun baixar(latitude: Double, longitude: Double): String
}

/**
 * Chamada HTTPS direta, com o HttpURLConnection do próprio Java.
 *
 * Retrofit ou Ktor fariam o mesmo com mais conforto, mas o app faz UMA
 * chamada GET para UM endereço. Uma biblioteca de rede inteira para isso
 * seria peso sem retorno. Se a API NestJS entrar, aí sim vale o Retrofit.
 */
class OpenMeteoHttp : FontePrevisao {

    override suspend fun baixar(latitude: Double, longitude: Double): String =
        // Rede nunca na thread principal: o Android derruba o app se tentar.
        withContext(Dispatchers.IO) {
            val conexao = URL(OpenMeteo.endereco(latitude, longitude)).openConnection() as HttpURLConnection
            try {
                conexao.connectTimeout = TEMPO_CONEXAO_MS
                conexao.readTimeout = TEMPO_LEITURA_MS
                conexao.setRequestProperty("Accept", "application/json")
                conexao.setRequestProperty("User-Agent", "AgroSaldo-Android")

                val codigo = conexao.responseCode
                if (codigo != HttpURLConnection.HTTP_OK) {
                    // 4xx/5xx: o servidor respondeu, então não é falta de internet.
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
