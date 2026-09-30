package br.joao.agrosaldo.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.domain.conta.RequisitosSenha

/**
 * Os requisitos da senha à vista enquanto a pessoa digita; cada um ganha ✓
 * ao ser cumprido. Usado no cadastro e na troca de senha — por isso subiu
 * para core/ui quando apareceu na segunda tela.
 */
@Composable
fun CartaoRequisitosSenha(requisitos: RequisitosSenha, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LinhaRequisito("Pelo menos ${RegrasDeConta.SENHA_MINIMO} caracteres", requisitos.temTamanhoMinimo)
            LinhaRequisito("Uma letra e um número", requisitos.temLetraENumero)
            LinhaRequisito("Difícil de adivinhar (sem seu nome ou senhas comuns)", requisitos.naoEhPrevisivel)
        }
    }
}

@Composable
private fun LinhaRequisito(texto: String, cumprido: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (cumprido) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            // O texto já diz o requisito; o ícone só informa se foi cumprido.
            contentDescription = if (cumprido) "Cumprido" else "Ainda não cumprido",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
