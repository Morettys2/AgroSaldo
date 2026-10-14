package br.joao.agrosaldo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.joao.agrosaldo.data.preferencias.PrivacidadePreferencias
import br.joao.agrosaldo.data.sessao.BloqueioPreferencias
import br.joao.agrosaldo.data.sessao.SessaoPreferencias
import br.joao.agrosaldo.data.sessao.TentativasPreferencias
import br.joao.agrosaldo.domain.model.InstanteMonotonico
import br.joao.agrosaldo.domain.repository.EstadoTentativas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * As preferências gravadas em SharedPreferences sobrevivem a "reiniciar o app".
 *
 * Reiniciar = criar uma instância NOVA da classe sobre o mesmo arquivo, que é o que
 * acontece quando o Android mata o processo e o app abre de novo. Roda com Robolectric,
 * que simula o SharedPreferences de verdade no JVM.
 */
@RunWith(RobolectricTestRunner::class)
class PreferenciasTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `senhas erradas continuam contadas depois de reiniciar o app`() {
        val estado = EstadoTentativas(
            falhasSeguidas = 5,
            bloqueadoAte = 1_000_000L,
            fimMonotonico = InstanteMonotonico(boot = 7, desdeOBootMs = 42_000L)
        )
        TentativasPreferencias(contexto).gravar("email-a", estado)

        val depoisDeReiniciar = TentativasPreferencias(contexto)

        assertEquals(estado, depoisDeReiniciar.ler("email-a"))
    }

    @Test
    fun `cada e-mail tem o seu contador`() {
        TentativasPreferencias(contexto).gravar("email-a", EstadoTentativas(falhasSeguidas = 3))

        assertEquals(EstadoTentativas(), TentativasPreferencias(contexto).ler("email-b"))
    }

    @Test
    fun `zerar o contador apaga o registro`() {
        val tentativas = TentativasPreferencias(contexto)
        tentativas.gravar("email-a", EstadoTentativas(falhasSeguidas = 2))
        tentativas.gravar("email-a", EstadoTentativas())

        assertEquals(EstadoTentativas(), TentativasPreferencias(contexto).ler("email-a"))
    }

    @Test
    fun `contador antigo sem e-mail e descartado na atualizacao`() {
        // Formato das versões anteriores: um contador só, sem sufixo.
        contexto.getSharedPreferences("seguranca", Context.MODE_PRIVATE).edit()
            .putInt("falhas_seguidas", 4).commit()

        TentativasPreferencias(contexto)

        val arquivo = contexto.getSharedPreferences("seguranca", Context.MODE_PRIVATE)
        assertTrue("o campo antigo saiu", !arquivo.contains("falhas_seguidas"))
    }

    @Test
    fun `sessao continua aberta depois de reiniciar e some ao sair`() {
        SessaoPreferencias(contexto).iniciar("u1")
        assertEquals("u1", SessaoPreferencias(contexto).usuarioId.value)

        SessaoPreferencias(contexto).encerrar()
        assertNull(SessaoPreferencias(contexto).usuarioId.value)
    }

    @Test
    fun `bloqueio por digital e valores escondidos ficam gravados`() {
        BloqueioPreferencias(contexto).definir(true)
        PrivacidadePreferencias(contexto).definir(true)

        assertTrue(BloqueioPreferencias(contexto).ativo.value)
        assertTrue(PrivacidadePreferencias(contexto).valoresOcultos.value)
    }
}
