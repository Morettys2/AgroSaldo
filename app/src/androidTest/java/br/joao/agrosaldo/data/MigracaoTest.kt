package br.joao.agrosaldo.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.joao.agrosaldo.data.local.AgroSaldoDatabase
import br.joao.agrosaldo.data.local.Semeador
import br.joao.agrosaldo.data.local.entidade.UsuarioEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Simula quem já usava o app da fase 3 e atualizou para a versão com login.
 *
 * 1. Cria no emulador um arquivo SQLite exatamente como o da versão 1, com
 *    as propriedades e os lançamentos de exemplo.
 * 2. Abre esse arquivo com o Room da versão 2.
 *
 * Ao abrir, o Room roda a MIGRACAO_1_2 e depois CONFERE o banco resultante
 * contra as entidades: se faltar uma coluna, um índice ou uma chave, ele
 * lança erro aqui — e não no celular do produtor.
 */
@RunWith(AndroidJUnit4::class)
class MigracaoTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arquivo = "teste-migracao.db"

    @Before
    @After
    fun limpar() {
        context.deleteDatabase(arquivo)
    }

    /** O SQL que o Room gerou para a versão 1 (está em app/schemas/.../1.json). */
    private val esquemaV1 = listOf(
        "CREATE TABLE IF NOT EXISTS `propriedades` (`id` TEXT NOT NULL, `nome` TEXT NOT NULL, " +
            "`cultura` TEXT NOT NULL, `area_hectares` REAL NOT NULL, `latitude` REAL NOT NULL, " +
            "`longitude` REAL NOT NULL, `atualizado_em` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `lancamentos` (`id` TEXT NOT NULL, `propriedade_id` TEXT NOT NULL, " +
            "`tipo` TEXT NOT NULL, `categoria` TEXT NOT NULL, `valor_centavos` INTEGER NOT NULL, " +
            "`data` INTEGER NOT NULL, `descricao` TEXT NOT NULL, `atualizado_em` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`), FOREIGN KEY(`propriedade_id`) REFERENCES `propriedades`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_lancamentos_propriedade_id` ON `lancamentos` (`propriedade_id`)",
        "CREATE INDEX IF NOT EXISTS `index_lancamentos_data` ON `lancamentos` (`data`)"
    )

    @Test
    fun migracao1Para2MantemTodosOsDadosEPassaNaConferenciaDoRoom() {
        // 1. Banco da versão 1, com dados.
        val caminho = context.getDatabasePath(arquivo)
        caminho.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(caminho, null).use { antigo ->
            esquemaV1.forEach { antigo.execSQL(it) }
            Semeador.semear(
                executar = { sql, argumentos -> antigo.execSQL(sql, argumentos) },
                hoje = LocalDate.of(2026, 9, 30)
            )
            antigo.version = 1
        }

        // 2. Abre com o Room da versão 2. A migração roda aqui.
        val banco = Room.databaseBuilder(context, AgroSaldoDatabase::class.java, arquivo)
            .addMigrations(AgroSaldoDatabase.MIGRACAO_1_2)
            .build()

        try {
            runBlocking {
                // Primeira consulta: é quando o Room abre, migra e confere.
                val conta = UsuarioEntity(
                    id = "u1", nome = "Fábio", email = "fabio@exemplo.com", senhaHash = "hash",
                    consentimentoEm = 1L, versaoTermo = 1, criadoEm = 1L
                )
                banco.usuarioDao().inserir(conta)

                // As propriedades antigas não tinham dono; a primeira conta fica com elas.
                assertEquals(2, banco.usuarioDao().adotarPropriedadesSemDono("u1"))

                // Nenhum lançamento se perdeu na migração.
                assertEquals(9, banco.lancamentoDao().observarDoUsuario("u1").first().size)
                assertEquals(2, banco.propriedadeDao().contarDoUsuario("u1"))
            }
        } finally {
            banco.close()
        }
    }
}
