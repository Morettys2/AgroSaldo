package br.joao.agrosaldo.domain.conta

/**
 * Senhas que aparecem no topo das listas de vazamentos no Brasil.
 *
 * A NIST (SP 800-63B) recomenda recusar senhas conhecidas por vazamentos em
 * vez de só exigir "letra e número": "senha123" cumpre qualquer regra de
 * composição e é a primeira que um atacante tenta.
 *
 * A lista é curta de propósito — cabe no app sem pesar e cobre o que mais
 * se tenta. Quando houver a API, a checagem pode consultar uma base
 * completa, como a do Have I Been Pwned.
 */
internal val SENHAS_COMUNS: Set<String> = setOf(
    "12345678", "123456789", "1234567890", "12341234", "11223344", "87654321",
    "10203040", "102030405", "01020304", "11111111", "00000000", "12121212",
    "senha123", "senha1234", "senha12345", "minhasenha", "minhasenha1", "senhasenha",
    "password", "password1", "password123", "passw0rd", "p4ssw0rd",
    "abc12345", "abcd1234", "a1b2c3d4", "1234abcd", "abc123456", "qwerty123",
    "qwerty12", "1q2w3e4r", "1q2w3e4r5t", "q1w2e3r4", "q1w2e3r4t5", "zaq12wsx",
    "mudar123", "mudar1234", "trocar123", "admin123", "admin1234", "teste123",
    "teste1234", "acesso123", "entrar123", "usuario123", "brasil123", "brasil2026",
    "flamengo1", "flamengo123", "corinthians1", "palmeiras1", "saopaulo1", "santos123",
    "gremio123", "vasco123", "cruzeiro1", "iloveyou1", "teamo123", "amor1234",
    "jesus123", "deus1234", "deusefiel", "familia123", "mamae123", "papai123",
    "fazenda123", "sitio123", "agro1234", "roca1234", "milho123", "soja1234",
    "cafe1234", "gado1234", "celular123", "android123", "google123"
)
