package br.joao.agrosaldo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.joao.agrosaldo.data.local.AgroSaldoDatabase
import br.joao.agrosaldo.data.preferencias.AjustesAvisoMemoria
import br.joao.agrosaldo.data.preferencias.PrivacidadeMemoria
import br.joao.agrosaldo.data.sessao.BloqueioMemoria
import br.joao.agrosaldo.domain.RelogioMonotonicoIndisponivel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * O que sobra da conta fora das tabelas. O bloqueio por digital e os valores ocultos são do
 * aparelho: excluir uma conta só os desliga quando não sobra conta nenhuma (SEG-01).
 */
@RunWith(RobolectricTestRunner::class)
class LimpezaDaContaTest {

    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private val banco = Room.inMemoryDatabaseBuilder(contexto, AgroSaldoDatabase::class.java).allowMainThreadQueries().build()
    private val bloqueio = BloqueioMemoria(inicial = true)
    private val privacidade = PrivacidadeMemoria(inicial = true)

    private val limpeza = LimpezaDaConta(
        contexto = contexto,
        selecao = SelecaoAtual(relogio = relogioEm(LocalDate.of(2026, 10, 28))),
        desfazer = Desfazer(RelogioMonotonicoIndisponivel),
        privacidade = privacidade,
        bloqueio = bloqueio,
        ajustesAviso = AjustesAvisoMemoria(),
        banco = banco,
        despachantes = DespachantesDeTeste(Dispatchers.Unconfined)
    )

    @After
    fun fechar() = banco.close()

    @Test
    fun `excluir uma conta quando ainda restam outras nao desliga o bloqueio do aparelho`() = runTest {
        limpeza.apagarRastrosDaConta("u1", ultimaConta = false)

        assertTrue("o bloqueio de quem continua no aparelho fica ligado", bloqueio.ativo.value)
        assertTrue(privacidade.valoresOcultos.value)
    }

    @Test
    fun `excluir a ultima conta volta os ajustes do aparelho ao padrao`() = runTest {
        limpeza.apagarRastrosDaConta("u1", ultimaConta = true)

        assertFalse(bloqueio.ativo.value)
        assertFalse(privacidade.valoresOcultos.value)
    }
}
