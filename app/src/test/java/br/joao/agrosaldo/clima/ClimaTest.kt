package br.joao.agrosaldo.clima

import br.joao.agrosaldo.aviso.TextosDoAviso
import br.joao.agrosaldo.clima.ApoioClima.AGORA
import br.joao.agrosaldo.clima.ApoioClima.jsonOpenMeteo
import br.joao.agrosaldo.clima.ApoioClima.previsao
import br.joao.agrosaldo.core.util.TextosDaChuva
import br.joao.agrosaldo.core.util.emMilimetros
import br.joao.agrosaldo.core.util.rotuloDoDia
import br.joao.agrosaldo.data.clima.FontePrevisao
import br.joao.agrosaldo.data.clima.LeitorOpenMeteo
import br.joao.agrosaldo.data.clima.OpenMeteo
import br.joao.agrosaldo.data.clima.RespostaInvalidaException
import br.joao.agrosaldo.data.local.dao.PrevisaoDao
import br.joao.agrosaldo.data.local.entidade.PrevisaoEntity
import br.joao.agrosaldo.data.preferencias.AjustesAvisoMemoria
import br.joao.agrosaldo.data.repository.ClimaRepositoryMemoria
import br.joao.agrosaldo.data.repository.ClimaRepositoryRoom
import br.joao.agrosaldo.data.repository.PropriedadeRepositoryMemoria
import br.joao.agrosaldo.data.sessao.SessaoMemoria
import br.joao.agrosaldo.domain.clima.AvisoDeChuva
import br.joao.agrosaldo.domain.clima.CondicaoTempo
import br.joao.agrosaldo.domain.clima.ControleDeAvisos
import br.joao.agrosaldo.domain.clima.LimitesChuva
import br.joao.agrosaldo.domain.clima.MotivoChuva
import br.joao.agrosaldo.domain.clima.PoliticaDeAtualizacao
import br.joao.agrosaldo.domain.clima.RegraDeChuva
import br.joao.agrosaldo.domain.clima.VerificadorDeChuva
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.repository.AjustesAviso
import br.joao.agrosaldo.domain.repository.ResultadoAtualizacao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * Clima e aviso de chuva (RF12, RF13, RF14, RN04).
 *
 * A regra de chuva é a mais fácil de quebrar sem perceber — o CLAUDE.md pede
 * teste para ela. Aqui estão as bordas: exatamente no limite, logo fora da
 * janela, hora atual pela metade, fuso horário.
 */
class ClimaTest {

    // ---------- RN04: a regra de chuva ----------

    @Test
    fun `RN04 - tudo seco nao avisa`() {
        val a = RegraDeChuva.avaliar(previsao(), AGORA)
        assertFalse(a.vaiChover)
        assertNull(a.motivo)
        assertTrue(a.temDados)
    }

    @Test
    fun `RN04 - exatamente 60 por cento ja avisa`() {
        val a = RegraDeChuva.avaliar(previsao(mapOf(18 to (60 to 0.0))), AGORA)
        assertTrue(a.vaiChover)
        assertEquals(MotivoChuva.PROBABILIDADE, a.motivo)
    }

    @Test
    fun `RN04 - 59 por cento e pouca chuva nao avisa`() {
        val a = RegraDeChuva.avaliar(previsao(mapOf(18 to (59 to 1.9))), AGORA)
        assertFalse(a.vaiChover)
        assertEquals(59, a.probabilidadeMaxima)
    }

    @Test
    fun `RN04 - 2 mm somados em varias horas avisam mesmo com chance baixa`() {
        // 0,6 + 0,7 + 0,7 = 2,0 mm — mas em Double a soma dá 1,9999999999999998.
        val a = RegraDeChuva.avaliar(previsao(mapOf(15 to (30 to 0.6), 16 to (30 to 0.7), 17 to (30 to 0.7))), AGORA)
        assertTrue(a.vaiChover)
        assertEquals(MotivoChuva.VOLUME, a.motivo)
    }

    @Test
    fun `RN04 - chance e volume juntos dao motivo AMBOS`() {
        val a = RegraDeChuva.avaliar(previsao(mapOf(20 to (90 to 8.0))), AGORA)
        assertEquals(MotivoChuva.AMBOS, a.motivo)
        assertEquals(8.0, a.chuvaTotalMm, 0.0001)
    }

    @Test
    fun `janela comeca na hora cheia atual e tem 12 horas`() {
        // Agora são 14:40. A hora das 14h conta; as 13h e as 02h do dia seguinte não.
        val dentroInicio = RegraDeChuva.avaliar(previsao(mapOf(14 to (80 to 0.0))), AGORA)
        val dentroFim = RegraDeChuva.avaliar(previsao(mapOf(25 to (80 to 0.0))), AGORA)
        val antes = RegraDeChuva.avaliar(previsao(mapOf(13 to (80 to 5.0))), AGORA)
        val depois = RegraDeChuva.avaliar(previsao(mapOf(26 to (80 to 5.0))), AGORA)

        assertTrue(dentroInicio.vaiChover)
        assertTrue(dentroFim.vaiChover)
        assertFalse(antes.vaiChover)
        assertFalse(depois.vaiChover)
    }

    @Test
    fun `horario e o da propriedade e nao o UTC`() {
        // 17:40 em UTC é 14:40 em Brasília. Se o fuso fosse ignorado, a
        // janela começaria às 17h e a chuva das 15h ficaria de fora.
        val a = RegraDeChuva.avaliar(previsao(mapOf(15 to (90 to 3.0))), AGORA)
        assertTrue(a.vaiChover)
        assertEquals(LocalDateTime.of(2026, 10, 1, 15, 0), a.inicioPrevisto)
    }

    @Test
    fun `limites do produtor sao respeitados`() {
        val p = previsao(mapOf(18 to (70 to 1.0)))
        assertTrue(RegraDeChuva.avaliar(p, AGORA, LimitesChuva(probabilidadeMinima = 70)).vaiChover)
        assertFalse(RegraDeChuva.avaliar(p, AGORA, LimitesChuva(probabilidadeMinima = 75, chuvaMinimaMm = 5.0)).vaiChover)
        // Janela de 3 h (14h às 16h59) não chega nas 18h.
        assertFalse(RegraDeChuva.avaliar(p, AGORA, LimitesChuva(janelaHoras = 3)).vaiChover)
    }

    @Test
    fun `previsao velha que nao cobre a janela nao inventa aviso`() {
        val dois_dias_depois = AGORA.plus(Duration.ofDays(2))
        val a = RegraDeChuva.avaliar(previsao(mapOf(18 to (100 to 50.0))), dois_dias_depois)
        assertFalse(a.vaiChover)
        assertFalse(a.temDados)
    }

    @Test
    fun `limites fora da faixa sao recusados`() {
        listOf(
            { LimitesChuva(probabilidadeMinima = 5) },
            { LimitesChuva(chuvaMinimaMm = 0.0) },
            { LimitesChuva(janelaHoras = 72) }
        ).forEach { criar ->
            try {
                criar()
                fail("Deveria recusar")
            } catch (esperado: IllegalArgumentException) {
                // ok
            }
        }
    }

    // ---------- 2 avisos por dia ----------

    @Test
    fun `nao avisa de novo a mesma propriedade em menos de 11 horas`() {
        assertTrue(ControleDeAvisos.podeAvisar(null, AGORA))
        assertFalse(ControleDeAvisos.podeAvisar(AGORA.minus(Duration.ofHours(10)), AGORA))
        assertTrue(ControleDeAvisos.podeAvisar(AGORA.minus(Duration.ofHours(11)), AGORA))
    }

    @Test
    fun `previsao so e buscada de novo depois de 1 hora`() {
        assertTrue(PoliticaDeAtualizacao.precisaAtualizar(null, AGORA))
        assertFalse(PoliticaDeAtualizacao.precisaAtualizar(AGORA.minus(Duration.ofMinutes(59)), AGORA))
        assertTrue(PoliticaDeAtualizacao.precisaAtualizar(AGORA.minus(Duration.ofMinutes(60)), AGORA))
    }

    // ---------- códigos WMO ----------

    @Test
    fun `codigos WMO viram condicoes`() {
        assertEquals(CondicaoTempo.CEU_LIMPO, CondicaoTempo.doCodigoWmo(0))
        assertEquals(CondicaoTempo.NUBLADO, CondicaoTempo.doCodigoWmo(3))
        assertEquals(CondicaoTempo.GAROA, CondicaoTempo.doCodigoWmo(53))
        assertEquals(CondicaoTempo.CHUVA, CondicaoTempo.doCodigoWmo(61))
        assertEquals(CondicaoTempo.PANCADAS, CondicaoTempo.doCodigoWmo(81))
        assertEquals(CondicaoTempo.TEMPESTADE, CondicaoTempo.doCodigoWmo(95))
        assertEquals(CondicaoTempo.DESCONHECIDA, CondicaoTempo.doCodigoWmo(null))
        assertEquals(CondicaoTempo.DESCONHECIDA, CondicaoTempo.doCodigoWmo(42))
        assertTrue(CondicaoTempo.TEMPESTADE.molhado)
        assertFalse(CondicaoTempo.NEBLINA.molhado)
    }

    // ---------- Open-Meteo: endereço e leitura ----------

    @Test
    fun `endereco usa ponto decimal mesmo com o celular em portugues`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pt-BR"))
            val url = OpenMeteo.endereco(-21.7049, -46.9312)
            assertTrue(url, url.contains("latitude=-21.70&longitude=-46.93"))
            assertFalse(url.contains("-21,70") || url.contains("-46,93"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `endereco pede so o necessario e usa HTTPS`() {
        val url = OpenMeteo.endereco(-21.7, -46.9)
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?"))
        assertTrue(url.contains("hourly=precipitation_probability,precipitation"))
        assertTrue(url.contains("timezone=auto"))
        assertTrue(url.contains("forecast_days=7"))
    }

    @Test
    fun `coordenada sai do celular arredondada em 2 casas`() {
        assertEquals(-21.70, OpenMeteo.arredondar(-21.70491), 0.0)
        assertEquals(-46.94, OpenMeteo.arredondar(-46.9351), 0.0)
        assertEquals(-21.71, OpenMeteo.arredondar(-21.705), 0.0)
    }

    @Test
    fun `leitor entende a resposta da Open-Meteo`() {
        val p = LeitorOpenMeteo.ler(jsonOpenMeteo(), AGORA)

        assertEquals(-10800, p.fusoSegundos)
        assertEquals(26.4, p.temperaturaAtual ?: 0.0, 0.0001)
        assertEquals(CondicaoTempo.NUBLADO, p.condicaoAtual)
        assertEquals(7, p.dias.size)
        assertEquals(48, p.horas.size)
        assertEquals(LocalDate.of(2026, 10, 1), p.dias[0].data)
        assertEquals(CondicaoTempo.CHUVA, p.dias[0].condicao)
        assertEquals(12.3, p.dias[0].chuvaMm, 0.0001)
        assertEquals(80, p.dias[0].probabilidadeChuva)
        assertEquals(LocalDateTime.of(2026, 10, 1, 13, 0), p.horas[13].horario)
    }

    @Test
    fun `valor nulo na resposta vira zero ou vazio em vez de travar`() {
        val p = LeitorOpenMeteo.ler(
            jsonOpenMeteo(probabilidades = List(48) { if (it == 20) null else 10 }, chuvas = List(48) { null }),
            AGORA
        )
        assertEquals(0, p.horas[20].probabilidadeChuva)
        assertEquals(0.0, p.horas[0].chuvaMm, 0.0)
        assertNull(p.dias[6].temperaturaMaxima)
        assertEquals(0, p.dias[6].probabilidadeChuva)
    }

    @Test
    fun `resposta quebrada vira RespostaInvalidaException`() {
        listOf(
            "não é json",
            "{}",
            """{"error":true,"reason":"Latitude must be in range of -90 to 90°."}""",
            jsonOpenMeteo().replace("\"precipitation\":[", "\"precipitation\":[1.0,")
        ).forEach { quebrado ->
            try {
                LeitorOpenMeteo.ler(quebrado, AGORA)
                fail("Deveria recusar: ${quebrado.take(40)}")
            } catch (esperado: RespostaInvalidaException) {
                // ok
            }
        }
    }

    // ---------- repositório: cache primeiro ----------

    private class PrevisaoDaoFalso : PrevisaoDao {
        val tabela = MutableStateFlow<Map<String, PrevisaoEntity>>(emptyMap())
        override fun observar(propriedadeId: String): Flow<PrevisaoEntity?> = tabela.map { it[propriedadeId] }
        override suspend fun buscar(propriedadeId: String) = tabela.value[propriedadeId]
        override suspend fun salvar(previsao: PrevisaoEntity) = tabela.update { it + (previsao.propriedadeId to previsao) }
    }

    private val fazenda = Propriedade(
        id = "p1", nome = "Fazenda São João da Serra", cultura = "Café",
        areaHectares = 42.0, latitude = -21.7049, longitude = -46.9312
    )

    private var agoraMs = AGORA.toEpochMilli()
    private var resposta: () -> String = { jsonOpenMeteo() }
    private var chamadas = 0
    private val dao = PrevisaoDaoFalso()
    private val fonte = FontePrevisao { _, _ -> chamadas++; resposta() }
    private val repositorio = ClimaRepositoryRoom(fonte, dao) { agoraMs }

    @Test
    fun `RF12 - atualizar baixa, guarda e entrega a previsao`() = runTest {
        val r = repositorio.atualizar(fazenda)

        assertTrue(r is ResultadoAtualizacao.Sucesso)
        val guardada = dao.tabela.value.getValue("p1")
        assertEquals(-21.70, guardada.latitude, 0.0)
        assertNotNull(repositorio.observar(fazenda).first())
    }

    @Test
    fun `sem internet a previsao guardada continua aparecendo`() = runTest {
        repositorio.atualizar(fazenda)
        resposta = { throw IOException("sem sinal no sítio") }
        agoraMs += Duration.ofHours(3).toMillis()

        assertEquals(ResultadoAtualizacao.SemConexao, repositorio.atualizar(fazenda))
        assertNotNull(repositorio.observar(fazenda).first())
    }

    @Test
    fun `resposta quebrada nao apaga a previsao boa que ja estava guardada`() = runTest {
        repositorio.atualizar(fazenda)
        val boa = dao.tabela.value.getValue("p1")
        resposta = { "{ quebrado" }

        assertTrue(repositorio.atualizar(fazenda) is ResultadoAtualizacao.Falha)
        assertEquals(boa, dao.tabela.value.getValue("p1"))
    }

    @Test
    fun `nao busca de novo antes de 1 hora`() = runTest {
        repositorio.atualizar(fazenda)
        agoraMs += Duration.ofMinutes(30).toMillis()
        assertNull(repositorio.atualizarSeNecessario(fazenda))
        assertEquals(1, chamadas)

        agoraMs += Duration.ofMinutes(31).toMillis()
        assertTrue(repositorio.atualizarSeNecessario(fazenda) is ResultadoAtualizacao.Sucesso)
        assertEquals(2, chamadas)
    }

    @Test
    fun `propriedade que mudou de lugar nao usa a previsao do lugar antigo`() = runTest {
        repositorio.atualizar(fazenda)
        val mudou = fazenda.copy(latitude = -22.5)
        assertNull(repositorio.observar(mudou).first())
        assertNull(repositorio.emCache(mudou))
    }

    @Test
    fun `previsao de mais de 2 dias deixa de ser mostrada`() = runTest {
        repositorio.atualizar(fazenda)
        agoraMs += Duration.ofDays(2).plusMinutes(1).toMillis()
        assertNull(repositorio.emCache(fazenda))
    }

    // ---------- RF14: verificação em segundo plano ----------

    private val sitio = Propriedade(
        id = "p2", nome = "Sítio Boa Vista", cultura = "Milho",
        areaHectares = 10.0, latitude = -21.8, longitude = -46.8
    )

    private fun verificador(
        clima: ClimaRepositoryMemoria,
        ajustes: AjustesAvisoMemoria = AjustesAvisoMemoria(),
        usuario: String? = "u1",
        relogio: () -> Long = { AGORA.toEpochMilli() }
    ) = VerificadorDeChuva(SessaoMemoria(usuario), PropriedadeRepositoryMemoria(listOf(fazenda, sitio)), clima, ajustes, relogio)

    private val comChuva = previsao(mapOf(18 to (85 to 6.0)))

    @Test
    fun `sitio desligado na tela de ajustes nao recebe aviso nem consulta a previsao`() = runTest {
        val clima = ClimaRepositoryMemoria().apply { respostas = mapOf("p1" to comChuva, "p2" to comChuva) }
        val ajustes = AjustesAvisoMemoria(AjustesAviso(propriedadesSemAviso = setOf("p1")))

        val r = verificador(clima, ajustes).verificar()

        assertEquals(listOf("p2"), r.avisos.map { it.propriedadeId })
        assertEquals("só o sítio ligado foi consultado", 1, clima.atualizacoes)
    }

    @Test
    fun `RF14 - avisa so a propriedade onde vai chover`() = runTest {
        val clima = ClimaRepositoryMemoria().apply { respostas = mapOf("p1" to comChuva, "p2" to previsao()) }

        val r = verificador(clima).verificar()

        assertEquals(listOf("p1"), r.avisos.map { it.propriedadeId })
        assertEquals(85, r.avisos.single().avaliacao.probabilidadeMaxima)
    }

    @Test
    fun `segunda verificacao no mesmo turno nao repete o aviso`() = runTest {
        val clima = ClimaRepositoryMemoria().apply { respostas = mapOf("p1" to comChuva) }
        val ajustes = AjustesAvisoMemoria()
        var agora = AGORA.toEpochMilli()
        val v = verificador(clima, ajustes) { agora }

        assertEquals(1, v.verificar().avisos.size)
        agora += Duration.ofHours(2).toMillis()
        assertEquals(0, v.verificar().avisos.size)
    }

    @Test
    fun `sem permissao de notificar nao registra aviso que ninguem viu`() = runTest {
        val clima = ClimaRepositoryMemoria().apply { respostas = mapOf("p1" to comChuva) }
        val ajustes = AjustesAvisoMemoria()
        val v = verificador(clima, ajustes)

        assertTrue(v.verificar(podeNotificar = false).avisos.isEmpty())
        assertEquals("a previsão foi atualizada mesmo assim", 2, clima.atualizacoes)
        // Liberou a notificação logo depois: o aviso sai na hora, sem esperar 11 h.
        assertEquals(listOf("p1"), v.verificar(podeNotificar = true).avisos.map { it.propriedadeId })
    }

    @Test
    fun `sem ninguem conectado ou com aviso desligado nada e verificado`() = runTest {
        val clima = ClimaRepositoryMemoria().apply { respostas = mapOf("p1" to comChuva) }

        assertTrue(verificador(clima, usuario = null).verificar().avisos.isEmpty())
        assertTrue(verificador(clima, AjustesAvisoMemoria(AjustesAviso(ativo = false))).verificar().avisos.isEmpty())
        assertEquals(0, clima.atualizacoes)
    }

    @Test
    fun `sem internet avisa com a previsao guardada se ela for recente`() = runTest {
        val clima = ClimaRepositoryMemoria(mapOf("p1" to comChuva.copy(obtidaEm = AGORA.minus(Duration.ofHours(2)))))
            .apply { semInternet = true }

        val r = verificador(clima).verificar()

        assertTrue(r.faltouConexao)
        assertEquals(1, r.avisos.size)
    }

    @Test
    fun `sem internet e previsao guardada velha nao avisa`() = runTest {
        val clima = ClimaRepositoryMemoria(mapOf("p1" to comChuva.copy(obtidaEm = AGORA.minus(Duration.ofHours(13)))))
            .apply { semInternet = true }

        assertTrue(verificador(clima).verificar().avisos.isEmpty())
    }

    // ---------- textos ----------

    @Test
    fun `milimetros aparecem do jeito que o produtor le`() {
        assertEquals("0 mm", 0.0.emMilimetros())
        assertEquals("2 mm", 2.0.emMilimetros())
        assertEquals("2,5 mm", 2.46.emMilimetros())
        assertEquals("12 mm", 12.3.emMilimetros())
        assertEquals("0 mm", (-1.0).emMilimetros())
    }

    @Test
    fun `dias da semana aparecem como hoje, amanha e o nome do dia`() {
        val hoje = LocalDate.of(2026, 10, 1) // quinta-feira
        assertEquals("Hoje", hoje.rotuloDoDia(hoje))
        assertEquals("Amanhã", hoje.plusDays(1).rotuloDoDia(hoje))
        assertEquals("Sábado", hoje.plusDays(2).rotuloDoDia(hoje))
        assertEquals("Segunda", hoje.plusDays(4).rotuloDoDia(hoje))
    }

    @Test
    fun `texto da notificacao traz chance, volume, hora e a recomendacao`() {
        val aviso = AvisoDeChuva("p1", "Fazenda São João", RegraDeChuva.avaliar(comChuva, AGORA))

        assertEquals("Chuva prevista: Fazenda São João", TextosDoAviso.titulo(aviso))
        assertEquals(
            "85% de chance de chuva nas próximas 12 horas, com 6 mm previstos a partir das 18h. " +
                "Dá para adiar a irrigação do pivô e economizar energia.",
            TextosDoAviso.corpo(aviso, 12)
        )
    }

    @Test
    fun `notificacao diz o dia quando a chuva comeca depois da meia-noite e nao fala 0 mm`() {
        val madrugada = previsao(mapOf(25 to (90 to 0.0)))
        val agoraLocal = madrugada.horaLocal(AGORA)
        val aviso = AvisoDeChuva("p1", "Fazenda São João", RegraDeChuva.avaliar(madrugada, AGORA), agoraLocal)

        assertEquals(
            "90% de chance de chuva nas próximas 12 horas a partir da 1h de amanhã. " +
                "Dá para adiar a irrigação do pivô e economizar energia.",
            TextosDoAviso.corpo(aviso, 12)
        )
    }

    @Test
    fun `frases de quando a chuva comeca`() {
        val agora = LocalDateTime.of(2026, 10, 1, 14, 40)
        assertTrue(TextosDaChuva.jaComecou(agora.withMinute(0), agora))
        assertFalse(TextosDaChuva.jaComecou(agora.withHour(15), agora))
        assertEquals("às 18h", TextosDaChuva.quando(agora.withHour(18), agora))
        assertEquals("amanhã às 2h", TextosDaChuva.quando(agora.plusDays(1).withHour(2), agora))
        assertEquals("depois de amanhã às 9h", TextosDaChuva.quando(agora.plusDays(2).withHour(9), agora))
        assertEquals("a partir das 18h", TextosDaChuva.aPartirDe(agora.withHour(18), agora))
        assertEquals("já nesta hora", TextosDaChuva.aPartirDe(agora.withMinute(0), agora))
        assertEquals("a partir das 2h de amanhã", TextosDaChuva.aPartirDe(agora.plusDays(1).withHour(2), agora))
        assertEquals("até as 2h de amanhã", TextosDaChuva.ate(agora.plusDays(1).withHour(2), agora))
        assertEquals("até as 14h de depois de amanhã", TextosDaChuva.ate(agora.plusDays(2), agora))
    }

    @Test
    fun `uma hora e meia-noite levam o artigo certo`() {
        val agora = LocalDateTime.of(2026, 10, 1, 20, 0)
        val meiaNoite = agora.plusDays(1).withHour(0)
        val umaHora = agora.plusDays(1).withHour(1)
        assertEquals("amanhã à 1h", TextosDaChuva.quando(umaHora, agora))
        assertEquals("amanhã à meia-noite", TextosDaChuva.quando(meiaNoite, agora))
        assertEquals("a partir da 1h de amanhã", TextosDaChuva.aPartirDe(umaHora, agora))
        assertEquals("a partir da meia-noite de amanhã", TextosDaChuva.aPartirDe(meiaNoite, agora))
        assertEquals("até a meia-noite de amanhã", TextosDaChuva.ate(meiaNoite, agora))
        assertEquals("até a 1h de amanhã", TextosDaChuva.ate(umaHora, agora))
    }

    @Test
    fun `tela bloqueada nao mostra o nome da propriedade`() {
        assertFalse(TextosDoAviso.TITULO_PUBLICO.contains("Fazenda"))
        assertFalse(TextosDoAviso.CORPO_PUBLICO.contains("%"))
    }
}
