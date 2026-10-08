package br.joao.agrosaldo.core.navigation

/**
 * Redirecionamento por estado de sessão, sem dependência de Compose (testável em JVM).
 *
 * [destino] devolve a rota para onde ir, ou null se a tela atual é permitida.
 * `rota` é a tela atual; `anterior`, a entrada logo abaixo na back stack.
 */
object RegrasDeNavegacao {

    fun destino(rota: String, anterior: String?, logado: Boolean, precisaNovoAceite: Boolean): String? {
        // A política só é permitida conforme a origem: aberta pela Conta, o "voltar"
        // levaria para uma área autenticada.
        fun politicaAbertaDe(origens: Set<String>) = rota == Rotas.POLITICA && anterior in origens

        return when {
            !logado ->
                if (rota in Rotas.DE_ENTRADA || politicaAbertaDe(Rotas.DE_ENTRADA)) null else Rotas.LOGIN
            // Antes do novo aceite, só a política pode ser lida.
            precisaNovoAceite ->
                if (rota == Rotas.NOVO_CONSENTIMENTO || politicaAbertaDe(setOf(Rotas.NOVO_CONSENTIMENTO))) null
                else Rotas.NOVO_CONSENTIMENTO
            rota in Rotas.DE_ENTRADA || rota == Rotas.NOVO_CONSENTIMENTO -> Rotas.INICIO
            else -> null
        }
    }
}
