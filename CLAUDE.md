# CLAUDE.md — AgroSaldo

Contexto permanente do projeto. Leia inteiro antes de qualquer alteração.

---

## 1. O projeto

App Android nativo para o pequeno produtor rural controlar **receitas e despesas por propriedade** e receber **aviso de chuva** para decidir a irrigação do pivô.

- Projeto acadêmico (UCE — Engenharia de Software, UNIFAE), com usuário real: um produtor de Itobi/SP, que já usa o APK.
- A professora cobra **MVVM** e **persistência local com Room**. Há um protótipo no Figma que precisa bater com o app.
- Meta: publicar na **Play Store**. Por isso: padrão profissional, segurança e zero crash.
- Versão atual: `versionCode 6` / `versionName 1.5`. Histórico das rodadas em `docs/rodadas/`.

## 2. Quem está aprendendo

O João Pedro (dono do repo) é desenvolvedor PHP/Oracle/NestJS/Next.js e está **aprendendo Kotlin e Android neste projeto**.

- Explique o **porquê** das decisões de Android. Analogias com NestJS/React ajudam: ViewModel ≈ service com estado; Room ≈ TypeORM; Compose ≈ React com state.
- Comentários de código em **português**, explicando intenção, como o resto do código já faz.
- No fim de cada rodada, escreva um **guia em PT-BR casual** em `docs/rodadas/` (veja §9).

---

## 3. Stack real (não troque sem discutir)

| Área | O que é usado |
|---|---|
| Linguagem / UI | Kotlin 2.2.10, Jetpack Compose (BOM 2026.02.01, Material 3 1.4) |
| Build | AGP 9.3.2, Gradle Version Catalog (`gradle/libs.versions.toml`), KSP |
| Arquitetura | MVVM + Repository, com camada `domain` pura |
| Banco | Room 2.8.5 (`data/local`), com migrações e schemas em `app/schemas` |
| DI | **Manual**: a classe `di/Dependencias.kt`, guardada pelo `AgroSaldoApp` e lida nas `Factory` de cada ViewModel (`dependencias` em `CreationExtras`). **Não** usamos Hilt/Koin (decisão consciente: é projeto de aprendizado) |
| Rede | `HttpURLConnection` + `org.json` (`data/clima/FontePrevisao.kt`). Só a Open-Meteo, HTTPS |
| Preferências | `SharedPreferences` (`data/preferencias`, `data/sessao`) |
| Segundo plano | WorkManager (`aviso/VerificacaoChuvaWorker.kt`) |
| Outros | Navigation Compose, Biometric, SplashScreen, fonte Plus Jakarta Sans embutida |
| Testes | JUnit 4 + kotlinx-coroutines-test, com fakes em memória em `src/testFixtures` (sem MockK/Turbine) |

`minSdk 26`, `compileSdk` / `targetSdk` 37. Java 11 no `compileOptions`.

---

## 4. Estrutura e nomes

Pacote: `br.joao.agrosaldo`.

```
aviso/        Worker, notificação e textos do aviso de chuva (Android)
core/         navigation/ (AppNavegacao, Rotas), ui/ (componentes Safra), util/ (formatadores), localizacao/, seguranca/
di/           Dependencias (container da DI manual)
data/         SelecaoAtual, Desfazer, local/ (Room: entidades, DAOs, migrações), repository/ (Room),
              clima/ (Open-Meteo), preferencias/, sessao/
domain/       model/, repository/ (interfaces), financeiro/ (saldo e ranking), clima/ (regra de chuva), conta/ (senha, conta)
feature/      inicio, lancamento, propriedade, clima, historico, conta — cada uma com *Screen.kt + *ViewModel.kt
ui/theme/     Color.kt, Type.kt, Shape.kt, Theme.kt, IconesAgro.kt (ícones desenhados)
```

**Convenção de nomes:** domínio em **português** (`Lancamento`, `Propriedade`, `aoSalvar`, `uiState`) com sufixos técnicos do Android em inglês (`Repository`, `Entity`, `Dao`, `Screen`, `ViewModel`, `UiState`). Mantenha esse padrão.

---

## 5. Regras de código (o código atual já cumpre — não quebre)

1. **Nada de `!!`.** Trate nulo explicitamente.
2. **Nenhuma cor literal fora de `ui/theme`.** Use `MaterialTheme.colorScheme` ou `MaterialTheme.agro`.
3. **Nada de `GlobalScope`.** Use `viewModelScope`.
4. **`domain/` não importa Android**: nada de `android.*`, `androidx.*`, Room nem Compose.
5. **ViewModel não importa Compose.**
6. **Dinheiro é `Long` em centavos.** Formatação só na UI (`core/util/Moeda.kt`).
7. **Datas são `java.time`** (`LocalDate`, `YearMonth`, `Instant`). Nada de `LocalDate.now()` no `init` de modelo (foi o crash corrigido na R5).
8. **Regra de negócio mora no domínio.** Conta de saldo, ranking e afins são funções puras em `domain/` (ex.: `ResumoFinanceiro`), testadas sem Android. ViewModel só junta dados e chama o domínio.
9. **Nada de estado global.** Estado compartilhado (ex.: `SelecaoAtual`) é criado no `Dependencias` e passado no construtor.
10. **Fakes e dados de exemplo ficam em `src/testFixtures`**, nunca em `src/main`: o APK não leva código de teste e o app começa sempre vazio.
11. **Erros em coroutine:** use `tentar { }` (`core/util/Erros.kt`), que não engole `CancellationException`, e mostre a mensagem com `mensagemParaUsuario(...)`.
12. **Gravações que mexem em mais de uma tabela** vão numa transação (`data/local/Transacao.kt`).
13. **Composables sem estado** quando possível: recebem `uiState` e lambdas, não o ViewModel. Toda tela tem `@Preview` (claro e escuro).
14. **Biblioteca nova** só com justificativa, e sempre pelo `libs.versions.toml`.

---

## 6. Segurança e privacidade — não negociável

- **Nenhum segredo no repositório.** `local.properties` fica no `.gitignore`.
- **A chave de assinatura (`*.jks` / `*.keystore`) NUNCA entra no Git.** Nunca gere uma chave nova: com outra chave o APK não instala por cima do antigo e o produtor perde os dados.
- `.gitignore` precisa ter `*.jks`, `*.keystore`, `/app/release/`, `*.apk`, `*.aab`, `local.properties`.
- **Dados pessoais reais do produtor não entram em código, testes nem docs**: nada de CNPJ, endereço, telefone, e-mail ou coordenadas da fazenda. Use dados fictícios.
- Senha: PBKDF2 (`domain/conta/HashDeSenha.kt`). Não enfraqueça.
- Nada de `Log`/`println` com dado pessoal ou financeiro.
- Open-Meteo: o plano grátis é só para uso **não comercial**, e os dados são CC BY 4.0 (crédito com link na tela de Clima). Se o app ganhar anúncio ou cobrança, precisa do plano pago.

---

## 7. Regra de negócio central: aviso de chuva (RN04)

Avisa quando, nas próximas N horas (padrão 12), **chance máxima ≥ limite (padrão 60%) OU chuva somada ≥ limite (padrão 2 mm)**.

- Os limites são do usuário (tela "Aviso de chuva").
- A regra é a função pura `domain/clima/RegraDeChuva.kt` e é toda coberta por teste.
- Os textos da chuva ficam num lugar só: `core/util/TextosDaChuva.kt`, usados pelo Início, pelo Clima e pela notificação.

---

## 8. Comandos

```bash
./gradlew testDebugUnitTest   # testes unitários — rode SEMPRE antes de commitar
./gradlew assembleDebug       # compila o app inteiro, incluindo as telas Compose
./gradlew lint                # Android Lint
```

Os testes instrumentados (`connectedAndroidTest`) precisam de emulador e **não rodam na nuvem**. Se mexer em DAO ou migração, avise para rodar no Android Studio.

O GitHub Actions (`.github/workflows/android.yml`) roda `testDebugUnitTest assembleDebug lintDebug` em todo push e PR.

---

## 9. Como trabalhar neste repo

1. **Plano primeiro.** Antes de editar, apresente o plano: arquivos que vão mudar e por quê. Espere o OK.
2. **Uma rodada por branch**: `rodada/r6-dominio`, `rodada/r7-componentes`... Nunca commite direto na `main`.
3. **Commits pequenos** em Conventional Commits, em português: `refactor(inicio): usa ResumoFinanceiro do domínio`.
4. **Teste junto:** cada regra nova ganha teste. Cada bug corrigido ganha um teste que falharia antes.
5. **Antes do PR**, rodar `./gradlew testDebugUnitTest assembleDebug lint`: tudo verde e nenhum warning novo do Kotlin.
6. **Abra o PR** com o resumo e **não faça merge**: o João revisa e testa no celular primeiro.
7. **Guia da rodada** em `docs/rodadas/RX.md`: o que mudou, **o porquê** (com a "lição" de cada mudança), o que testar no celular e a mensagem de commit. Casual, em português, com títulos, negrito e listas curtas.
8. Se algo depende de decisão de produto (texto, comportamento visível ao produtor), **pergunte** em vez de assumir.

O plano das próximas rodadas está em `docs/ROADMAP.md`.
