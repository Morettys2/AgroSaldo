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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Atualização direta do banco v1 para a versão atual (1→2: usuários/login; 2→3: previsão do clima).
 *
 * Cria um SQLite com o esquema v1 e os dados de exemplo e o abre com o Room atual. Ao abrir, o Room
 * aplica as migrações e valida o esquema resultante contra as entidades, falhando se faltar
 * coluna, índice ou chave.
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

    /** SQL gerado pelo Room para a versão 1 (app/schemas/.../1.json). */
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
    fun migracaoDaVersao1AteAAtualMantemTodosOsDadosEPassaNaConferenciaDoRoom() {
        // Banco v1 com os dados de exemplo.
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

        val banco = Room.databaseBuilder(context, AgroSaldoDatabase::class.java, arquivo)
            .addMigrations(*AgroSaldoDatabase.MIGRACOES)
            .build()

        try {
            runBlocking {
                // Primeira consulta: o Room abre o banco, migra e valida o esquema.
                val conta = UsuarioEntity(
                    id = "u1", nome = "Fábio", email = "fabio@exemplo.com", senhaHash = "hash",
                    consentimentoEm = 1L, versaoTermo = 1, criadoEm = 1L
                )
                banco.usuarioDao().inserir(conta)

                // Propriedades da v1 não têm dono; a primeira conta as adota.
                assertEquals(2, banco.usuarioDao().adotarPropriedadesSemDono("u1"))

                // Nenhum lançamento perdido na migração.
                assertEquals(9, banco.lancamentoDao().observarDoUsuario("u1").first().size)
                assertEquals(2, banco.propriedadeDao().contarDoUsuario("u1"))

                // Tabela de previsão (v3) existe e começa vazia.
                assertNull(banco.previsaoDao().buscar(DadosDeExemplo.ID_FAZENDA))
            }
        } finally {
            banco.close()
        }
    }
}
