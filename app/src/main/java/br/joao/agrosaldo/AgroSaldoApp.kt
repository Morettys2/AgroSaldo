package br.joao.agrosaldo

import android.app.Application
import br.joao.agrosaldo.data.Repositorios

/**
 * Classe que representa o app inteiro. Nasce antes de qualquer tela e vive
 * enquanto o processo estiver de pé.
 *
 * É o lugar certo para preparar o que o app todo usa — no caso, o banco.
 * Fazer isso na MainActivity funcionaria, mas a Activity pode ser destruída
 * e recriada (ao girar o celular, por exemplo), e o banco não deve depender
 * do ciclo de vida de uma tela.
 *
 * Para o Android usar esta classe, o AndroidManifest precisa apontar para ela:
 *   <application android:name=".AgroSaldoApp" ...>
 */
class AgroSaldoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repositorios.inicializar(this)
    }
}
