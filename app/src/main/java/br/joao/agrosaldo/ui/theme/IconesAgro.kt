package br.joao.agrosaldo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Ícones próprios em traço (grade 24x24, stroke 2, pontas arredondadas), definidos
 * como path data SVG. Usados no lugar dos Material Icons, que não cobrem todas as
 * categorias no mesmo estilo. O traço é preto e recebe a cor via `tint` do `Icon`.
 * Construídos sob demanda (lazy).
 */
object IconesAgro {

    // Categorias
    val Energia by lazy { icone("energia", "M13 2L4 14h7l-1 8 9-12h-7l1-8z") }
    val Defensivos by lazy {
        icone(
            "defensivos",
            "M9 3h4v3H9z",
            "M8 6h6l1 4v10a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V10z",
            "M15 8h3M18 5l2-1M18 11l2 1"
        )
    }
    val Sementes by lazy { icone("sementes", BROTO) }
    val Adubo by lazy { icone("adubo", "M5 19c0-8 6-14 15-14 0 9-6 15-14 15M5 19l7-7") }
    val Combustivel by lazy {
        icone("combustivel", "M4 21V5a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v16M3 21h13M15 10h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V8l-3-3M7 7h5")
    }
    val MaoDeObra by lazy {
        icone(
            "mao_de_obra",
            circulo(9f, 8f, 3.5f),
            "M2.5 20c0-3.5 3-5.5 6.5-5.5s6.5 2 6.5 5.5M16 4.5a3.5 3.5 0 0 1 0 7M18 14.8c2 .6 3.5 2.3 3.5 5.2"
        )
    }
    val Manutencao by lazy {
        icone("manutencao", "M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.5-.5-.5-2.5z")
    }
    val VendaSafra by lazy {
        icone("venda_safra", "M9 3h6l-2 4h-2z", "M10 7C6 9 4 13 4 16a4 4 0 0 0 4 4h8a4 4 0 0 0 4-4c0-3-2-7-6-9", "M10 14h4")
    }
    val Arrendamento by lazy { icone("arrendamento", circulo(8f, 15f, 4f), "M11 12l9-9M17 6l3 3M14 9l2 2") }
    val Outras by lazy { icone("outras", circulo(5f, 12f, 1f), circulo(12f, 12f, 1f), circulo(19f, 12f, 1f)) }

    // Navegação
    val Inicio by lazy { icone("inicio", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z") }
    val Lista by lazy { icone("lista", "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01") }
    val Nuvem by lazy { icone("nuvem", "M7 18h10a4 4 0 0 0 0-8 6 6 0 0 0-11.5 1.5A3.3 3.3 0 0 0 7 18z") }
    val Sitio by lazy { icone("sitio", BROTO) }

    // Ações e indicadores
    val Mais by lazy { icone("mais", "M12 5v14M5 12h14") }
    val Historico by lazy { icone("historico", "M5 20V11M12 20V5M19 20v-7") }
    val Olho by lazy { icone("olho", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circulo(12f, 12f, 3f)) }
    val OlhoFechado by lazy {
        icone(
            "olho_fechado",
            "M3 3l18 18",
            "M10.6 5.1A10 10 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4.2",
            "M6.6 6.6C3.8 8.4 2 12 2 12s3.5 7 10 7a9.6 9.6 0 0 0 5.4-1.6",
            "M9.9 9.9a3 3 0 0 0 4.2 4.2"
        )
    }
    val Busca by lazy { icone("busca", circulo(11f, 11f, 7f), "M20 20l-3.5-3.5") }
    val Fechar by lazy { icone("fechar", "M6 6l12 12M18 6L6 18") }
    val Calendario by lazy {
        icone(
            "calendario",
            "M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z",
            "M3 10h18M8 3v4M16 3v4"
        )
    }
    val Apagar by lazy { icone("apagar", "M21 5H9l-6 7 6 7h12a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1zM17 9l-5 6M12 9l5 6") }
    val Lapis by lazy { icone("lapis", "M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4") }
    val Confirmado by lazy { icone("confirmado", "M5 12l5 5 9-10") }
    val Entrou by lazy { icone("entrou", "M7 17L17 7M9 7h8v8") }
    val Saiu by lazy { icone("saiu", "M7 7l10 10M17 9v8H9") }
    val Chuva by lazy {
        icone("chuva", "M7 15h10a4 4 0 0 0 0-8 6 6 0 0 0-11.5 1.5A3.3 3.3 0 0 0 7 15z", "M8 18l-1 2M12 18l-1 2M16 18l-1 2")
    }
    val Gota by lazy { icone("gota", "M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z") }
    val SetaBaixo by lazy { icone("seta_baixo", "M6 9l6 6 6-6") }
    val SetaEsquerda by lazy { icone("seta_esquerda", "M15 6l-6 6 6 6") }
    val SetaDireita by lazy { icone("seta_direita", "M9 6l6 6-6 6") }
    val MaisOpcoes by lazy { icone("mais_opcoes", circulo(12f, 5f, 1f), circulo(12f, 12f, 1f), circulo(12f, 19f, 1f)) }
    val Copiar by lazy {
        icone(
            "copiar",
            "M10 9h9a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1V10a1 1 0 0 1 1-1z",
            "M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1"
        )
    }
    val Lixeira by lazy { icone("lixeira", "M4 7h16M10 11v6M14 11v6M5 7l1 13a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1l1-13M9 7V4h6v3") }
    val Atualizar by lazy { icone("atualizar", "M20 12a8 8 0 1 1-2.3-5.7M20 4v5h-5") }
    val Sino by lazy { icone("sino", "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z", "M10 21a2 2 0 0 0 4 0") }
    val Conta by lazy { icone("conta", circulo(12f, 8f, 4f), "M4 21c0-4 3.6-6 8-6s8 2 8 6") }

    private const val BROTO = "M12 21v-8M12 13c0-4-3-6-7-6 0 4 3 6 7 6zM12 11c0-4 3-6 7-6 0 4-3 6-7 6z"

    /** Círculo em path data: dois arcos de 180°. */
    private fun circulo(cx: Float, cy: Float, r: Float): String = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun icone(nome: String, vararg caminhos: String): ImageVector = ImageVector.Builder(
        name = nome,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        caminhos.forEach { caminho ->
            addPath(
                pathData = addPathNodes(caminho),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}
