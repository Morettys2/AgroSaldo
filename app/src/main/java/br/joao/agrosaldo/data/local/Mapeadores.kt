package br.joao.agrosaldo.data.local

import br.joao.agrosaldo.data.local.entidade.LancamentoEntity
import br.joao.agrosaldo.data.local.entidade.PropriedadeEntity
import br.joao.agrosaldo.data.local.entidade.UsuarioEntity
import br.joao.agrosaldo.domain.model.CategoriaLancamento
import br.joao.agrosaldo.domain.model.Lancamento
import br.joao.agrosaldo.domain.model.Propriedade
import br.joao.agrosaldo.domain.model.TipoLancamento
import br.joao.agrosaldo.domain.model.Usuario
import java.time.Instant
import java.time.LocalDate

/*
 * Conversões entidade <-> domínio. Formatos persistidos: datas em epochDay,
 * instantes em epoch ms, enums pelo nome da constante.
 */

fun PropriedadeEntity.paraDominio(): Propriedade = Propriedade(
    id = id,
    nome = nome,
    cultura = cultura,
    areaHectares = areaHectares,
    latitude = latitude,
    longitude = longitude
)

/**
 * @param usuarioId dono, definido pelo repositório a partir da sessão (não faz
 *   parte do modelo de domínio).
 * @param agora valor de `atualizado_em`, em epoch ms.
 */
fun Propriedade.paraEntidade(usuarioId: String?, agora: Long = System.currentTimeMillis()): PropriedadeEntity = PropriedadeEntity(
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
 * Enums são lidos com valueOf: renomear ou remover constantes de
 * [TipoLancamento]/[CategoriaLancamento] quebra a leitura de dados gravados.
 * Adicionar constantes é seguro.
 */
fun LancamentoEntity.paraDominio(): Lancamento = Lancamento(
    id = id,
    propriedadeId = propriedadeId,
    tipo = TipoLancamento.valueOf(tipo),
    categoria = CategoriaLancamento.valueOf(categoria),
    valorCentavos = valorCentavos,
    data = LocalDate.ofEpochDay(data),
    descricao = descricao
)

fun Lancamento.paraEntidade(agora: Long = System.currentTimeMillis()): LancamentoEntity = LancamentoEntity(
    id = id,
    propriedadeId = propriedadeId,
    tipo = tipo.name,
    categoria = categoria.name,
    valorCentavos = valorCentavos,
    data = data.toEpochDay(),
    descricao = descricao,
    atualizadoEm = agora
)

fun UsuarioEntity.paraDominio(): Usuario = Usuario(
    id = id,
    nome = nome,
    email = email,
    senhaHash = senhaHash,
    consentimentoEm = Instant.ofEpochMilli(consentimentoEm),
    versaoTermo = versaoTermo,
    criadoEm = Instant.ofEpochMilli(criadoEm)
)

fun Usuario.paraEntidade(): UsuarioEntity = UsuarioEntity(
    id = id,
    nome = nome,
    email = email,
    senhaHash = senhaHash,
    consentimentoEm = consentimentoEm.toEpochMilli(),
    versaoTermo = versaoTermo,
    criadoEm = criadoEm.toEpochMilli()
)
