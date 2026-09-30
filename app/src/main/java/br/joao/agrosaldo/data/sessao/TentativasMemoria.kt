package br.joao.agrosaldo.data.sessao

import br.joao.agrosaldo.domain.repository.EstadoTentativas
import br.joao.agrosaldo.domain.repository.TentativasRepository

/** Usada nos testes. Duas instâncias do ContaService com a mesma TentativasMemoria simulam fechar e abrir o app. */
class TentativasMemoria : TentativasRepository {
    private var estado = EstadoTentativas()
    override fun ler(): EstadoTentativas = estado
    override fun gravar(estado: EstadoTentativas) {
        this.estado = estado
    }
}
