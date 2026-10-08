package br.joao.agrosaldo.feature.conta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.domain.model.TermoDePrivacidade
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme

private data class Secao(val titulo: String, val texto: String)

/**
 * Texto da política. Qualquer alteração exige incrementar TermoDePrivacidade.VERSAO_ATUAL,
 * que dispara o reaceite (NovoConsentimentoScreen) e registra qual versão cada usuário aceitou.
 */
private val SECOES = listOf(
    Secao(
        "Quem somos",
        "O AgroSaldo é um projeto acadêmico do curso de Engenharia de Software da UNIFAE, " +
            "feito para ajudar pequenos produtores rurais a controlar as contas de cada propriedade."
    ),
    Secao(
        "O que o app guarda",
        "Da conta: nome, e-mail e a senha protegida por hash — nem o app consegue ler a senha " +
            "original. Das propriedades: nome, cultura, área e coordenadas. E os lançamentos de " +
            "receitas e despesas que você cadastrar."
    ),
    Secao(
        "Onde fica",
        "Tudo fica num banco de dados privado dentro deste celular. Outros apps não têm acesso, " +
            "e nada é enviado para servidores do AgroSaldo. Se o backup do Android estiver ligado, o " +
            "próprio Android pode guardar uma cópia dos dados na nuvem da sua conta (como a do Google) " +
            "e levá-los para um celular novo. O app só permite a cópia na nuvem quando ela é " +
            "criptografada de ponta a ponta, o que exige bloqueio de tela no celular."
    ),
    Secao(
        "O que sai do celular",
        "Para mostrar a previsão do tempo, o app envia somente a latitude e a longitude da " +
            "propriedade para o serviço Open-Meteo. Nenhum nome, e-mail ou valor financeiro é enviado."
    ),
    Secao(
        "Localização do celular",
        "O app só usa o GPS quando você toca em \"Usar minha localização\" no cadastro de uma " +
            "propriedade, e só para preencher a latitude e a longitude dela. A localização não é " +
            "acompanhada, não roda em segundo plano e não é guardada além das coordenadas que você salvar."
    ),
    Secao(
        "Por que tratamos esses dados",
        "Com base no seu consentimento (art. 7º, I, da LGPD), dado ao criar a conta, e só para " +
            "as funções do app: controlar receitas e despesas e avisar sobre chuva."
    ),
    Secao(
        "Seus direitos",
        "Você pode ver seus dados a qualquer momento nas telas do app, corrigi-los editando cada " +
            "cadastro, e apagar tudo em Conta › Excluir conta e todos os dados. A exclusão apaga " +
            "os dados deste celular na hora e é definitiva (art. 18 da LGPD)."
    ),
    Secao(
        "Por quanto tempo",
        "Enquanto a conta existir. Ao excluir a conta, os dados saem deste celular na hora, e a " +
            "cópia do backup do Android é substituída no próximo backup automático. Ao desinstalar o " +
            "app, os dados saem do celular, mas a cópia do backup pode continuar guardada pela sua " +
            "conta por um tempo e voltar se você instalar o app de novo."
    )
)

/**
 * Política de privacidade completa. Stateless, pois é aberta tanto antes de
 * existir conta (consentimento) quanto a partir da tela de Conta.
 */
@Composable
fun PoliticaScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SECOES.forEach { secao ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = secao.titulo, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = secao.texto,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "Versão ${TermoDePrivacidade.VERSAO_ATUAL} do termo",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun PoliticaPreview() {
    AgroSaldoTheme { PoliticaScreen() }
}
