package br.joao.agrosaldo.core.util

/**
 * Textos repetidos em mais de uma tela. Ficam num lugar só para a mesma situação não
 * aparecer escrita de jeitos diferentes ("Selecione" num lugar, "Escolha" em outro).
 *
 * Quando o app for traduzido, isto vira `strings.xml`.
 */
object TextosComuns {
    /** Nenhuma propriedade cadastrada (seletores e cabeçalhos). */
    const val SEM_PROPRIEDADE = "Nenhuma propriedade"

    /** Campo de propriedade ainda sem escolha (formulário de lançamento). */
    const val ESCOLHA_A_PROPRIEDADE = "Escolha a propriedade"

    /** Cultura não preenchida, como aparece na tela (no banco ela fica vazia). */
    const val CULTURA_NAO_INFORMADA = "Não informada"

    /** A cultura como aparece na tela: [CULTURA_NAO_INFORMADA] quando a pessoa não preencheu. */
    fun cultura(cultura: String): String = cultura.ifBlank { CULTURA_NAO_INFORMADA }
}
