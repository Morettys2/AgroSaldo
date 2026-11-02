package br.joao.agrosaldo.data.local

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Registra as migrações a partir da versão 3, a base da produção (as anteriores ficaram na branch
 * `desenvolvimento`). A produção e o teste de migração usam esta mesma função.
 */
fun RoomDatabase.Builder<AgroSaldoDatabase>.comMigracoes(): RoomDatabase.Builder<AgroSaldoDatabase> = addMigrations(MIGRACAO_3_4)

/**
 * 3 → 4: `propriedades.usuario_id` passa a ser obrigatório. Uma propriedade sem dono (só os dados
 * de exemplo antigos geravam isso) vai para a conta mais antiga do aparelho; sem conta nenhuma,
 * ninguém conseguiria vê-la, e ela sai junto com os lançamentos e a previsão dela.
 *
 * O SQLite não transforma uma coluna em NOT NULL: a tabela é recriada (cria a nova, copia, apaga a
 * velha, renomeia), como nas migrações automáticas do Room. O Room só liga as chaves estrangeiras
 * depois das migrações, então apagar a tabela velha não leva os lançamentos em cascata; por isso
 * os filhos da propriedade sem dono saem à mão. No fim, o Room confere as chaves e o schema.
 */
val MIGRACAO_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE propriedades
            SET usuario_id = (SELECT id FROM usuarios ORDER BY criado_em, id LIMIT 1)
            WHERE usuario_id IS NULL
            """.trimIndent()
        )
        db.execSQL("DELETE FROM lancamentos WHERE propriedade_id IN (SELECT id FROM propriedades WHERE usuario_id IS NULL)")
        db.execSQL("DELETE FROM previsoes WHERE propriedade_id IN (SELECT id FROM propriedades WHERE usuario_id IS NULL)")
        db.execSQL("DELETE FROM propriedades WHERE usuario_id IS NULL")

        // createSql da tabela no app/schemas/.../4.json.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `propriedades_nova` (`id` TEXT NOT NULL, `nome` TEXT NOT NULL,
            `cultura` TEXT NOT NULL, `area_hectares` REAL NOT NULL, `latitude` REAL NOT NULL,
            `longitude` REAL NOT NULL, `atualizado_em` INTEGER NOT NULL, `usuario_id` TEXT NOT NULL,
            PRIMARY KEY(`id`), FOREIGN KEY(`usuario_id`) REFERENCES `usuarios`(`id`)
            ON UPDATE NO ACTION ON DELETE CASCADE )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO propriedades_nova (id, nome, cultura, area_hectares, latitude, longitude, atualizado_em, usuario_id)
            SELECT id, nome, cultura, area_hectares, latitude, longitude, atualizado_em, usuario_id FROM propriedades
            """.trimIndent()
        )
        db.execSQL("DROP TABLE propriedades")
        db.execSQL("ALTER TABLE propriedades_nova RENAME TO propriedades")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_propriedades_usuario_id` ON `propriedades` (`usuario_id`)")
    }
}
