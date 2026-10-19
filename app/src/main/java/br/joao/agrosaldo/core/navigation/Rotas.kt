package br.joao.agrosaldo.core.navigation

import br.joao.agrosaldo.domain.model.TipoLancamento

/**
 * Rotas de navegação. Telas devem usar as funções daqui em vez de montar strings.
 * Argumentos em query (`?id=`) são opcionais: a mesma rota serve para criar e editar.
 */
object Rotas {
    // Fluxo não autenticado.
    const val LOGIN = "login"
    const val CRIAR_CONTA = "criar_conta"
    const val CONSENTIMENTO = "consentimento"

    // Acessível com ou sem sessão.
    const val POLITICA = "politica"

    // Autenticado, mas com nova versão da política pendente de aceite.
    const val NOVO_CONSENTIMENTO = "novo_consentimento"

    const val CONTA = "conta"
    const val ALTERAR_SENHA = "conta/senha"
    const val AJUSTES_AVISO = "clima/aviso"

    const val INICIO = "inicio"
    const val LANCAMENTOS = "lancamentos"
    const val CLIMA = "clima"
    const val PROPRIEDADES = "propriedades"

    // Histórico anual.
    const val HISTORICO = "historico"

    const val ARG_ID = "id"
    const val ARG_COPIA = "copia"
    const val ARG_TIPO = "tipo"

    const val FORM_LANCAMENTO = "lancamento/form?$ARG_ID={$ARG_ID}&$ARG_COPIA={$ARG_COPIA}&$ARG_TIPO={$ARG_TIPO}"
    const val FORM_PROPRIEDADE = "propriedade/form?$ARG_ID={$ARG_ID}"

    /** Telas do fluxo não autenticado; com sessão, redireciona para fora delas. */
    val DE_ENTRADA = setOf(LOGIN, CRIAR_CONTA, CONSENTIMENTO)

    val ABAS = setOf(INICIO, LANCAMENTOS, CLIMA, PROPRIEDADES)

    /**
     * Telas cujo cabeçalho escuro se estende sob a status bar: ícones claros e
     * inset superior tratado pelo próprio cabeçalho.
     */
    val COM_CABECALHO_ESCURO = ABAS + FORM_LANCAMENTO

    /**
     * Parâmetros mutuamente exclusivos, nesta prioridade.
     * @param id edição de um lançamento existente.
     * @param copiaDe novo lançamento pré-preenchido a partir deste.
     * @param tipo novo lançamento com o tipo pré-selecionado.
     */
    fun formLancamento(id: String? = null, copiaDe: String? = null, tipo: TipoLancamento? = null): String = when {
        id != null -> "lancamento/form?$ARG_ID=$id"
        copiaDe != null -> "lancamento/form?$ARG_COPIA=$copiaDe"
        tipo != null -> "lancamento/form?$ARG_TIPO=${tipo.name}"
        else -> "lancamento/form"
    }

    /** Converte o argumento `tipo` da rota; valor inválido vira null. */
    fun tipoDaRota(texto: String?): TipoLancamento? = TipoLancamento.entries.firstOrNull { it.name == texto }

    fun formPropriedade(id: String? = null): String = if (id == null) "propriedade/form" else "propriedade/form?$ARG_ID=$id"
}
