package br.joao.agrosaldo.core.navigation

import br.joao.agrosaldo.domain.model.TipoLancamento

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

    // Histórico do ano (RF10), aberto pelo Início.
    const val HISTORICO = "historico"

    const val ARG_ID = "id"
    const val ARG_COPIA = "copia"
    const val ARG_TIPO = "tipo"

    const val FORM_LANCAMENTO = "lancamento/form?$ARG_ID={$ARG_ID}&$ARG_COPIA={$ARG_COPIA}&$ARG_TIPO={$ARG_TIPO}"
    const val FORM_PROPRIEDADE = "propriedade/form?$ARG_ID={$ARG_ID}"

    /** Telas de quem ainda não entrou. Conectado, o app sai delas. */
    val DE_ENTRADA = setOf(LOGIN, CRIAR_CONTA, CONSENTIMENTO)

    /** As quatro abas da barra de baixo. */
    val ABAS = setOf(INICIO, LANCAMENTOS, CLIMA, PROPRIEDADES)

    /**
     * Telas com o cabeçalho verde-noite indo até o topo, por trás do relógio:
     * nelas a barra de status usa ícones claros, e o app não reserva o espaço
     * dela (o próprio cabeçalho faz isso).
     */
    val COM_CABECALHO_ESCURO = ABAS + FORM_LANCAMENTO


    /**
     * @param id para editar um lançamento.
     * @param copiaDe para "lançar de novo": um lançamento novo, preenchido com os dados deste.
     * @param tipo para os atalhos "Despesa" e "Receita" do Início: um lançamento novo já nesse tipo.
     */
    fun formLancamento(id: String? = null, copiaDe: String? = null, tipo: TipoLancamento? = null): String = when {
        id != null -> "lancamento/form?$ARG_ID=$id"
        copiaDe != null -> "lancamento/form?$ARG_COPIA=$copiaDe"
        tipo != null -> "lancamento/form?$ARG_TIPO=${tipo.name}"
        else -> "lancamento/form"
    }

    /** O contrário do [formLancamento]: lê o tipo que veio na rota. Texto estranho vira null. */
    fun tipoDaRota(texto: String?): TipoLancamento? = TipoLancamento.entries.firstOrNull { it.name == texto }

    fun formPropriedade(id: String? = null): String =
        if (id == null) "propriedade/form" else "propriedade/form?$ARG_ID=$id"
}
