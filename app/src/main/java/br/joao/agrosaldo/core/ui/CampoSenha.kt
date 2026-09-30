package br.joao.agrosaldo.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Campo de senha com o "olhinho" para mostrar o que foi digitado.
 *
 * Mostrar ou esconder é estado puramente visual, então fica aqui dentro com
 * rememberSaveable (sobrevive a girar o celular) e não sobe para o ViewModel.
 *
 * [aoConfirmar] é chamado pela tecla "OK" do teclado — no login, ela já entra
 * sem a pessoa precisar procurar o botão.
 */
@Composable
fun CampoSenha(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    apoio: String? = null,
    erro: Boolean = false,
    ultimoCampo: Boolean = false,
    aoConfirmar: () -> Unit = {}
) {
    var visivel by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        isError = erro,
        supportingText = apoio?.let { { Text(it) } },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        visualTransformation = if (visivel) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (ultimoCampo) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onDone = { aoConfirmar() }),
        trailingIcon = {
            IconButton(onClick = { visivel = !visivel }) {
                Icon(
                    imageVector = if (visivel) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visivel) "Esconder senha" else "Mostrar senha"
                )
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}
