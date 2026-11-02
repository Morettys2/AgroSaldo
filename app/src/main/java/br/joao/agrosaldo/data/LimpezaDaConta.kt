package br.joao.agrosaldo.data

import android.app.backup.BackupManager
import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.core.app.NotificationManagerCompat
import br.joao.agrosaldo.data.local.AgroSaldoDatabase
import br.joao.agrosaldo.domain.Despachantes
import br.joao.agrosaldo.domain.repository.AjustesAvisoRepository
import br.joao.agrosaldo.domain.repository.BloqueioRepository
import br.joao.agrosaldo.domain.repository.PrivacidadeRepository
import kotlinx.coroutines.withContext

/**
 * O que o app limpa quando a conta muda ou é excluída, fora do que o domínio já cuida: estado
 * guardado em memória, notificações, ajustes do aparelho e as páginas livres do SQLite.
 * O [br.joao.agrosaldo.domain.conta.ContaService] chama estas duas operações nos momentos certos.
 */
class LimpezaDaConta(
    private val contexto: Context,
    private val selecao: SelecaoAtual,
    private val desfazer: Desfazer,
    private val privacidade: PrivacidadeRepository,
    private val bloqueio: BloqueioRepository,
    private val ajustesAviso: AjustesAvisoRepository,
    private val banco: AgroSaldoDatabase,
    private val despachantes: Despachantes
) {

    /**
     * Limpa o que pertence à conta que está saindo: seleção, desfazer pendente e notificações
     * (elas contêm nomes de propriedades).
     */
    fun limparEstadoDaContaAnterior() {
        selecao.limpar()
        desfazer.esquecer()
        NotificationManagerCompat.from(contexto).cancelAll()
    }

    /**
     * O que a conta excluída deixa fora das tabelas: os ajustes do aviso de chuva dela e as páginas
     * livres do SQLite, que guardam os registros apagados até o VACUUM. Avisa o backup do Android
     * para a próxima cópia já sair sem a conta.
     *
     * Os ajustes do aparelho (valores ocultos e bloqueio por digital) só voltam ao padrão com
     * [ultimaConta]. Com outra conta no aparelho, excluir uma conta qualquer (o cadastro é livre)
     * desligaria a proteção de quem continua usando o celular, sem pedir a digital.
     */
    suspend fun apagarRastrosDaConta(usuarioId: String, ultimaConta: Boolean) {
        if (ultimaConta) {
            privacidade.definir(false)
            bloqueio.definir(false)
        }
        ajustesAviso.excluirDadosDoUsuario(usuarioId)
        BackupManager(contexto).dataChanged()
        compactarBanco()
    }

    /**
     * VACUUM reescreve o banco sem as páginas livres; o checkpoint copia o resultado do WAL
     * para o arquivo e esvazia o WAL. Precisa rodar fora de transação.
     */
    @Suppress("SwallowedException") // Falha esperada (disco cheio): a exclusão já aconteceu e segue valendo.
    private suspend fun compactarBanco() {
        withContext(despachantes.io) {
            try {
                val sqlite = banco.openHelper.writableDatabase
                sqlite.execSQL("VACUUM")
                sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            } catch (e: SQLiteException) {
                // Os registros já foram apagados; sem espaço para o VACUUM, as páginas livres
                // são reaproveitadas pelas próximas gravações.
            }
        }
    }
}
