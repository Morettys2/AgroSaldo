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
 * OutlinedTextField com o shape e o tratamento de erro padrão do app.
 *
 * @param acaoNoFim conteúdo no fim do campo (ex.: botão que troca o sinal).
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
    umaLinha: Boolean = true,
    acaoNoFim: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        trailingIcon = acaoNoFim,
        isError = erro,
        supportingText = apoio?.let { { Text(it) } },
        singleLine = umaLinha,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = modifier.fillMaxWidth()
    )
}
