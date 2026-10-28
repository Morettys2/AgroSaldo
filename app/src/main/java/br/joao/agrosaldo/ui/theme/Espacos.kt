package br.joao.agrosaldo.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Escala de espaçamentos do app (margens, paddings e distâncias entre itens).
 *
 * Tudo é múltiplo de 4 dp, como recomenda o Material 3. Usar sempre estes nomes, em vez
 * de `20.dp` solto, deixa as telas alinhadas entre si: a margem lateral, por exemplo,
 * era 20 em quase todo lugar e 16 ou 18 em outros. Ajustes finos (2, 6, 10 dp dentro de
 * um componente) podem continuar literais.
 */
object Espacos {
    /** 4 dp: entre ícone e texto colados, entre linhas de um mesmo bloco. */
    val minimo = 4.dp

    /** 8 dp: entre itens de uma mesma linha ou de um grupo pequeno. */
    val pequeno = 8.dp

    /** 12 dp: entre cartões de uma lista. */
    val medio = 12.dp

    /** 16 dp: entre blocos dentro de um cartão. */
    val padrao = 16.dp

    /** 20 dp: margem lateral das telas e padding interno dos cartões. */
    val largo = 20.dp

    /** 24 dp: separação entre seções. */
    val grande = 24.dp

    /** 28 dp: respiro do topo das telas de entrada e da base do cabeçalho do lançamento. */
    val amplo = 28.dp

    /** 32 dp: respiro grande (antes do rodapé, estados vazios). */
    val enorme = 32.dp

    /** Margem lateral de todas as telas. */
    val margemDaTela = largo

    /** Menor área de toque aceitável (diretriz de acessibilidade do Android). */
    val alvoDeToque = 48.dp

    /** Altura mínima dos botões principais; cresce com a fonte do sistema. */
    val alturaDoBotao = 56.dp
}
