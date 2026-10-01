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
 * Tudo o que é específico da Open-Meteo: o endereço da chamada e o formato
 * da resposta. Se um dia o app trocar de serviço de clima, só este arquivo
 * e o [OpenMeteoHttp] mudam.
 *
 * A Open-Meteo não pede chave de API e é gratuita para uso não comercial.
 */
object OpenMeteo {

    private const val ENDERECO = "https://api.open-meteo.com/v1/forecast"

    /**
     * Arredonda para 2 casas (≈ 1 km). Para previsão do tempo não muda nada
     * — os modelos trabalham com quadrados de vários km — e o ponto exato da
     * propriedade não sai do celular. Privacidade por padrão (LGPD art. 46).
     */
    fun arredondar(coordenada: Double): Double =
        BigDecimal.valueOf(coordenada).setScale(2, RoundingMode.HALF_UP).toDouble()

    fun endereco(latitude: Double, longitude: Double): String {
        // Locale.ROOT de propósito: com o celular em português, "%.2f" vira
        // "-21,70", e a API não entende vírgula. Bug clássico de app brasileiro.
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
}

/** A resposta não tem o formato esperado. */
class RespostaInvalidaException(mensagem: String) : Exception(mensagem)

/**
 * Lê o JSON da Open-Meteo e devolve o modelo de domínio.
 *
 * Usa o org.json, que já vem dentro do Android — nenhuma biblioteca a mais
 * para ler um único formato de resposta.
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
            // JSONException, DateTimeParseException...: tudo vira um erro só,
            // com a causa guardada para quem for investigar.
            throw RespostaInvalidaException("Resposta da Open-Meteo em formato inesperado: ${e.message}")
        }
    }

    // A Open-Meteo manda null quando não tem o dado daquela hora.
    private fun JSONArray.doubleOuNulo(i: Int): Double? =
        if (i >= length() || isNull(i)) null else getDouble(i)

    private fun JSONArray.intOuNulo(i: Int): Int? =
        if (i >= length() || isNull(i)) null else getDouble(i).toInt()

    private fun JSONObject.doubleOuNulo(chave: String): Double? =
        if (!has(chave) || isNull(chave)) null else getDouble(chave)

    private fun JSONObject.intOuNulo(chave: String): Int? =
        if (!has(chave) || isNull(chave)) null else getDouble(chave).toInt()
}
