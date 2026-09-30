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
 *
 * Desde a chegada do login, toda consulta recebe o usuário. Não existe
 * "buscar propriedade por id" sem dizer de quem: é o banco que garante que
 * uma conta nunca enxerga os dados da outra.
 */
@Dao
interface PropriedadeDao {

    /**
     * Retornar Flow faz o Room reemitir a lista sozinho sempre que a tabela
     * mudar. É o que mantém as telas atualizadas sem ninguém pedir.
     *
     * COLLATE NOCASE ordena ignorando maiúsculas: "sítio" e "Sítio" ficam juntos.
     */
    @Query("SELECT * FROM propriedades WHERE usuario_id = :usuarioId ORDER BY nome COLLATE NOCASE")
    fun observarDoUsuario(usuarioId: String): Flow<List<PropriedadeEntity>>

    @Query("SELECT * FROM propriedades WHERE id = :id AND usuario_id = :usuarioId")
    suspend fun buscarPorId(id: String, usuarioId: String): PropriedadeEntity?

    /** Dono atual da propriedade, ou null se ela não existe ou ainda não tem dono. */
    @Query("SELECT usuario_id FROM propriedades WHERE id = :id")
    suspend fun donoDe(id: String): String?

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

    @Query("DELETE FROM propriedades WHERE id = :id AND usuario_id = :usuarioId")
    suspend fun excluir(id: String, usuarioId: String)

    @Query("SELECT COUNT(*) FROM propriedades WHERE usuario_id = :usuarioId")
    suspend fun contarDoUsuario(usuarioId: String): Int
}
