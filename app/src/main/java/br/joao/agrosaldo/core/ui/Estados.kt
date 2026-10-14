package br.joao.agrosaldo.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.joao.agrosaldo.ui.theme.AgroSaldoTheme
import br.joao.agrosaldo.ui.theme.Espacos
import br.joao.agrosaldo.ui.theme.IconesAgro

/*
 * Estados de tela compartilhados: carregando, vazio e erro. Antes cada tela tinha o seu
 * (o `Aviso` estava copiado em duas telas e o `EstadoVazio` era privado do Clima).
 */

/** Indicador de carregamento centralizado, anunciado pelo leitor de tela. */
@Composable
fun Carregando(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(Espacos.alvoDeToque)
            .semantics { contentDescription = "Carregando" }
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Tela ou trecho sem conteúdo, explicando o que fazer. [icone] e [titulo] são opcionais:
 * sem eles, vira um aviso curto num cartão (ex.: "Nenhum lançamento neste mês").
 *
 * @param destaque usa o [BotaoDestaque] (a próxima ação óbvia, como cadastrar a primeira
 *   propriedade); senão, um botão de texto discreto (ex.: "Limpar filtros").
 */
@Composable
fun EstadoVazio(
    texto: String,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    corDoIcone: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    fundoDoIcone: Color = MaterialTheme.colorScheme.primaryContainer,
    titulo: String? = null,
    acao: String? = null,
    destaque: Boolean = false,
    aoClicarAcao: () -> Unit = {}
) {
    CartaoSafra(modifier = modifier, preenchimento = if (icone != null) 28.dp else Espacos.largo) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espacos.medio)
        ) {
            if (icone != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(fundoDoIcone)
                ) {
                    Icon(icone, contentDescription = null, tint = corDoIcone, modifier = Modifier.size(36.dp))
                }
            }
            if (titulo != null) {
                Text(titulo, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }
            Text(
                text = texto,
                style = if (titulo != null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (acao != null) {
                if (destaque) {
                    BotaoDestaque(texto = acao, aoClicar = aoClicarAcao, tamanho = TamanhoBotao.COMPACTO)
                } else {
                    TextButton(onClick = aoClicarAcao) { Text(acao, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

/**
 * Faixa de aviso no topo do conteúdo (ex.: "Sem internet. Mostrando a última previsão
 * salva."). Com [erro], usa a cor de erro. É uma liveRegion: o leitor de tela anuncia o
 * texto quando ele aparece ou muda, sem a pessoa precisar achar a faixa.
 */
@Composable
fun FaixaDeAviso(
    texto: String,
    modifier: Modifier = Modifier,
    icone: ImageVector = IconesAgro.Nuvem,
    erro: Boolean = false
) {
    val cor = if (erro) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    CartaoSafra(modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }, preenchimento = Espacos.padrao) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espacos.medio)
        ) {
            Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(22.dp))
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = cor)
        }
    }
}

/** Erro ao ler os dados; some sozinho quando a leitura volta (veja `repetirAposErro`). */
@Composable
fun EstadoErro(mensagem: String, modifier: Modifier = Modifier) {
    FaixaDeAviso(texto = mensagem, icone = IconesAgro.Atualizar, erro = true, modifier = modifier)
}

@Preview(name = "Estados", showBackground = true)
@Preview(name = "Estados · fonte 2x", showBackground = true, fontScale = 2f)
@Composable
private fun EstadosPreview() {
    AgroSaldoTheme {
        Column(
            modifier = Modifier.padding(Espacos.largo),
            verticalArrangement = Arrangement.spacedBy(Espacos.medio)
        ) {
            EstadoErro("Não foi possível carregar os lançamentos. Tentando de novo…")
            FaixaDeAviso("Sem internet. Mostrando a última previsão salva.")
            EstadoVazio(
                texto = "Cadastre um sítio com as coordenadas para ver a chuva prevista.",
                titulo = "Previsão por propriedade",
                icone = IconesAgro.Gota,
                acao = "Cadastrar propriedade",
                destaque = true
            )
            EstadoVazio(texto = "Nenhum lançamento com esse filtro neste mês.", acao = "Limpar filtros")
            Carregando()
        }
    }
}
