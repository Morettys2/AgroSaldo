package br.joao.agrosaldo.data.local

/**
 * SQL das migrações, sem dependência de Android: os testes executam estas
 * instruções num SQLite e comparam com o schema gerado pelo Room.
 *
 * Migrações publicadas são imutáveis; qualquer mudança nova exige outra versão.
 */
object MigracoesSql {

    /**
     * 1 -> 2: tabela `usuarios` (e-mail único) e coluna `propriedades.usuario_id`
     * com FK e índice.
     *
     * Usa ALTER TABLE em vez de recriar a tabela: DROP TABLE em `propriedades`
     * dispararia o ON DELETE CASCADE e apagaria os lançamentos (coberto por teste).
     * Propriedades existentes ficam com usuario_id NULL até a primeira conta adotá-las.
     */
    val DE_1_PARA_2: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `usuarios` (`id` TEXT NOT NULL, `nome` TEXT NOT NULL, " +
            "`email` TEXT NOT NULL, `senha_hash` TEXT NOT NULL, `consentimento_em` INTEGER NOT NULL, " +
            "`versao_termo` INTEGER NOT NULL, `criado_em` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_usuarios_email` ON `usuarios` (`email`)",
        "ALTER TABLE `propriedades` ADD COLUMN `usuario_id` TEXT " +
            "REFERENCES `usuarios`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE",
        "CREATE INDEX IF NOT EXISTS `index_propriedades_usuario_id` ON `propriedades` (`usuario_id`)"
    )

    /** 2 -> 3: cria a tabela `previsoes` (cache de clima). Não altera dados existentes. */
    val DE_2_PARA_3: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `previsoes` (`propriedade_id` TEXT NOT NULL, `latitude` REAL NOT NULL, " +
            "`longitude` REAL NOT NULL, `resposta_json` TEXT NOT NULL, `obtida_em` INTEGER NOT NULL, " +
            "PRIMARY KEY(`propriedade_id`), FOREIGN KEY(`propriedade_id`) REFERENCES `propriedades`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
}
