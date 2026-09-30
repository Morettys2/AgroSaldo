package br.joao.agrosaldo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Troca a thread principal do Android por um dispatcher de teste.
 *
 * Por que é necessário: o viewModelScope roda na thread principal
 * (Dispatchers.Main). Num teste rodando no computador essa thread não existe,
 * e todo ViewModel quebraria ao lançar uma coroutine.
 *
 * O UnconfinedTestDispatcher executa as coroutines na hora, então quando uma
 * função do ViewModel retorna, o estado já está atualizado para o teste ler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
