package br.joao.agrosaldo.core.navigation

/**
 * Redirecionamento por estado de sessão, sem dependência de Compose (testável em JVM).
 *
 * [destino] devolve a rota para onde ir, ou null se a tela atual é permitida.
 * `rota` é a tela atual; `anterior`, a entrada logo abaixo na back stack;
 * `precisaNovoAceite` é null enquanto a conta conectada não foi lida.
 */
object RegrasDeNavegacao {

    fun destino(rota: String, anterior: String?, logado: Boolean, precisaNovoAceite: Boolean?): String? {
        // A política só é permitida conforme a origem: aberta pela Conta, o "voltar"
        // levaria para uma área autenticada.
        fun politicaAbertaDe(origens: Set<String>) = rota == Rotas.POLITICA && anterior in origens

        return when {
            !logado ->
                if (rota in Rotas.DE_ENTRADA || politicaAbertaDe(Rotas.DE_ENTRADA)) null else Rotas.LOGIN
            // Ainda sem saber do novo aceite: espera, com a tela coberta (ver telaPodeAparecer).
            precisaNovoAceite == null -> null
            // Antes do novo aceite, só a política pode ser lida.
            precisaNovoAceite ->
                if (rota == Rotas.NOVO_CONSENTIMENTO || politicaAbertaDe(setOf(Rotas.NOVO_CONSENTIMENTO))) null
                else Rotas.NOVO_CONSENTIMENTO
            rota in Rotas.DE_ENTRADA || rota == Rotas.NOVO_CONSENTIMENTO -> Rotas.INICIO
            else -> null
        }
    }

    /**
     * Se a tela atual pode ser exibida: o NavHost já tem rota, o estado da sessão é
     * conhecido e nenhum desvio de [destino] está pendente. Enquanto false, a navegação
     * cobre o conteúdo, para nenhuma tela aparecer por alguns quadros antes do desvio.
     */
    fun telaPodeAparecer(rota: String?, anterior: String?, logado: Boolean, precisaNovoAceite: Boolean?): Boolean =
        rota != null && (!logado || precisaNovoAceite != null) &&
            destino(rota, anterior, logado, precisaNovoAceite) == null
}
