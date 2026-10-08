package br.joao.agrosaldo.feature.conta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.core.seguranca.Biometria
import br.joao.agrosaldo.core.seguranca.acharFragmentActivity
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme

/**
 * Tela de bloqueio do app, exibida no lugar do conteúdo. Abre o prompt biométrico ao
 * entrar em composição; "Desbloquear" reabre se cancelado. [aoUsarSenha] é a saída de
 * emergência (sem biometria/PIN): encerra a sessão e exige login com senha, sem expor dados.
 */
@Composable
fun TelaBloqueioRoute(
    aoDesbloquear: () -> Unit,
    aoUsarSenha: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mensagem by remember { mutableStateOf<String?>(null) }
    val aoDesbloquearAtual by rememberUpdatedState(aoDesbloquear)

    val pedirDigital = {
        val activity = context.acharFragmentActivity()
        if (activity == null || !Biometria.disponivel(context)) {
            // Credenciais do aparelho removidas depois que o bloqueio foi ligado.
            mensagem = "Este celular não tem mais digital nem PIN cadastrados. Entre com seu e-mail e senha."
        } else {
            mensagem = null
            Biometria.autenticar(
                activity = activity,
                titulo = "Desbloquear o AgroSaldo",
                subtitulo = "Use a digital ou o PIN do celular",
                aoSucesso = { aoDesbloquearAtual() },
                aoFalhar = { erro -> mensagem = erro }
            )
        }
    }

    LaunchedEffect(Unit) { pedirDigital() }

    // Botão voltar com o app bloqueado é tratado na AppNavegacao.

    TelaBloqueioScreen(
        mensagem = mensagem,
        aoDesbloquear = pedirDigital,
        aoUsarSenha = aoUsarSenha,
        modifier = modifier
    )
}

@Composable
fun TelaBloqueioScreen(
    mensagem: String?,
    aoDesbloquear: () -> Unit,
    aoUsarSenha: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(88.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "AgroSaldo bloqueado",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Use a digital ou o PIN do celular para ver suas contas.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            mensagem?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = aoDesbloquear,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(text = "Desbloquear", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = aoUsarSenha, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Entrar com e-mail e senha",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TelaBloqueioPreview() {
    AgroSaldoTheme {
        TelaBloqueioScreen(mensagem = null, aoDesbloquear = {}, aoUsarSenha = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun TelaBloqueioErroPreview() {
    AgroSaldoTheme {
        TelaBloqueioScreen(
            mensagem = "Muitas tentativas. Tente de novo mais tarde.",
            aoDesbloquear = {},
            aoUsarSenha = {}
        )
    }
}
