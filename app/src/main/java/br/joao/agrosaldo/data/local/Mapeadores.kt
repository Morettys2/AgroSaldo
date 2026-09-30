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
 * Tradução entre o formato do banco e o formato do domínio.
 *
 * É a fronteira da camada de dados: acima daqui ninguém sabe que existe
 * epochDay, snake_case ou enum guardado como texto.
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
 * O dono não faz parte do modelo de domínio: quem decide é a sessão, no
 * repositório. [agora] é parâmetro para os testes conseguirem fixar o horário.
 */
fun Propriedade.paraEntidade(
    usuarioId: String?,
    agora: Long = System.currentTimeMillis()
): PropriedadeEntity =
    PropriedadeEntity(
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
 * Enum volta do texto com valueOf.
 *
 * Por isso NUNCA renomeie uma constante de TipoLancamento ou
 * CategoriaLancamento depois que existir dado gravado: o valueOf não acharia
 * o nome antigo e a leitura quebraria. Acrescentar constante nova é seguro.
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

fun Lancamento.paraEntidade(agora: Long = System.currentTimeMillis()): LancamentoEntity =
    LancamentoEntity(
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
