package br.joao.agrosaldo.core.navigation

/**
 * Todas as rotas do app num lugar só.
 *
 * Nenhuma tela monta string de rota na mão — elas chamam estas funções.
 * Assim, se o formato de uma rota mudar, muda aqui e em mais nenhum lugar.
 *
 * O "?id=" no fim torna o argumento opcional: a mesma rota serve para
 * criar (sem id) e para editar (com id).
 */
object Rotas {
    // Entrada: antes de haver alguém conectado.
    const val LOGIN = "login"
    const val CRIAR_CONTA = "criar_conta"
    const val CONSENTIMENTO = "consentimento"

    // Abre com ou sem conta (vem do consentimento e da tela de conta).
    const val POLITICA = "politica"

    // Conectado, mas a política mudou: precisa aceitar de novo para seguir.
    const val NOVO_CONSENTIMENTO = "novo_consentimento"

    const val CONTA = "conta"
    const val ALTERAR_SENHA = "conta/senha"
    const val AJUSTES_AVISO = "clima/aviso"

    const val INICIO = "inicio"
    const val LANCAMENTOS = "lancamentos"
    const val CLIMA = "clima"
    const val PROPRIEDADES = "propriedades"

    const val ARG_ID = "id"

    const val FORM_LANCAMENTO = "lancamento/form?$ARG_ID={$ARG_ID}"
    const val FORM_PROPRIEDADE = "propriedade/form?$ARG_ID={$ARG_ID}"

    /** Telas de quem ainda não entrou. Conectado, o app sai delas. */
    val DE_ENTRADA = setOf(LOGIN, CRIAR_CONTA, CONSENTIMENTO)


    fun formLancamento(id: String? = null): String =
        if (id == null) "lancamento/form" else "lancamento/form?$ARG_ID=$id"

    fun formPropriedade(id: String? = null): String =
        if (id == null) "propriedade/form" else "propriedade/form?$ARG_ID=$id"
}
