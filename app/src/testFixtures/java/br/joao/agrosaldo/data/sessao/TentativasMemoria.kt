package br.joao.agrosaldo.data.sessao

import br.joao.agrosaldo.domain.repository.EstadoTentativas
import br.joao.agrosaldo.domain.repository.TentativasRepository

/** Fake em memória para testes. Compartilhar a instância entre dois ContaService simula reinício do app. */
class TentativasMemoria : TentativasRepository {
    private val estados = mutableMapOf<String, EstadoTentativas>()

    /** Chaves com registro guardado. */
    val chaves: Set<String> get() = estados.keys.toSet()

    override fun ler(chave: String): EstadoTentativas = estados[chave] ?: EstadoTentativas()

    override fun gravar(chave: String, estado: EstadoTentativas) {
        if (estado == EstadoTentativas()) estados.remove(chave) else estados[chave] = estado
    }
}
