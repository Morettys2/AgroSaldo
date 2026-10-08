package br.joao.agrosaldo.clima

import br.joao.agrosaldo.core.util.DestinoDaPermissao
import br.joao.agrosaldo.core.util.RegrasDeNotificacao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Botão "Permitir notificações" do cartão do aviso de chuva. */
class PermissaoNotificacaoTest {

    @Test
    fun `permissao pendente pede pelo dialogo do sistema`() {
        assertEquals(
            DestinoDaPermissao.DIALOGO,
            // No Android 13+ sem permissão, as notificações do app também aparecem desligadas.
            RegrasDeNotificacao.destino(permissaoPendente = true, appLigado = false, canalLigado = true)
        )
    }

    @Test
    fun `notificacoes do app ou canal desligados levam aos ajustes`() {
        // Ex.: Android 8 a 12, onde não há diálogo de permissão.
        assertEquals(
            DestinoDaPermissao.AJUSTES_DO_APP,
            RegrasDeNotificacao.destino(permissaoPendente = false, appLigado = false, canalLigado = false)
        )
        assertEquals(
            DestinoDaPermissao.AJUSTES_DO_CANAL,
            RegrasDeNotificacao.destino(permissaoPendente = false, appLigado = true, canalLigado = false)
        )
        assertNull(RegrasDeNotificacao.destino(permissaoPendente = false, appLigado = true, canalLigado = true))
    }

    @Test
    fun `permissao negada de vez e reconhecida para abrir os ajustes`() {
        // O sistema recusou sem mostrar o diálogo: a explicação continua false antes e depois.
        assertTrue(
            RegrasDeNotificacao.dialogoBloqueado(aceitou = false, explicarAntes = false, explicarDepois = false, jaPediuAntes = true)
        )
    }

    @Test
    fun `fechar o dialogo no primeiro pedido nao abre os ajustes`() {
        // Tocar fora ou voltar no 1º pedido também deixa a explicação false antes e depois.
        assertFalse(
            RegrasDeNotificacao.dialogoBloqueado(aceitou = false, explicarAntes = false, explicarDepois = false, jaPediuAntes = false)
        )
    }

    @Test
    fun `recusa feita no dialogo nao abre os ajustes logo em seguida`() {
        // 1ª recusa: a explicação passa a ser true.
        assertFalse(
            RegrasDeNotificacao.dialogoBloqueado(aceitou = false, explicarAntes = false, explicarDepois = true, jaPediuAntes = false)
        )
        // 2ª recusa: era true antes de pedir.
        assertFalse(
            RegrasDeNotificacao.dialogoBloqueado(aceitou = false, explicarAntes = true, explicarDepois = false, jaPediuAntes = true)
        )
        assertFalse(
            RegrasDeNotificacao.dialogoBloqueado(aceitou = true, explicarAntes = false, explicarDepois = false, jaPediuAntes = true)
        )
    }
}
