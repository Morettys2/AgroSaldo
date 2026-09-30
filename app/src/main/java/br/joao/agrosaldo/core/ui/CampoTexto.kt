package br.joao.agrosaldo.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * Campo de texto padrão do app.
 *
 * Existe para não repetir shape, cor e tratamento de erro em cada formulário —
 * e para garantir que todo campo do app tenha a mesma aparência sem ninguém
 * precisar lembrar dos parâmetros.
 */
@Composable
fun CampoTexto(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    apoio: String? = null,
    erro: Boolean = false,
    teclado: KeyboardType = KeyboardType.Text,
    umaLinha: Boolean = true
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        isError = erro,
        supportingText = apoio?.let { { Text(it) } },
        singleLine = umaLinha,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = modifier.fillMaxWidth()
    )
}
