package br.joao.agrosaldo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import br.joao.agrosaldo.data.local.dao.LancamentoDao
import br.joao.agrosaldo.data.local.dao.PrevisaoDao
import br.joao.agrosaldo.data.local.dao.PropriedadeDao
import br.joao.agrosaldo.data.local.dao.UsuarioDao
import br.joao.agrosaldo.data.local.entidade.LancamentoEntity
import br.joao.agrosaldo.data.local.entidade.PrevisaoEntity
import br.joao.agrosaldo.data.local.entidade.PropriedadeEntity
import br.joao.agrosaldo.data.local.entidade.UsuarioEntity

/**
 * Banco Room do app (`@Database`), gravado no arquivo [NOME_ARQUIVO] no armazenamento privado
 * do aplicativo. Registra as quatro entidades e expõe um DAO por tabela.
 *
 * Versão do schema: 4, exportado em `app/schemas`. Mudar o schema exige subir a versão e
 * escrever uma `Migration` (em `Migracoes.kt`), com o teste dela.
 */
@Database(
    entities = [
        UsuarioEntity::class,
        PropriedadeEntity::class,
        LancamentoEntity::class,
        PrevisaoEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AgroSaldoDatabase : RoomDatabase() {

    /** DAOs: o Room gera a implementação de cada um na compilação (KSP). */
    abstract fun usuarioDao(): UsuarioDao
    abstract fun propriedadeDao(): PropriedadeDao
    abstract fun lancamentoDao(): LancamentoDao
    abstract fun previsaoDao(): PrevisaoDao

    companion object {
        const val NOME_ARQUIVO = "agrosaldo.db"

        /**
         * Cria o banco com `Room.databaseBuilder`. Deve existir uma única instância por processo:
         * o `BancoModule` do Hilt a fornece como `@Singleton`.
         */
        fun criar(context: Context): AgroSaldoDatabase = Room.databaseBuilder(
            context.applicationContext,
            AgroSaldoDatabase::class.java,
            NOME_ARQUIVO
        )
            .comMigracoes()
            // Sem fallbackToDestructiveMigration: sem a Migration, abrir o banco falha em vez de
            // recriá-lo vazio (o que apagaria os dados).
            .build()
    }
}
