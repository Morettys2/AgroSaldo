package br.joao.agrosaldo.domain.conta

/** Estado de cada requisito de senha, exibido como checklist no formulário. */
data class RequisitosSenha(
    val temTamanhoMinimo: Boolean,
    val temLetraENumero: Boolean,
    /** Não está em [SENHAS_COMUNS], não é sequência trivial nem contém nome, e-mail ou o nome do app. */
    val naoEhPrevisivel: Boolean
) {
    val todosCumpridos: Boolean get() = temTamanhoMinimo && temLetraENumero && naoEhPrevisivel
}

/**
 * Validação e normalização de dados de conta, compartilhadas entre os
 * formulários (feedback por campo) e o [ContaService] (validação final).
 */
object RegrasDeConta {

    const val SENHA_MINIMO = 8

    /** Comporta frases-senha e limita o custo do PBKDF2 com entradas enormes. */
    const val SENHA_MAXIMO = 128

    const val NOME_MAXIMO = 80

    /** Tamanho máximo de um endereço de e-mail (RFC 5321). */
    const val EMAIL_MAXIMO = 254

    /** Pedaço do nome ou do e-mail que, dentro da senha, a torna previsível. */
    private const val TAMANHO_MINIMO_DO_PEDACO = 4

    /** Sequência ("12345", "abcdef") a partir deste tamanho torna a senha previsível. */
    private const val TAMANHO_DE_SEQUENCIA_PREVISIVEL = 6

    // Validação mínima (x@y.z, sem espaços). Não há confirmação por e-mail.
    private val FORMATO_EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Forma canônica usada para busca e unicidade: trim + minúsculas. */
    fun normalizarEmail(email: String): String = email.trim().lowercase()

    fun emailValido(email: String): Boolean = normalizarEmail(email).let { it.length <= EMAIL_MAXIMO && FORMATO_EMAIL.matches(it) }

    fun nomeValido(nome: String): Boolean = nome.isNotBlank() && nome.trim().length <= NOME_MAXIMO

    /**
     * [email] e [nome] são usados para recusar senhas com termos do contexto
     * do usuário (NIST 800-63B).
     */
    fun requisitosDaSenha(senha: String, email: String = "", nome: String = ""): RequisitosSenha = RequisitosSenha(
        temTamanhoMinimo = senha.length >= SENHA_MINIMO,
        temLetraENumero = senha.any { it.isLetter() } && senha.any { it.isDigit() },
        naoEhPrevisivel = senha.isNotEmpty() && !ehPrevisivel(senha, email, nome)
    )

    fun senhaValida(senha: String, email: String = "", nome: String = ""): Boolean =
        requisitosDaSenha(senha, email, nome).todosCumpridos && senha.length <= SENHA_MAXIMO

    private fun ehPrevisivel(senha: String, email: String, nome: String): Boolean {
        val minuscula = senha.lowercase()
        // Também recusa variações de um caractere no início ou fim ("senha123!").
        if (minuscula in SENHAS_COMUNS || minuscula.dropLast(1) in SENHAS_COMUNS || minuscula.drop(1) in SENHAS_COMUNS) {
            return true
        }
        // Sequência numérica ou alfabética de 6+ caracteres ("12345678a", "abcdef12").
        if (maiorSequencia(minuscula) >= TAMANHO_DE_SEQUENCIA_PREVISIVEL) return true
        if ("agrosaldo" in minuscula) return true
        // Baixa variedade de caracteres ("aaaaaaa1").
        if (minuscula.toSet().size <= 2) return true

        // Parte local do e-mail e partes do nome com 4+ caracteres.
        val pedacos = buildList {
            add(normalizarEmail(email).substringBefore("@"))
            addAll(nome.lowercase().split(Regex("\\s+")))
        }.filter { it.length >= TAMANHO_MINIMO_DO_PEDACO }
        return pedacos.any { it in minuscula }
    }

    /** Maior sequência de caracteres consecutivos, crescente ou decrescente ("a12345b" -> 5). */
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

    /** Iniciais do primeiro e último nome ("Maria Souza" -> "MS"); "?" se vazio. */
    fun iniciais(nome: String): String {
        val partes = nome.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            partes.isEmpty() -> "?"
            partes.size == 1 -> partes[0].take(1).uppercase()
            else -> (partes.first().take(1) + partes.last().take(1)).uppercase()
        }
    }
}
