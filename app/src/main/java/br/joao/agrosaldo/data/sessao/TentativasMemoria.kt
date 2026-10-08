package br.joao.agrosaldo.data.sessao

import br.joao.agrosaldo.domain.repository.EstadoTentativas
import br.joao.agrosaldo.domain.repository.TentativasRepository

/** Fake em memória para testes. Compartilhar a instância entre dois ContaService simula reinício do app. */
class TentativasMemoria : TentativasRepository {
    private var estado = EstadoTentativas()
    override fun ler(): EstadoTentativas = estado
    override fun gravar(estado: EstadoTentativas) {
        this.estado = estado
    }
}
