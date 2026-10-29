package br.joao.agrosaldo.data.local

import br.joao.agrosaldo.data.local.entidade.LancamentoEntity
import br.joao.agrosaldo.data.local.entidade.PropriedadeEntity
import br.joao.agrosaldo.data.local.entidade.UsuarioEntity
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.LancamentoExcluido
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.model.Usuario
import java.time.Instant
import java.time.LocalDate

/*
 * Conversões entre entidades do Room e modelos do domínio. Formatos gravados: datas em epochDay,
 * instantes em epoch ms e enums pelo nome da constante.
 */

/**
 * Converte [PropriedadeEntity] (Room) em [Propriedade] (domínio).
 *
 * Os limites de nome, cultura e área entraram no domínio em 18/10/2026, e um registro gravado antes
 * pode passar deles. Ele é lido ajustado ao limite: uma linha antiga não pode esconder a lista inteira
 * (o `require` do modelo continua barrando as gravações novas).
 */
fun PropriedadeEntity.paraDominio(): Propriedade = Propriedade(
    id = id,
    nome = nome.take(Propriedade.NOME_MAXIMO),
    cultura = cultura.take(Propriedade.CULTURA_MAXIMA),
    // O teto também cobre o infinito, que as versões antigas aceitavam.
    areaHectares = areaHectares.coerceAtMost(Propriedade.AREA_MAXIMA_HECTARES),
    latitude = latitude,
    longitude = longitude
)

/**
 * Converte [Propriedade] em [PropriedadeEntity].
 *
 * @param usuarioId dono, vindo da sessão (não faz parte do modelo de domínio).
 * @param agora valor de `atualizado_em`, em epoch ms.
 */
fun Propriedade.paraEntidade(usuarioId: String?, agora: Long): PropriedadeEntity = PropriedadeEntity(
    id = id,
    nome = nome,
    cultura = cultura,
    areaHectares = areaHectares,
    latitude = latitude,
    longitude = longitude,
    atualizadoEm = agora,
    usuarioId = usuarioId
)

/**
 * Converte [LancamentoEntity] em [Lancamento]. Os enums são lidos com `valueOf`: renomear ou
 * remover constantes de [TipoLancamento]/[CategoriaLancamento] quebra a leitura de dados gravados;
 * adicionar constantes é seguro. A descrição de um registro gravado antes do limite (18/10/2026) é
 * lida cortada, pelo mesmo motivo de [PropriedadeEntity.paraDominio].
 */
fun LancamentoEntity.paraDominio(): Lancamento = Lancamento(
    id = id,
    propriedadeId = propriedadeId,
    tipo = TipoLancamento.valueOf(tipo),
    categoria = CategoriaLancamento.valueOf(categoria),
    valorCentavos = valorCentavos,
    data = LocalDate.ofEpochDay(data),
    descricao = descricao.take(Lancamento.DESCRICAO_MAXIMA)
)

/** O lançamento lido junto com o `atualizado_em`, para o "Desfazer" regravar o mesmo instante. */
fun LancamentoEntity.paraExcluido(): LancamentoExcluido = LancamentoExcluido(paraDominio(), Instant.ofEpochMilli(atualizadoEm))

/** Converte [Lancamento] em [LancamentoEntity]; [agora] vira `atualizado_em` (epoch ms). */
fun Lancamento.paraEntidade(agora: Long): LancamentoEntity = LancamentoEntity(
    id = id,
    propriedadeId = propriedadeId,
    tipo = tipo.name,
    categoria = categoria.name,
    valorCentavos = valorCentavos,
    data = data.toEpochDay(),
    descricao = descricao,
    atualizadoEm = agora
)

/**
 * Converte [UsuarioEntity] em [Usuario] (epoch ms vira `Instant`). Nome e e-mail gravados antes do
 * limite no modelo são lidos cortados, pelo mesmo motivo de [PropriedadeEntity.paraDominio].
 */
fun UsuarioEntity.paraDominio(): Usuario = Usuario(
    id = id,
    nome = nome.take(Usuario.NOME_MAXIMO),
    email = email.take(Usuario.EMAIL_MAXIMO),
    senhaHash = senhaHash,
    consentimentoEm = Instant.ofEpochMilli(consentimentoEm),
    versaoTermo = versaoTermo,
    criadoEm = Instant.ofEpochMilli(criadoEm)
)

/** Converte [Usuario] em [UsuarioEntity] (`Instant` vira epoch ms). */
fun Usuario.paraEntidade(): UsuarioEntity = UsuarioEntity(
    id = id,
    nome = nome,
    email = email,
    senhaHash = senhaHash,
    consentimentoEm = consentimentoEm.toEpochMilli(),
    versaoTermo = versaoTermo,
    criadoEm = criadoEm.toEpochMilli()
)
