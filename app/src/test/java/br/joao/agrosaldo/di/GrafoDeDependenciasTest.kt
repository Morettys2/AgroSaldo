package br.joao.agrosaldo.di

import br.joao.agrosaldo.data.Desfazer
import br.joao.agrosaldo.data.LimpezaDaConta
import br.joao.agrosaldo.data.RelogioDoAparelho
import br.joao.agrosaldo.data.SelecaoAtual
import br.joao.agrosaldo.data.clima.FontePrevisao
import br.joao.agrosaldo.data.clima.OpenMeteoHttp
import br.joao.agrosaldo.domain.Despachantes
import br.joao.agrosaldo.domain.DespachantesDoSistema
import br.joao.agrosaldo.domain.RelogioMonotonico
import br.joao.agrosaldo.domain.clima.VerificadorDeChuva
import br.joao.agrosaldo.domain.conta.ContaService
import br.joao.agrosaldo.domain.repository.AgendaDeVerificacao
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.BloqueioRepository
import br.joao.agrosaldo.domain.repository.ClimaRepository
import br.joao.agrosaldo.domain.repository.LancamentoRepository
import br.joao.agrosaldo.domain.repository.PrivacidadeRepository
import br.joao.agrosaldo.domain.repository.PropriedadeRepository
import br.joao.agrosaldo.domain.repository.SessaoRepository
import br.joao.agrosaldo.domain.repository.UsuarioLogadoRepository
import br.joao.agrosaldo.domain.repository.UsuarioRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * O grafo do Hilt montado de verdade (com os módulos do app) dentro do Robolectric: se uma
 * ligação estiver errada ou faltar, o teste não sobe. O Dagger já confere as ligações na
 * compilação; aqui se confere o que só aparece rodando (banco abrindo, singletons).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class GrafoDeDependenciasTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var conta: ContaService

    @Inject lateinit var usuarios: UsuarioRepository

    @Inject lateinit var sessao: SessaoRepository

    @Inject lateinit var propriedades: PropriedadeRepository

    @Inject lateinit var lancamentos: LancamentoRepository

    @Inject lateinit var clima: ClimaRepository

    @Inject lateinit var fontePrevisao: FontePrevisao

    @Inject lateinit var ajustesAviso: AjustesAvisoRepository

    @Inject lateinit var agenda: AgendaDeVerificacao

    @Inject lateinit var verificador: VerificadorDeChuva

    @Inject lateinit var bloqueio: BloqueioRepository

    @Inject lateinit var privacidade: PrivacidadeRepository

    @Inject lateinit var usuarioLogado: UsuarioLogadoRepository

    @Inject lateinit var limpeza: LimpezaDaConta

    @Inject lateinit var selecao: SelecaoAtual

    @Inject lateinit var outraReferenciaDaSelecao: SelecaoAtual

    @Inject lateinit var desfazer: Desfazer

    @Inject lateinit var outraReferenciaDoDesfazer: Desfazer

    @Inject lateinit var relogio: Clock

    @Inject lateinit var relogioMonotonico: RelogioMonotonico

    @Inject lateinit var despachantes: Despachantes

    @Before
    fun injetar() {
        hilt.inject()
    }

    @Test
    fun `o grafo monta e o app comeca sem ninguem conectado`() {
        // Chegar aqui já prova que todas as ligações resolveram (o Hilt falha ao injetar se faltar uma).
        assertFalse(conta.estaLogado())
        assertNull(sessao.usuarioId.value)
    }

    @Test
    fun `o app comeca vazio e sem ninguem conectado`() = runTest {
        assertEquals(emptyList<Any>(), propriedades.observarTodas().first())
        assertEquals(emptyList<Any>(), lancamentos.observarTodos().first())
        assertNull(usuarioLogado.observarNome().first())
        assertNull(selecao.propriedadeId.value)
    }

    @Test
    fun `tempo e despachantes injetados sao os do aparelho`() {
        assertSame(RelogioDoAparelho, relogio)
        assertSame(DespachantesDoSistema, despachantes)
        assertTrue(relogioMonotonico.milissegundos() >= 0L)
    }

    @Test
    fun `a previsao vem da Open-Meteo de verdade`() {
        assertTrue(fontePrevisao is OpenMeteoHttp)
    }

    @Test
    fun `o estado compartilhado e um objeto so no app inteiro`() {
        // Quem pede o SelecaoAtual ou o Desfazer (a Activity, os ViewModels) recebe sempre a mesma instância.
        assertSame(selecao, outraReferenciaDaSelecao)
        assertSame(desfazer, outraReferenciaDoDesfazer)

        selecao.selecionar("p1")
        assertEquals("p1", outraReferenciaDaSelecao.propriedadeId.value)
    }
}
