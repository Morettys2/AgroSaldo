package br.joao.agrosaldo.domain

import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.model.Usuario
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Limites de tamanho e valores inválidos que o domínio recusa mesmo que a tela deixe passar. */
class ValidacaoDeEntradasTest {

    private fun lancamento(descricao: String) = Lancamento(
        propriedadeId = "p1",
        tipo = TipoLancamento.DESPESA,
        categoria = CategoriaLancamento.ADUBO,
        valorCentavos = 100L,
        data = LocalDate.of(2026, 10, 1),
        descricao = descricao
    )

    private fun propriedade(nome: String = "Sítio", cultura: String = "Milho", area: Double = 3.0): Propriedade =
        Propriedade(nome = nome, cultura = cultura, areaHectares = area, latitude = -21.0, longitude = -46.0)

    private fun recusa(mensagem: String, bloco: () -> Unit) {
        try {
            bloco()
            fail("Deveria recusar: $mensagem")
        } catch (esperado: IllegalArgumentException) {
            assertEquals(mensagem, esperado.message)
        }
    }

    @Test
    fun `descricao de 200 caracteres passa e de 201 e recusada`() {
        lancamento("a".repeat(Lancamento.DESCRICAO_MAXIMA))
        recusa("A descrição pode ter no máximo 200 caracteres.") { lancamento("a".repeat(Lancamento.DESCRICAO_MAXIMA + 1)) }
    }

    @Test
    fun `nome e cultura da propriedade respeitam o tamanho maximo`() {
        propriedade(nome = "n".repeat(Propriedade.NOME_MAXIMO), cultura = "c".repeat(Propriedade.CULTURA_MAXIMA))
        recusa("O nome pode ter no máximo 80 caracteres.") { propriedade(nome = "n".repeat(Propriedade.NOME_MAXIMO + 1)) }
        recusa("A cultura pode ter no máximo 60 caracteres.") { propriedade(cultura = "c".repeat(Propriedade.CULTURA_MAXIMA + 1)) }
    }

    @Test
    fun `area infinita ou NaN e recusada no dominio`() {
        recusa("A área deve ser maior que zero.") { propriedade(area = Double.POSITIVE_INFINITY) }
        recusa("A área deve ser maior que zero.") { propriedade(area = Double.NaN) }
        recusa("A área deve ser maior que zero.") { propriedade(area = 0.0) }
    }

    @Test
    fun `area acima de um milhao de hectares e recusada e o limite exato passa`() {
        propriedade(area = Propriedade.AREA_MAXIMA_HECTARES)
        recusa("A área máxima é 1.000.000 ha.") { propriedade(area = Propriedade.AREA_MAXIMA_HECTARES + 0.01) }
    }

    @Test
    fun `email com mais de 254 caracteres nao e valido`() {
        val local = "a".repeat(RegrasDeConta.EMAIL_MAXIMO - "@x.com".length)
        assertTrue(RegrasDeConta.emailValido("$local@x.com"))
        assertFalse(RegrasDeConta.emailValido("${local}a@x.com"))
    }

    // ---------- conta (ENT-01) ----------

    private fun usuario(nome: String = "Maria Souza", email: String = "maria@exemplo.com") = Usuario(
        nome = nome,
        email = email,
        senhaHash = "hash",
        consentimentoEm = Instant.EPOCH,
        versaoTermo = 1,
        criadoEm = Instant.EPOCH
    )

    @Test
    fun `nome do usuario de 80 caracteres passa e de 81 e recusado`() {
        usuario(nome = "N".repeat(Usuario.NOME_MAXIMO))
        recusa("O nome pode ter no máximo 80 caracteres.") { usuario(nome = "N".repeat(Usuario.NOME_MAXIMO + 1)) }
    }

    @Test
    fun `e-mail do usuario de 254 caracteres passa e de 255 e recusado`() {
        val dominio = "@exemplo.com"
        usuario(email = "a".repeat(Usuario.EMAIL_MAXIMO - dominio.length) + dominio)
        recusa("O e-mail pode ter no máximo 254 caracteres.") {
            usuario(email = "a".repeat(Usuario.EMAIL_MAXIMO - dominio.length + 1) + dominio)
        }
    }
}
