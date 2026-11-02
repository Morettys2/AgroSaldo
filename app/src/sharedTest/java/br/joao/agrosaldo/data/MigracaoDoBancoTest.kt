package br.joao.agrosaldo.data

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.joao.agrosaldo.data.local.AgroSaldoDatabase
import br.joao.agrosaldo.data.local.comMigracoes
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrações a partir da versão 3, a base da produção. O arquivo antigo é montado com o SQL exato do
 * schema exportado (`app/schemas/.../3.json`); o Room abre, roda a migração e confere o schema novo
 * (se a tabela migrada não bater com a entidade, ele falha ao abrir).
 *
 * Usa um arquivo próprio, nunca o `agrosaldo.db`: no aparelho, o teste roda dentro do app instalado.
 * Fica em `src/sharedTest`: roda no JVM com Robolectric e no aparelho (`connectedAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class MigracaoDoBancoTest {

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun apagarArquivo() {
        contexto.deleteDatabase(ARQUIVO)
    }

    @Test
    fun migracao3Para4DaAPropriedadeSemDonoAContaMaisAntigaSemPerderNada() {
        criarNaVersao3 { db ->
            db.execSQL(usuario("nova", criadoEm = 200))
            db.execSQL(usuario("antiga", criadoEm = 100))
            db.execSQL(propriedade("com-dono", dono = "'nova'"))
            db.execSQL(propriedade("sem-dono", dono = "NULL"))
            db.execSQL(lancamento("l1", "com-dono"))
            db.execSQL(lancamento("l2", "sem-dono"))
            db.execSQL(previsao("sem-dono"))
        }

        comBancoMigrado { banco ->
            assertNotNull(banco.propriedadeDao().buscarPorId("sem-dono", "antiga"))
            assertNotNull(banco.propriedadeDao().buscarPorId("com-dono", "nova"))
            assertEquals(listOf("l2"), banco.lancamentoDao().daPropriedade("sem-dono", "antiga").map { it.id })
            assertEquals(listOf("l1"), banco.lancamentoDao().daPropriedade("com-dono", "nova").map { it.id })
            assertNotNull(banco.previsaoDao().buscar("sem-dono"))
        }
    }

    @Test
    fun migracao3Para4SemNenhumaContaTiraAPropriedadeSemDonoComOQueEDela() {
        criarNaVersao3 { db ->
            db.execSQL(propriedade("sem-dono", dono = "NULL"))
            db.execSQL(lancamento("l1", "sem-dono"))
            db.execSQL(previsao("sem-dono"))
        }

        comBancoMigrado { banco ->
            listOf("propriedades", "lancamentos", "previsoes").forEach { tabela ->
                assertEquals(tabela, 0, contarLinhas(banco, tabela))
            }
        }
    }

    @Test
    fun depoisDaMigracao3Para4OBancoRecusaPropriedadeSemDono() {
        criarNaVersao3 { }

        comBancoMigrado { banco ->
            try {
                banco.openHelper.writableDatabase.execSQL(propriedade("sem-dono", dono = "NULL"))
                fail("usuario_id virou obrigatório")
            } catch (esperado: SQLiteConstraintException) {
                assertEquals(0, contarLinhas(banco, "propriedades"))
            }
        }
    }

    /** Cria o arquivo como o app da versão 3 deixaria, com os [dados] de cada teste. */
    private fun criarNaVersao3(dados: (SQLiteDatabase) -> Unit) {
        val arquivo = contexto.getDatabasePath(ARQUIVO)
        arquivo.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(arquivo, null).use { db ->
            ESQUEMA_3.forEach { db.execSQL(it) }
            dados(db)
            db.version = 3
        }
    }

    /** Abre com as mesmas migrações da produção, sobre o arquivo do teste, e fecha no fim. */
    private fun comBancoMigrado(bloco: suspend (AgroSaldoDatabase) -> Unit) {
        val banco = Room.databaseBuilder(contexto, AgroSaldoDatabase::class.java, ARQUIVO)
            .comMigracoes()
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking { bloco(banco) }
        } finally {
            banco.close()
        }
    }

    private fun contarLinhas(banco: AgroSaldoDatabase, tabela: String): Int =
        banco.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tabela").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun usuario(id: String, criadoEm: Long) =
        "INSERT INTO usuarios VALUES ('$id', 'Conta $id', '$id@exemplo.com', 'hash', 1, 1, $criadoEm)"

    private fun propriedade(id: String, dono: String) =
        "INSERT INTO propriedades VALUES ('$id', 'Propriedade $id', 'Milho', 10.0, -23.5, -48.0, 1, $dono)"

    private fun lancamento(id: String, propriedadeId: String) =
        "INSERT INTO lancamentos VALUES ('$id', '$propriedadeId', 'RECEITA', 'VENDA_SAFRA', 1000, 20000, 'Venda', 1)"

    private fun previsao(propriedadeId: String) = "INSERT INTO previsoes VALUES ('$propriedadeId', -23.5, -48.0, '{}', 1)"

    private companion object {
        const val ARQUIVO = "migracao-teste.db"

        /** O `createSql` de cada tabela e índice do `3.json`, com a tabela do Room (`room_master_table`). */
        val ESQUEMA_3 = listOf(
            "CREATE TABLE IF NOT EXISTS `usuarios` (`id` TEXT NOT NULL, `nome` TEXT NOT NULL, `email` TEXT NOT NULL, " +
                "`senha_hash` TEXT NOT NULL, `consentimento_em` INTEGER NOT NULL, `versao_termo` INTEGER NOT NULL, " +
                "`criado_em` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_usuarios_email` ON `usuarios` (`email`)",
            "CREATE TABLE IF NOT EXISTS `propriedades` (`id` TEXT NOT NULL, `nome` TEXT NOT NULL, `cultura` TEXT NOT NULL, " +
                "`area_hectares` REAL NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
                "`atualizado_em` INTEGER NOT NULL, `usuario_id` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`usuario_id`) " +
                "REFERENCES `usuarios`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_propriedades_usuario_id` ON `propriedades` (`usuario_id`)",
            "CREATE TABLE IF NOT EXISTS `lancamentos` (`id` TEXT NOT NULL, `propriedade_id` TEXT NOT NULL, `tipo` TEXT NOT NULL, " +
                "`categoria` TEXT NOT NULL, `valor_centavos` INTEGER NOT NULL, `data` INTEGER NOT NULL, " +
                "`descricao` TEXT NOT NULL, `atualizado_em` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`propriedade_id`) " +
                "REFERENCES `propriedades`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_lancamentos_propriedade_id` ON `lancamentos` (`propriedade_id`)",
            "CREATE INDEX IF NOT EXISTS `index_lancamentos_data` ON `lancamentos` (`data`)",
            "CREATE TABLE IF NOT EXISTS `previsoes` (`propriedade_id` TEXT NOT NULL, `latitude` REAL NOT NULL, " +
                "`longitude` REAL NOT NULL, `resposta_json` TEXT NOT NULL, `obtida_em` INTEGER NOT NULL, " +
                "PRIMARY KEY(`propriedade_id`), FOREIGN KEY(`propriedade_id`) REFERENCES `propriedades`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'dff79049407f02393a175484c14fb5a0')"
        )
    }
}
