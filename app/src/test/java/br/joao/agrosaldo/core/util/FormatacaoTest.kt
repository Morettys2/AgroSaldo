package br.joao.agrosaldo.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FormatacaoTest {

    /**
     * Troca espaços especiais por espaço comum: o separador entre "R$" e o número (NBSP ou
     * NNBSP) varia entre versões do Java e do Android.
     */
    private fun normalizar(texto: String) = texto.replace(Regex("[\\s\\u00A0\\u202F]"), " ")

    @Test
    fun `centavos viram reais com virgula e ponto de milhar`() {
        assertEquals("R$ 1.250,50", normalizar(125_050L.emReal()))
        assertEquals("R$ 0,05", normalizar(5L.emReal()))
        assertEquals("R$ 18.000,00", normalizar(1_800_000L.emReal()))
    }

    @Test
    fun `versao curta omite os centavos`() {
        assertEquals("R$ 1.250", normalizar(125_000L.emRealCurto()))
    }

    @Test
    fun `versao curta nunca arredonda centavos`() {
        assertEquals("R$ 0,50", normalizar(50L.emRealCurto()))
        assertEquals("R$ 1,50", normalizar(150L.emRealCurto()))
        assertEquals("R$ 12,50", normalizar(1_250L.emRealCurto()))
        assertEquals("R$ 13,50", normalizar(1_350L.emRealCurto()))
        assertEquals("R$ 1.249,99", normalizar(124_999L.emRealCurto()))
    }

    @Test
    fun `versao curta de valor negativo com centavos igual a completa`() {
        assertEquals((-1_250L).emReal(), (-1_250L).emRealCurto())
    }

    @Test
    fun `maior valor aceito no campo e formatado sem erro de ponto flutuante`() {
        assertEquals("R$ 9.999.999,99", normalizar(999_999_999L.emReal()))
        assertEquals("R$ 9.999.999,99", normalizar(999_999_999L.emRealCurto()))
    }

    @Test
    fun `valor negativo aparece com sinal`() {
        assertEquals("-R$ 320,00", normalizar((-32_000L).emReal()))
    }

    @Test
    fun `digitos viram centavos ignorando qualquer outro caractere`() {
        assertEquals(125_050L, "125050".digitosParaCentavos())
        assertEquals(125_050L, "R$ 1.250,50".digitosParaCentavos())
        assertNull("".digitosParaCentavos())
        assertNull("abc".digitosParaCentavos())
    }

    @Test
    fun `entrada com mais de 9 digitos e recusada em vez de cortada`() {
        // Cortar faria "R$ 12.345.678,90" virar R$ 1.234.567,89, um valor 10 vezes menor.
        assertNull("1234567890".digitosParaCentavos())
        assertNull("99999999999999".digitosParaCentavos())
        assertEquals(999_999_999L, "999999999".digitosParaCentavos())
    }

    @Test
    fun `zeros a esquerda nao contam para o limite de digitos`() {
        assertEquals(5L, "R$ 0,05".digitosParaCentavos())
        assertEquals(5L, "0000000005".digitosParaCentavos())
        assertEquals(0L, "000".digitosParaCentavos())
    }

    @Test
    fun `data valida em dd-MM-aaaa e aceita`() {
        assertEquals(LocalDate.of(2026, 9, 28), "28/09/2026".paraLocalDateOuNulo())
        assertEquals(LocalDate.of(2026, 9, 28), " 28/09/2026 ".paraLocalDateOuNulo())
    }

    @Test
    fun `data impossivel e recusada em vez de corrigida em silencio`() {
        assertNull("31/02/2026".paraLocalDateOuNulo())
        assertNull("29/02/2026".paraLocalDateOuNulo()) // 2026 não é bissexto.
        assertEquals(LocalDate.of(2028, 2, 29), "29/02/2028".paraLocalDateOuNulo())
    }

    @Test
    fun `texto que nao e data e recusado`() {
        assertNull("amanhã".paraLocalDateOuNulo())
        assertNull("2026-09-28".paraLocalDateOuNulo())
        assertNull("".paraLocalDateOuNulo())
    }

    @Test
    fun `formatar e ler de volta da a mesma data`() {
        val data = LocalDate.of(2026, 1, 5)
        assertEquals("05/01/2026", data.formatarBr())
        assertEquals(data, data.formatarBr().paraLocalDateOuNulo())
        assertEquals("05/01", data.formatarCurto())
    }

    @Test
    fun `rotulo do mes tem inicial maiuscula`() {
        assertEquals("Setembro de 2026", YearMonth.of(2026, 9).rotulo())
        assertEquals("Março de 2027", YearMonth.of(2027, 3).rotulo())
    }

    @Test
    fun `pertence ao mes compara mes e ano`() {
        assertTrue(LocalDate.of(2026, 9, 30).pertenceAo(YearMonth.of(2026, 9)))
        assertFalse(LocalDate.of(2025, 9, 30).pertenceAo(YearMonth.of(2026, 9)))
    }

    @Test
    fun `espera do bloqueio aparece em minutos e segundos`() {
        assertEquals("30 segundos", formatarEspera(30))
        assertEquals("1 segundo", formatarEspera(1))
        assertEquals("1 segundo", formatarEspera(0))
        assertEquals("1 minuto", formatarEspera(60))
        assertEquals("2 minutos e 30 segundos", formatarEspera(150))
        assertEquals("15 minutos", formatarEspera(900))
    }

    @Test
    fun `calendario - o dia escolhido nao volta um dia no fuso do Brasil`() {
        val dia = LocalDate.of(2026, 10, 4)
        // O DatePicker do Material trabalha com meia-noite UTC do dia escolhido.
        assertEquals(1_791_072_000_000L, dia.emMillisUtc())
        assertEquals(dia, dia.emMillisUtc().paraLocalDateUtc())
        // Qualquer instante do mesmo dia UTC volta para o mesmo LocalDate.
        assertEquals(dia, (dia.emMillisUtc() + 23 * 3_600_000L).paraLocalDateUtc())
    }

    @Test
    fun `siglas e nomes dos meses em portugues`() {
        assertEquals("Jan", YearMonth.of(2026, 1).siglaDoMes())
        assertEquals("Set", YearMonth.of(2026, 9).siglaDoMes())
        assertEquals("Mai", YearMonth.of(2026, 5).siglaDoMes())
        assertEquals("Março", YearMonth.of(2026, 3).nomeDoMes())
    }

    @Test
    fun `dia relativo usa hoje, ontem, amanha e o nome do mes`() {
        val hoje = LocalDate.of(2026, 10, 4)
        assertEquals("Hoje", hoje.rotuloRelativo(hoje))
        assertEquals("Ontem", hoje.minusDays(1).rotuloRelativo(hoje))
        assertEquals("Amanhã", hoje.plusDays(1).rotuloRelativo(hoje))
        assertEquals("2 de outubro", LocalDate.of(2026, 10, 2).rotuloRelativo(hoje))
        assertEquals("o ano aparece se for outro", "2 de outubro de 2025", LocalDate.of(2025, 10, 2).rotuloRelativo(hoje))
        assertEquals("virada de ano: 31/12 é ontem", "Ontem", LocalDate.of(2025, 12, 31).rotuloRelativo(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `dia relativo curto usa a sigla do mes sem ponto`() {
        val hoje = LocalDate.of(2026, 10, 4)
        assertEquals("Hoje", hoje.rotuloRelativoCurto(hoje))
        assertEquals("Ontem", hoje.minusDays(1).rotuloRelativoCurto(hoje))
        assertEquals("Amanhã", hoje.plusDays(1).rotuloRelativoCurto(hoje))
        assertEquals("2 out", LocalDate.of(2026, 10, 2).rotuloRelativoCurto(hoje))
        assertEquals("2 out 2025", LocalDate.of(2025, 10, 2).rotuloRelativoCurto(hoje))
    }

    @Test
    fun `valor escondido tem sempre o mesmo tamanho, qualquer que seja o valor`() {
        assertEquals(VALOR_ESCONDIDO, 5L.emReal(oculto = true))
        assertEquals(VALOR_ESCONDIDO, 1_800_000_00L.emReal(oculto = true))
        assertEquals(VALOR_ESCONDIDO, 125_000L.emRealCurto(oculto = true))
    }

    @Test
    fun `com o olho aberto o valor aparece normal`() {
        assertEquals(125_050L.emReal(), 125_050L.emReal(oculto = false))
        assertEquals(125_000L.emRealCurto(), 125_000L.emRealCurto(oculto = false))
    }
}
