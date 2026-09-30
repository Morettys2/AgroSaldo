package br.joao.agrosaldo.data.local

/**
 * O SQL de cada migração do banco, separado do Room.
 *
 * Fica num arquivo sem nenhuma dependência de Android para poder ser
 * conferido fora do celular: os testes executam exatamente estas linhas num
 * SQLite e comparam o resultado com o banco que o Room criaria do zero.
 *
 * Regra de ouro das migrações: depois que uma versão foi para o celular de
 * alguém, a migração dela NUNCA mais muda. Mudou o banco de novo? Nova versão,
 * nova migração.
 */
object MigracoesSql {

    /**
     * Versão 1 → 2: entra o login.
     *
     * 1. Cria a tabela de usuários, com e-mail único.
     * 2. Acrescenta o dono (usuario_id) em propriedades, com chave estrangeira.
     * 3. Cria o índice do dono, que o Room exige para toda chave estrangeira.
     *
     * Por que ALTER TABLE e não "criar tabela nova, copiar e apagar a velha",
     * que é a receita mais comum? Porque apagar a tabela propriedades faria o
     * SQLite apagar as linhas dela uma a uma — e o CASCADE dos lançamentos
     * levaria todos os lançamentos do produtor junto. Acrescentar a coluna
     * não toca em nenhuma linha existente. (Há teste provando as duas coisas.)
     *
     * As propriedades antigas ficam com usuario_id nulo até a primeira conta
     * ser criada, que fica com elas.
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
}
