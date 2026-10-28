package br.joao.agrosaldo.feature.lancamento

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.core.ui.cor
import br.joao.agrosaldo.core.ui.icone
import br.joao.agrosaldo.core.util.TextosComuns
import br.joao.agrosaldo.core.util.emMillisUtc
import br.joao.agrosaldo.core.util.paraLocalDateUtc
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro
import br.joao.agrosaldo.ui.theme.agro
import java.time.LocalDate

/*
 * Campos do formulário de lançamento: categoria, data, propriedade e descrição.
 */

/** Todas as categorias do tipo, sem truncamento. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Categorias(categorias: List<CategoriaLancamento>, escolhida: CategoriaLancamento, aoEscolher: (CategoriaLancamento) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Categoria",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Espacos.pequeno),
            verticalArrangement = Arrangement.spacedBy(Espacos.pequeno),
            modifier = Modifier.selectableGroup()
        ) {
            categorias.forEach { categoria ->
                ChipCategoria(
                    categoria = categoria,
                    selecionada = categoria == escolhida,
                    aoClicar = { aoEscolher(categoria) }
                )
            }
        }
    }
}

@Composable
internal fun ChipCategoria(categoria: CategoriaLancamento, selecionada: Boolean, aoClicar: () -> Unit) {
    val cor = categoria.cor()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacos.pequeno),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (selecionada) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
            .selectable(selected = selecionada, role = Role.RadioButton, onClick = aoClicar)
            .padding(start = Espacos.pequeno, end = Espacos.padrao, top = Espacos.pequeno, bottom = Espacos.pequeno)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (selecionada) MaterialTheme.agro.destaque else cor.fundo)
        ) {
            Icon(
                // Selecionada exibe ✓, indicando o estado sem depender só da cor.
                imageVector = if (selecionada) IconesAgro.Confirmado else categoria.icone(),
                contentDescription = null,
                tint = if (selecionada) MaterialTheme.agro.sobreDestaque else cor.conteudo,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = categoria.rotulo,
            style = if (selecionada) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (selecionada) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Campo clicável com ícone, legenda e valor (ex.: "Data / Hoje, 4 out"). */
@Composable
internal fun CampoDeEscolha(
    icone: ImageVector,
    legenda: String,
    valor: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    erro: Boolean = false
) {
    Surface(
        onClick = aoClicar,
        enabled = habilitado,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (erro) BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null,
        modifier = modifier.heightIn(min = 60.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = Espacos.medio, vertical = 10.dp)
        ) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(20.dp))
            Column {
                Text(legenda, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(valor, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun CampoData(
    rotulo: String,
    erro: String?,
    aoUsarHoje: () -> Unit,
    aoUsarOntem: () -> Unit,
    aoAbrirCalendario: () -> Unit,
    modifier: Modifier = Modifier
) {
    var aberto by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Espacos.minimo)) {
        Box {
            CampoDeEscolha(
                icone = IconesAgro.Calendario,
                legenda = "Data",
                valor = rotulo,
                aoClicar = { aberto = true },
                erro = erro != null,
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
                DropdownMenuItem(text = { Text("Hoje") }, onClick = {
                    aberto = false
                    aoUsarHoje()
                })
                DropdownMenuItem(text = { Text("Ontem") }, onClick = {
                    aberto = false
                    aoUsarOntem()
                })
                DropdownMenuItem(
                    text = { Text("Escolher no calendário") },
                    leadingIcon = { Icon(IconesAgro.Calendario, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        aberto = false
                        aoAbrirCalendario()
                    }
                )
            }
        }
        if (erro != null) {
            Text(
                text = erro,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
internal fun CampoSitio(propriedades: List<Propriedade>, escolhidaId: String, aoEscolher: (String) -> Unit, modifier: Modifier = Modifier) {
    var aberto by remember { mutableStateOf(false) }
    val nome = propriedades.firstOrNull { it.id == escolhidaId }?.nome ?: TextosComuns.ESCOLHA_A_PROPRIEDADE
    Box(modifier = modifier) {
        CampoDeEscolha(
            icone = IconesAgro.Sitio,
            legenda = "Sítio",
            valor = nome,
            aoClicar = { aberto = true },
            habilitado = propriedades.size > 1,
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            propriedades.forEach { propriedade ->
                DropdownMenuItem(
                    text = { Text(propriedade.nome) },
                    trailingIcon = if (propriedade.id == escolhidaId) {
                        { Icon(IconesAgro.Confirmado, contentDescription = "Escolhido", modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    onClick = {
                        aoEscolher(propriedade.id)
                        aberto = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun CampoDescricao(texto: String, aoMudar: (String) -> Unit) {
    val foco = LocalFocusManager.current
    TextField(
        value = texto,
        onValueChange = aoMudar,
        singleLine = true,
        label = { Text("Descrição (opcional)") },
        leadingIcon = { Icon(IconesAgro.Lapis, contentDescription = null, modifier = Modifier.size(20.dp)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { foco.clearFocus() }),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** DatePicker com datas após [ultimaPermitida] desabilitadas. Datas em millis UTC. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarioDialogo(
    dataAtual: LocalDate?,
    hoje: LocalDate,
    ultimaPermitida: LocalDate,
    aoEscolher: (LocalDate) -> Unit,
    aoFechar: () -> Unit
) {
    val limite = ultimaPermitida.emMillisUtc()
    val estado = rememberDatePickerState(
        // Sem data válida no campo, abre no "hoje" do ViewModel (relógio injetado), não no do sistema.
        initialSelectedDateMillis = (dataAtual ?: hoje).emMillisUtc(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= limite
            override fun isSelectableYear(year: Int): Boolean = year <= ultimaPermitida.year
        }
    )

    DatePickerDialog(
        onDismissRequest = aoFechar,
        confirmButton = {
            TextButton(
                onClick = {
                    estado.selectedDateMillis?.let { aoEscolher(it.paraLocalDateUtc()) }
                    aoFechar()
                },
                enabled = estado.selectedDateMillis != null
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = aoFechar) { Text("Cancelar") }
        }
    ) {
        DatePicker(state = estado)
    }
}
