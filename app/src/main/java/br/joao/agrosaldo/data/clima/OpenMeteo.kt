package br.joao.agrosaldo.data.clima

import br.joao.agrosaldo.domain.clima.CondicaoTempo
import br.joao.agrosaldo.domain.clima.Previsao
import br.joao.agrosaldo.domain.clima.PrevisaoDia
import br.joao.agrosaldo.domain.clima.PrevisaoHora
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * Montagem da URL da Open-Meteo (API sem chave; plano gratuito é só para uso
 * não comercial). Junto com [LeitorOpenMeteo] e [OpenMeteoHttp], concentra o
 * acoplamento com o provedor.
 */
object OpenMeteo {

    private const val ENDERECO = "https://api.open-meteo.com/v1/forecast"

    /**
     * Arredonda para 2 casas (~1 km), abaixo da resolução dos modelos, para que
     * a coordenada exata não saia do aparelho (privacidade por padrão, LGPD art. 46).
     */
    fun arredondar(coordenada: Double): Double =
        BigDecimal.valueOf(coordenada).setScale(2, RoundingMode.HALF_UP).toDouble()

    fun endereco(latitude: Double, longitude: Double): String {
        // Locale.ROOT: em pt-BR "%.2f" usaria vírgula decimal, rejeitada pela API.
        val lat = String.format(Locale.ROOT, "%.2f", arredondar(latitude))
        val lon = String.format(Locale.ROOT, "%.2f", arredondar(longitude))
        return ENDERECO +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code" +
            "&hourly=precipitation_probability,precipitation" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min," +
            "precipitation_sum,precipitation_probability_max" +
            "&timezone=auto&forecast_days=7"
    }

    /**
     * Erro para uma resposta HTTP diferente de 200: [ServicoIndisponivelException] para 429 e 5xx
     * (passageiros), [RespostaInvalidaException] para os demais.
     */
    fun erroHttp(codigo: Int, corpo: String): Exception {
        val mensagem = "HTTP $codigo ${corpo.take(200)}"
        return if (codigo == HTTP_MUITAS_REQUISICOES || codigo in 500..599) {
            ServicoIndisponivelException(mensagem)
        } else {
            RespostaInvalidaException(mensagem)
        }
    }

    private const val HTTP_MUITAS_REQUISICOES = 429
}

/** Resposta HTTP de erro ou corpo fora do formato esperado. */
class RespostaInvalidaException(mensagem: String, cause: Throwable? = null) : Exception(mensagem, cause)

/** HTTP 429 ou 5xx: o serviço está sobrecarregado ou fora do ar; vale tentar de novo mais tarde. */
class ServicoIndisponivelException(mensagem: String) : Exception(mensagem)

/**
 * Converte o JSON da Open-Meteo em [Previsao] usando org.json (embutido no Android).
 *
 * @throws RespostaInvalidaException para qualquer erro de formato.
 */
object LeitorOpenMeteo {

    fun ler(json: String, obtidaEm: Instant): Previsao {
        try {
            val raiz = JSONObject(json)
            if (raiz.optBoolean("error", false)) {
                throw RespostaInvalidaException(raiz.optString("reason", "erro do serviço"))
            }

            val atual = raiz.optJSONObject("current")
            val diario = raiz.getJSONObject("daily")
            val horario = raiz.getJSONObject("hourly")

            val datas = diario.getJSONArray("time")
            val dias = (0 until datas.length()).map { i ->
                PrevisaoDia(
                    data = LocalDate.parse(datas.getString(i)),
                    condicao = CondicaoTempo.doCodigoWmo(diario.optJSONArray("weather_code")?.intOuNulo(i)),
                    temperaturaMaxima = diario.optJSONArray("temperature_2m_max")?.doubleOuNulo(i),
                    temperaturaMinima = diario.optJSONArray("temperature_2m_min")?.doubleOuNulo(i),
                    chuvaMm = diario.optJSONArray("precipitation_sum")?.doubleOuNulo(i) ?: 0.0,
                    probabilidadeChuva = diario.optJSONArray("precipitation_probability_max")?.intOuNulo(i) ?: 0
                )
            }

            val horarios = horario.getJSONArray("time")
            val probabilidades = horario.getJSONArray("precipitation_probability")
            val chuvas = horario.getJSONArray("precipitation")
            if (probabilidades.length() != horarios.length() || chuvas.length() != horarios.length()) {
                throw RespostaInvalidaException("Listas horárias com tamanhos diferentes.")
            }
            val horas = (0 until horarios.length()).map { i ->
                PrevisaoHora(
                    horario = LocalDateTime.parse(horarios.getString(i)),
                    probabilidadeChuva = (probabilidades.intOuNulo(i) ?: 0).coerceIn(0, 100),
                    chuvaMm = (chuvas.doubleOuNulo(i) ?: 0.0).coerceAtLeast(0.0)
                )
            }

            if (dias.isEmpty() || horas.isEmpty()) throw RespostaInvalidaException("Previsão vazia.")

            return Previsao(
                latitude = raiz.getDouble("latitude"),
                longitude = raiz.getDouble("longitude"),
                obtidaEm = obtidaEm,
                fusoSegundos = raiz.optInt("utc_offset_seconds", 0),
                temperaturaAtual = atual?.doubleOuNulo("temperature_2m"),
                condicaoAtual = CondicaoTempo.doCodigoWmo(atual?.intOuNulo("weather_code")),
                dias = dias,
                horas = horas
            )
        } catch (e: RespostaInvalidaException) {
            throw e
        } catch (e: Exception) {
            // Unifica JSONException, DateTimeParseException etc.; a original fica como causa.
            throw RespostaInvalidaException("Resposta da Open-Meteo em formato inesperado: ${e.message}", e)
        }
    }

    // A API envia null para valores ausentes; índice fora do array também vira null.
    private fun JSONArray.doubleOuNulo(i: Int): Double? =
        if (i >= length() || isNull(i)) null else getDouble(i)

    private fun JSONArray.intOuNulo(i: Int): Int? =
        if (i >= length() || isNull(i)) null else getDouble(i).toInt()

    private fun JSONObject.doubleOuNulo(chave: String): Double? =
        if (!has(chave) || isNull(chave)) null else getDouble(chave)

    private fun JSONObject.intOuNulo(chave: String): Int? =
        if (!has(chave) || isNull(chave)) null else getDouble(chave).toInt()
}
