package br.joao.agrosaldo.di

import javax.inject.Qualifier

/**
 * Escopo de corrotinas que vive enquanto o processo viver (não é cancelado por tela nenhuma).
 * Existe um `CoroutineScope` só; o qualificador evita que um escopo qualquer seja injetado
 * por engano no lugar dele.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EscopoDoApp
