package br.joao.agrosaldo.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import br.joao.agrosaldo.data.local.entidade.PropriedadeEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO — Data Access Object. É onde o SQL mora.
 *
 * Você escreve só a assinatura e o SQL; o Room gera a implementação na hora
 * da compilação (é isso que o KSP faz). E ele confere o SQL nesse momento:
 * se uma coluna estiver com nome errado, o erro aparece no build, não no
 * celular do produtor.
 */
@Dao
interface PropriedadeDao {

    /**
     * Retornar Flow faz o Room reemitir a lista sozinho sempre que a tabela
     * mudar. É o que mantém as telas atualizadas sem ninguém pedir.
     *
     * COLLATE NOCASE ordena ignorando maiúsculas: "sítio" e "Sítio" ficam juntos.
     */
    @Query("SELECT * FROM propriedades ORDER BY nome COLLATE NOCASE")
    fun observarTodas(): Flow<List<PropriedadeEntity>>

    @Query("SELECT * FROM propriedades WHERE id = :id")
    suspend fun buscarPorId(id: String): PropriedadeEntity?

    /**
     * Upsert = insere se não existe, atualiza se já existe.
     *
     * Atenção a um detalhe que derruba muita gente: NÃO use
     * @Insert(onConflict = OnConflictStrategy.REPLACE) aqui. O REPLACE do
     * SQLite apaga a linha antiga e insere uma nova — e o apagar dispara o
     * CASCADE, sumindo com todos os lançamentos da propriedade só porque o
     * nome dela foi editado. O @Upsert faz UPDATE de verdade e não tem esse
     * problema. (Os testes deste projeto verificam isso.)
     */
    @Upsert
    suspend fun salvar(propriedade: PropriedadeEntity)

    @Query("DELETE FROM propriedades WHERE id = :id")
    suspend fun excluir(id: String)

    @Query("SELECT COUNT(*) FROM propriedades")
    suspend fun contar(): Int
}
