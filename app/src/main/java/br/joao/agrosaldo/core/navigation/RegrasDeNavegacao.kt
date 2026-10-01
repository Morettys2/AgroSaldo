package br.joao.agrosaldo.core.navigation

/**
 * Para onde o app deve ir quando a sessão muda. Kotlin puro, sem Compose:
 * por isso dá para testar cada caso sem abrir o app.
 *
 * Devolve null quando a tela atual pode continuar.
 *
 * @param rota a tela atual.
 * @param anterior a tela logo abaixo na pilha (de onde a atual foi aberta).
 */
object RegrasDeNavegacao {

    fun destino(rota: String, anterior: String?, logado: Boolean, precisaNovoAceite: Boolean): String? {
        // A política abre de vários lugares. O que decide se ela pode ficar na
        // tela é de onde ela veio: aberta pela Conta, o "voltar" levaria para
        // dentro da conta — então ali ela não conta como lugar seguro.
        fun politicaAbertaDe(origens: Set<String>) = rota == Rotas.POLITICA && anterior in origens

        return when {
            !logado ->
                if (rota in Rotas.DE_ENTRADA || politicaAbertaDe(Rotas.DE_ENTRADA)) null else Rotas.LOGIN
            // Dá para ler a política antes de aceitar; o resto do app, não.
            precisaNovoAceite ->
                if (rota == Rotas.NOVO_CONSENTIMENTO || politicaAbertaDe(setOf(Rotas.NOVO_CONSENTIMENTO))) null
                else Rotas.NOVO_CONSENTIMENTO
            rota in Rotas.DE_ENTRADA || rota == Rotas.NOVO_CONSENTIMENTO -> Rotas.INICIO
            else -> null
        }
    }
}
