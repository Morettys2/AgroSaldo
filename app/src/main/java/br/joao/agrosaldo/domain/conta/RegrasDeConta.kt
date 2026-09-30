package br.joao.agrosaldo.domain.conta

/** Quais requisitos de senha já foram cumpridos. A tela marca cada um com um ✓. */
data class RequisitosSenha(
    val temTamanhoMinimo: Boolean,
    val temLetraENumero: Boolean,
    /** Não é uma senha de vazamento, nem repete o nome, o e-mail ou o nome do app. */
    val naoEhPrevisivel: Boolean
) {
    val todosCumpridos: Boolean get() = temTamanhoMinimo && temLetraENumero && naoEhPrevisivel
}

/**
 * Regras de conta que valem para o app inteiro.
 *
 * Moram no domínio, e não no ViewModel, porque dois lugares precisam delas:
 * o formulário (para mostrar o erro no campo) e o [ContaService] (para
 * recusar dado inválido mesmo que a tela deixe passar).
 */
object RegrasDeConta {

    const val SENHA_MINIMO = 8

    /** Limite alto o bastante para frase-senha e baixo o bastante para não travar o PBKDF2. */
    const val SENHA_MAXIMO = 128

    const val NOME_MAXIMO = 80

    // Simples de propósito: algo@algo.algo, sem espaço. Validar e-mail "de
    // verdade" por regex é um poço sem fundo — a prova final seria mandar um
    // e-mail, o que só faz sentido quando a API existir.
    private val FORMATO_EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** "  Fabio@Exemplo.COM " e "fabio@exemplo.com" são a mesma conta. */
    fun normalizarEmail(email: String): String = email.trim().lowercase()

    fun emailValido(email: String): Boolean =
        FORMATO_EMAIL.matches(normalizarEmail(email))

    fun nomeValido(nome: String): Boolean =
        nome.isNotBlank() && nome.trim().length <= NOME_MAXIMO

    /**
     * [email] e [nome] entram porque uma senha que contém o próprio nome ou
     * o e-mail é a segunda coisa que alguém tenta (NIST 800-63B pede para
     * recusar "palavras do contexto": usuário, nome do serviço).
     */
    fun requisitosDaSenha(senha: String, email: String = "", nome: String = ""): RequisitosSenha =
        RequisitosSenha(
            temTamanhoMinimo = senha.length >= SENHA_MINIMO,
            temLetraENumero = senha.any { it.isLetter() } && senha.any { it.isDigit() },
            naoEhPrevisivel = senha.isNotEmpty() && !ehPrevisivel(senha, email, nome)
        )

    fun senhaValida(senha: String, email: String = "", nome: String = ""): Boolean =
        requisitosDaSenha(senha, email, nome).todosCumpridos && senha.length <= SENHA_MAXIMO

    private fun ehPrevisivel(senha: String, email: String, nome: String): Boolean {
        val minuscula = senha.lowercase()
        // "senha1234" e "senha123!" são variações óbvias de uma senha da lista.
        if (minuscula in SENHAS_COMUNS || minuscula.dropLast(1) in SENHAS_COMUNS || minuscula.drop(1) in SENHAS_COMUNS) {
            return true
        }
        // "12345678a", "abcdef12": sequências de teclado ou do alfabeto.
        if (maiorSequencia(minuscula) >= 6) return true
        if ("agrosaldo" in minuscula) return true
        // "aaaaaaa1", "11111111a": quase nenhuma variedade de caracteres.
        if (minuscula.toSet().size <= 2) return true

        // Pedaços da identidade da pessoa com 4 letras ou mais.
        val pedacos = buildList {
            add(normalizarEmail(email).substringBefore("@"))
            addAll(nome.lowercase().split(Regex("\\s+")))
        }.filter { it.length >= 4 }
        return pedacos.any { it in minuscula }
    }

    /** Tamanho da maior sequência seguida, subindo ou descendo: "a12345b" → 5. */
    private fun maiorSequencia(texto: String): Int {
        var maior = 1
        var subindo = 1
        var descendo = 1
        for (i in 1 until texto.length) {
            val diferenca = texto[i] - texto[i - 1]
            subindo = if (diferenca == 1) subindo + 1 else 1
            descendo = if (diferenca == -1) descendo + 1 else 1
            maior = maxOf(maior, subindo, descendo)
        }
        return maior
    }

    /** "Fábio Moretti" → "FM". Usado no avatar da tela de conta. */
    fun iniciais(nome: String): String {
        val partes = nome.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            partes.isEmpty() -> "?"
            partes.size == 1 -> partes[0].take(1).uppercase()
            else -> (partes.first().take(1) + partes.last().take(1)).uppercase()
        }
    }
}
