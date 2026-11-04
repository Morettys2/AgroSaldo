package br.joao.agrosaldo.feature.conta

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.joao.agrosaldo.core.seguranca.Biometria
import br.joao.agrosaldo.core.seguranca.acharFragmentActivity
import br.joao.agrosaldo.core.ui.BotaoSecundario
import br.joao.agrosaldo.core.ui.CampoSenha
import br.joao.agrosaldo.core.ui.EstadoErro
import br.joao.agrosaldo.domain.conta.RegrasDeConta
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.PreviasDaTela
import br.joao.agrosaldo.ui.theme.agro

/**
 * Rota da tela de conta. Sair e excluir não navegam: encerram a sessão e a
 * AppNavegacao redireciona ao login. Por isso só recebe destinos que mantêm a sessão.
 */
@Composable
fun ContaRoute(
    aoAbrirPrivacidade: () -> Unit,
    aoAlterarSenha: () -> Unit,
    aoAbrirAvisoChuva: () -> Unit,
    aoAbrirPropriedades: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val aoMudarBloqueio: (Boolean) -> Unit = { ativar ->
        val activity = context.acharFragmentActivity()
        if (activity == null || !Biometria.disponivel(context)) {
            Toast.makeText(
                context,
                "Cadastre uma digital ou um PIN nas configurações do celular primeiro.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Biometria.confirmarMudancaDoBloqueio(activity, ativar) { viewModel.aoDefinirBloqueio(ativar) }
        }
    }

    ContaScreen(
        uiState = uiState,
        aoMudarBloqueio = aoMudarBloqueio,
        aoAbrirPrivacidade = aoAbrirPrivacidade,
        aoAlterarSenha = aoAlterarSenha,
        aoAbrirAvisoChuva = aoAbrirAvisoChuva,
        aoAbrirPropriedades = aoAbrirPropriedades,
        aoSair = viewModel::aoSair,
        aoExcluirConta = viewModel::aoExcluirConta,
        aoCancelarExclusao = viewModel::aoCancelarExclusao,
        modifier = modifier
    )
}

@Composable
fun ContaScreen(
    uiState: ContaUiState,
    aoMudarBloqueio: (ativar: Boolean) -> Unit,
    aoAbrirPrivacidade: () -> Unit,
    aoAlterarSenha: () -> Unit,
    aoAbrirAvisoChuva: () -> Unit,
    aoAbrirPropriedades: () -> Unit,
    aoSair: () -> Unit,
    aoExcluirConta: (senha: String) -> Unit,
    aoCancelarExclusao: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Saveable para o diálogo sobreviver a mudança de configuração.
    var confirmandoExclusao by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Espacos.margemDaTela)
            .padding(top = Espacos.pequeno, bottom = Espacos.enorme)
    ) {
        Cabecalho(
            iniciais = uiState.iniciais,
            nome = uiState.nome,
            email = uiState.email
        )

        // Falha ao ler a conta: a leitura recomeça sozinha e a faixa some quando ela volta.
        uiState.erro?.let { erro ->
            EstadoErro(erro, Modifier.padding(bottom = Espacos.medio))
        }

        ItemConta(
            icone = Icons.Outlined.NotificationsActive,
            titulo = "Aviso de chuva",
            apoio = "Limites e notificação",
            aoClicar = aoAbrirAvisoChuva
        )
        ItemConta(
            icone = Icons.Outlined.Shield,
            titulo = "Privacidade e dados",
            apoio = if (uiState.consentimentoEm.isBlank()) {
                "O que o app guarda"
            } else {
                "Termo aceito em ${uiState.consentimentoEm}"
            },
            aoClicar = aoAbrirPrivacidade
        )
        ItemBloqueio(
            ativo = uiState.bloqueioAtivo,
            aoMudar = aoMudarBloqueio
        )
        ItemConta(
            icone = Icons.Outlined.Lock,
            titulo = "Alterar senha",
            apoio = "Pede a senha atual",
            aoClicar = aoAlterarSenha
        )
        ItemConta(
            icone = Icons.Outlined.Grass,
            titulo = "Propriedades",
            apoio = uiState.resumoPropriedades,
            aoClicar = aoAbrirPropriedades
        )

        Spacer(Modifier.height(Espacos.enorme))

        BotaoSecundario(
            texto = "Sair da conta",
            aoClicar = aoSair,
            icone = Icons.AutoMirrored.Outlined.Logout,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Espacos.medio))

        // Cor de despesa, não de erro: exclusão é ação legítima, não falha.
        BotaoSecundario(
            texto = "Excluir conta e todos os dados",
            aoClicar = { confirmandoExclusao = true },
            icone = Icons.Outlined.DeleteForever,
            perigo = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "A exclusão é permanente e apaga todas as propriedades e lançamentos desta conta.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (confirmandoExclusao) {
        DialogoExclusao(
            excluindo = uiState.excluindo,
            erro = uiState.erroExclusao,
            aoConfirmar = aoExcluirConta,
            aoCancelar = {
                confirmandoExclusao = false
                aoCancelarExclusao()
            }
        )
    }
}

@Composable
private fun Cabecalho(iniciais: String, nome: String, email: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Espacos.pequeno, bottom = Espacos.grande)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = iniciais,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Text(text = nome, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ItemConta(icone: ImageVector, titulo: String, apoio: String, aoClicar: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacos.medio),
            modifier = Modifier
                .fillMaxWidth()
                // role: o leitor de tela anuncia "botão", e não só o texto.
                .clickable(role = Role.Button, onClick = aoClicar)
                .padding(vertical = Espacos.padrao)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text = titulo, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = apoio,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.agro.tintaApagada
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.agro.tintaApagada
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Item no mesmo layout de [ItemConta], com Switch no lugar da seta. */
@Composable
private fun ItemBloqueio(ativo: Boolean, aoMudar: (Boolean) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacos.medio),
            modifier = Modifier
                .fillMaxWidth()
                // toggleable na linha inteira: área de toque maior e TalkBack anuncia como Switch.
                .toggleable(value = ativo, role = Role.Switch, onValueChange = aoMudar)
                .padding(vertical = Espacos.medio)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text = "Bloquear com digital ou PIN", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (ativo) "Pede ao abrir e após 2 min fora" else "Desligado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.agro.tintaApagada
                )
            }
            // null: o toque é tratado pelo toggleable da linha.
            Switch(checked = ativo, onCheckedChange = null)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Confirmação de exclusão; exige a senha para evitar exclusão por terceiros. */
@Composable
private fun DialogoExclusao(excluindo: Boolean, erro: String?, aoConfirmar: (String) -> Unit, aoCancelar: () -> Unit) {
    var senha by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!excluindo) aoCancelar() },
        icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.agro.despesa) },
        title = { Text("Excluir a conta?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Espacos.medio)) {
                Text(
                    "A conta, as propriedades e todos os lançamentos serão apagados deste " +
                        "celular. Não dá para desfazer."
                )
                CampoSenha(
                    valor = senha,
                    aoMudar = { senha = it.take(RegrasDeConta.SENHA_MAXIMO) },
                    rotulo = "Sua senha",
                    erro = erro != null,
                    apoio = erro,
                    ultimoCampo = true,
                    aoConfirmar = { aoConfirmar(senha) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { aoConfirmar(senha) }, enabled = !excluindo) {
                if (excluindo) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("Excluir tudo", color = MaterialTheme.agro.despesa)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = aoCancelar, enabled = !excluindo) { Text("Cancelar") }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}

@PreviasDaTela
@Composable
private fun ContaPreview() {
    AgroSaldoTheme {
        ContaScreen(
            uiState = ContaUiState(
                carregando = false,
                nome = "Maria Souza",
                email = "maria@exemplo.com",
                iniciais = "MS",
                consentimentoEm = "30/09/2026 às 19:40",
                versaoTermo = 1,
                quantidadePropriedades = 2,
                bloqueioAtivo = true
            ),
            aoMudarBloqueio = {},
            aoAbrirPrivacidade = {}, aoAlterarSenha = {}, aoAbrirAvisoChuva = {}, aoAbrirPropriedades = {}, aoSair = {},
            aoExcluirConta = {}, aoCancelarExclusao = {}
        )
    }
}
