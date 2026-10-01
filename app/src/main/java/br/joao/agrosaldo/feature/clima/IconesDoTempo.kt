package br.joao.agrosaldo.feature.clima

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import br.joao.agrosaldo.domain.clima.CondicaoTempo

/**
 * Ícone de cada condição. Fica na camada de tela: o domínio só sabe que
 * vai chover, quem decide o desenho é a interface.
 */
fun CondicaoTempo.icone(): ImageVector = when (this) {
    CondicaoTempo.CEU_LIMPO -> Icons.Outlined.WbSunny
    CondicaoTempo.POUCAS_NUVENS -> Icons.Outlined.FilterDrama
    CondicaoTempo.NUBLADO -> Icons.Outlined.Cloud
    CondicaoTempo.NEBLINA -> Icons.Outlined.Dehaze
    // Garoa também é gota: o ícone de pontinhos (Grain) não era reconhecido
    // como chuva. O rótulo ("Garoa") é que diferencia, para quem usa leitor de tela.
    CondicaoTempo.GAROA, CondicaoTempo.CHUVA, CondicaoTempo.PANCADAS -> Icons.Outlined.WaterDrop
    CondicaoTempo.TEMPESTADE -> Icons.Outlined.FlashOn
    CondicaoTempo.NEVE -> Icons.Outlined.AcUnit
    CondicaoTempo.DESCONHECIDA -> Icons.AutoMirrored.Outlined.HelpOutline
}
