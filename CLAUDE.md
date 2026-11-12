# CLAUDE.md — AgroSaldo

Guia de referência para quem contribui com o projeto, pessoas ou agentes de IA: contexto do produto, arquitetura, convenções obrigatórias, segurança, testes e fluxo de trabalho. **Leia o arquivo inteiro antes de qualquer alteração.**

A documentação técnica detalhada (pasta por pasta, arquivo por arquivo, bibliotecas, design e data classes) fica em [`docs/DOCUMENTACAO_TECNICA.mdx`](docs/DOCUMENTACAO_TECNICA.mdx). Quando o código mudar de forma relevante, atualize a seção correspondente nela no mesmo PR.

## Sumário

1. [Produto](#1-produto)
2. [Stack](#2-stack)
3. [Arquitetura e estrutura](#3-arquitetura-e-estrutura)
4. [Convenções de código](#4-convenções-de-código)
5. [Segurança e privacidade](#5-segurança-e-privacidade)
6. [Regras de negócio](#6-regras-de-negócio)
7. [Build, testes e qualidade](#7-build-testes-e-qualidade)
8. [Fluxo de trabalho](#8-fluxo-de-trabalho)
9. [Decisões registradas](#9-decisões-registradas)
10. [Roadmap e fora do escopo](#10-roadmap-e-fora-do-escopo)

---

## 1. Produto

Aplicativo Android nativo para o pequeno produtor rural controlar **receitas e despesas por propriedade** e receber **aviso de chuva**, que recomenda adiar a irrigação do pivô.

| | |
|---|---|
| Contexto | Projeto acadêmico (Engenharia de Software, UNIFAE) com usuário real: um produtor rural que usa o app no dia a dia |
| Requisitos da disciplina | Arquitetura **MVVM** e persistência local com **Room**; as telas seguem o protótipo do Figma |
| Modelo de dados | **100% local**: Room no aparelho, sem backend e sem sincronização |
| Rede | Uma única integração: previsão do tempo da Open-Meteo, por HTTPS |
| Versão | `versionCode 7` / `versionName 1.6`. A 1.5 (`versionCode 6`) é a versão em uso pelo produtor |
| Plataforma | `minSdk 26` (Android 8.0), `compileSdk` e `targetSdk` 37 |

**Comunicação:** respostas, comentários de código e mensagens de commit em **português**. Ao explicar uma decisão, priorize o porquê, sobretudo no que é específico do Android. Analogias com NestJS e React ajudam: ViewModel ≈ service com estado; Room ≈ TypeORM; DAO ≈ repository do TypeORM; Compose ≈ React com estado.

---

## 2. Stack

Não troque nenhum item desta tabela sem discussão prévia.

| Área | Tecnologia |
|---|---|
| Linguagem e UI | Kotlin 2.4.21, Jetpack Compose (BOM 2026.09.00, Material 3), Navigation Compose |
| Build | AGP 9.5.0 (Kotlin embutido, na versão do plugin Kotlin do catálogo), Gradle 9.8.2, Version Catalog (`gradle/libs.versions.toml`), KSP 2.3.13 |
| JDK | O Gradle roda no JDK 25 (toolchain do daemon, baixado automaticamente); o código compila para Java 11 |
| Arquitetura | MVVM + Repository; domínio no **módulo Gradle `:domain`**, em Kotlin puro |
| Banco | Room 2.8.5, banco versão **4** (`usuarios`, `propriedades`, `lancamentos`, `previsoes`); schemas exportados em `app/schemas` |
| Injeção de dependência | Hilt 2.60.1 + AndroidX Hilt 1.4.0 (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@HiltWorker`), via KSP |
| Rede | `HttpURLConnection` + `org.json` (`data/clima/FontePrevisao.kt`) |
| Preferências | `SharedPreferences` (`data/preferencias`, `data/sessao`) |
| Segundo plano | WorkManager 2.12 (`aviso/VerificacaoChuvaWorker.kt`) |
| Outros | Biometric, SplashScreen, fonte Plus Jakarta Sans embutida |
| Testes | JUnit 4, kotlinx-coroutines-test, Robolectric 4.17, Roborazzi 1.77, Room sobre Robolectric para DAOs e migrações (`src/sharedTest`); fakes em memória em `src/testFixtures` (sem MockK nem Turbine), que conferem posse como o Room (`ContratoDosRepositoriosTest` roda os mesmos cenários nos dois); Worker testado pela função `verificarChuva` e agendamento pelo WorkManager real no Robolectric |
| Qualidade | ktlint (plugin 14.2, estilo `android_studio`, regras de Compose 0.6.8), detekt 1.23.8 **sem baseline**, Android Lint com `abortOnError` |

---

## 3. Arquitetura e estrutura

```
View (Compose)  ──eventos──▶  ViewModel  ──chama──▶  Domínio (:domain)  ◀──implementa──  Dados (Room, prefs, rede)
      ▲                          │
      └──── StateFlow<UiState> ──┘           Hilt liga as camadas pelo construtor
```

- **View:** composables sem estado que recebem `uiState` e lambdas.
- **ViewModel:** guarda o estado da tela num `StateFlow`, recebe eventos e delega as regras ao domínio.
- **Domínio:** modelos, regras puras e **interfaces** dos repositórios. O compilador recusa `android.*`, `androidx.*`, Room, Compose e Hilt nesse módulo.
- **Dados:** implementações dos repositórios (Room, SharedPreferences, Open-Meteo). As entidades do Room são separadas dos modelos e convertidas nos mapeadores.

### Código de produção (`br.joao.agrosaldo`)

```
AgroSaldoApp.kt  Application (@HiltAndroidApp): canal de notificação e agendamento do Worker
MainActivity.kt  única Activity: FLAG_SECURE, capa de bloqueio, intent da notificação
aviso/           Worker, notificação e agendamento do aviso de chuva
core/            navigation/ (AppNavegacao, Navegador, GrafosDeNavegacao, RegrasDeNavegacao, TransicoesDeTela, BarraInferior, Rotas),
                 ui/ (componentes compartilhados), util/ (Moeda, datas, Erros, TextosDaChuva), localizacao/, seguranca/
di/              módulos do Hilt: TempoModule, BancoModule, PreferenciasModule, RepositoriosModule, ServicosModule
data/            SelecaoAtual, Desfazer, local/ (entidades, DAOs, banco, migrações, Transacao, Mapeadores),
                 repository/ (implementações Room), clima/ (Open-Meteo), preferencias/, sessao/
domain/          módulo :domain — model/, repository/ (interfaces), financeiro/ (ResumoFinanceiro), clima/ (RegraDeChuva),
                 conta/ (HashDeSenha, ContaService, regras da conta e do bloqueio), Relogio e Despachantes
feature/         inicio, lancamento, propriedade, clima, historico, conta, sessao — cada uma com *Screen.kt + *ViewModel.kt
ui/theme/        cores, tipografia, formas, Espacos, Theme, IconesAgro, PreviasDaTela (multiprévia)
```

### Testes e configuração

```
domain/src/test/          testes do domínio (JVM puro)
domain/src/testFixtures/  fakes *Memoria, DadosDeExemplo e relógios de teste
app/src/testFixtures/     AjustesAvisoMemoria e Semeador; reexporta os fakes do :domain
app/src/test/             ViewModels, dados, Worker, preferências, telas (Robolectric), capturas (Roborazzi), grafo do Hilt
app/src/sharedTest/       DAOs e migrações do Room: rodam no JVM (Robolectric, inclusive no CI) e no aparelho
app/schemas/              JSON do schema do Room, um por versão (a partir da 3)
config/detekt/detekt.yml  regras do detekt
.editorconfig             regras do ktlint
.github/workflows/android.yml  pipeline de CI
```

### Nomenclatura

Domínio em **português** (`Lancamento`, `Propriedade`, `aoSalvar`, `uiState`) com os sufixos técnicos do Android em inglês (`Repository`, `Entity`, `Dao`, `Screen`, `ViewModel`, `UiState`).

### Data classes

São 65 (somando `app` e `:domain`), em cinco grupos: modelos do domínio, regras e resultados do domínio, entidades do Room, `UiState` das telas e tema/navegação. Modelos do domínio validam invariantes no `init` (`require`). As classes do tema são `@Immutable`.

---

## 4. Convenções de código

O código atual cumpre todas estas regras. Uma alteração que quebre qualquer uma delas não é aceita.

### Kotlin e arquitetura

1. **Sem `!!`.** Trate nulos explicitamente.
2. **Sem `GlobalScope`.** Use `viewModelScope`.
3. **O `:domain` não importa Android:** nada de `android.*`, `androidx.*`, Room, Compose ou Hilt. É garantido pelo build, não só por convenção.
4. **ViewModel não importa Compose.**
5. **Regra de negócio mora no domínio.** Saldo, ranking e afins são funções puras em `domain/` (ex.: `ResumoFinanceiro`), testadas sem Android. O ViewModel apenas reúne os dados e chama o domínio.
6. **Sem estado global.** Estado compartilhado (`SelecaoAtual`, `Desfazer`) é `@Singleton` num módulo do Hilt e chega pelo construtor.
7. **Propriedade privada com `_`** só existe se houver a pública correspondente (`_uiState` / `uiState`).
8. **Bibliotecas novas** só com justificativa e sempre pelo `libs.versions.toml`.

### Dados, tempo e erros

9. **Dinheiro é `Long` em centavos.** No banco, `INTEGER`, nunca `REAL`. A formatação acontece só na UI (`core/util/Moeda.kt`).
10. **Datas usam `java.time`** (`LocalDate`, `YearMonth`, `Instant`). Nunca chame `LocalDate.now()` no `init` de um modelo. A RN07 é checada ao salvar, com `Lancamento.dataDentroDoLimite`.
11. **Tempo e threads são injetados:** a hora do calendário vem de `java.time.Clock` (`LocalDate.now(relogio)`, nunca `now()` solto), a duração vem de `RelogioMonotonico` e as threads vêm de `Despachantes`, no lugar de `Dispatchers.*`. Nos testes, use `relogioEm`, `relogioPorMs`, `RelogioMutavel` e `DespachantesDeTeste`.
12. **Erros em coroutines:** use `tentar { }` (`core/util/Erros.kt`), que não engole `CancellationException`, e exiba a mensagem com `mensagemParaUsuario(...)`. Todo `Flow` lido do banco tem tratamento de erro e um campo de erro no `UiState`.
13. **Gravações em mais de uma tabela** acontecem numa transação (`data/local/Transacao.kt`).
14. **Ler e apagar acontecem na mesma transação.** `LancamentoRepository.excluir` devolve um `LancamentoExcluido` (o lançamento e o instante da última gravação) ou `null`, e `restaurar` regrava com o mesmo instante. Assim, o "Desfazer" devolve exatamente o que saiu do banco, na mesma posição da lista.
15. **Todo texto digitado tem limite**, validado no domínio (`require`) e aplicado no ViewModel ao digitar ou colar:

    | Campo | Limite |
    |---|---|
    | Descrição do lançamento | 200 caracteres |
    | Nome da propriedade | 80 caracteres |
    | Cultura | 60 caracteres |
    | Nome da conta | 80 caracteres |
    | E-mail | 254 caracteres |
    | Senha | 128 caracteres |
    | Área | até 1.000.000 ha, sempre finita |
    | Texto da área e das coordenadas | 20 caracteres |

    Registros gravados antes de um limite existir são lidos já ajustados a ele nos mapeadores (`data/local/Mapeadores.kt`), sem migração: uma linha antiga não pode esconder a lista nem fechar o app.
16. **Fakes e dados de exemplo ficam em `src/testFixtures`**, nunca em `src/main`. O APK não leva código de teste e o app sempre começa vazio, inclusive no build de debug.

### Interface (Compose)

17. **Composables sem estado** sempre que possível: recebem `uiState` e lambdas (ou uma classe `*Acoes` quando são muitas), nunca o ViewModel.
18. **Todo composable que desenha recebe `modifier`.**
19. **Toda tela tem as prévias clara, escura e com fonte 2×**, pela multiprévia `@PreviasDaTela` (`ui/theme`).
20. **Nenhuma cor literal fora de `ui/theme`.** Use `MaterialTheme.colorScheme` ou `MaterialTheme.agro`.
21. **Use os componentes de `core/ui`** (`BotaoPrincipal`, `BotaoSecundario`, `BotaoDestaque`, `BotaoTonal`, `Carregando`, `EstadoVazio`, `EstadoErro`, `FaixaDeAviso`) e os espaçamentos de `Espacos`. Não recrie botões nem use `dp` solto para margem.
22. **Alvos de toque têm no mínimo 48 dp**, com `heightIn` em vez de `height` fixo.
23. **Callbacks dentro de `LaunchedEffect`** passam por `rememberUpdatedState`.

---

## 5. Segurança e privacidade

Estas regras não são negociáveis.

### Repositório

- **Nenhum segredo no repositório.** `local.properties` fica no `.gitignore`.
- **A chave de assinatura fica fora do repositório e nunca é substituída.** Um APK assinado com outra chave não instala por cima do anterior, e o usuário perderia os dados.
- O `.gitignore` precisa conter `*.jks`, `*.keystore`, `/app/release/`, `*.apk`, `*.aab` e `local.properties`.
- **Nenhum dado pessoal real** em código, testes, documentação ou capturas: nada de nomes, CNPJ, endereço, telefone, e-mail ou coordenadas reais. Use dados fictícios.

### Aplicativo

- **Senha:** PBKDF2-HMAC-SHA256 (`domain/conta/HashDeSenha.kt`). Não reduza o custo nem troque o algoritmo.
- **Bloqueio por tentativas:** usa relógio monotônico (`InstanteMonotonico`), imune à mudança da hora do aparelho, e contador por e-mail que sobrevive ao reinício do app. Depois de reiniciar o aparelho, a espera recomeça inteira; reiniciar ou mudar a hora nunca a encurta. O relógio de parede só é usado quando o sistema não expõe o `BOOT_COUNT`.
- **`FLAG_SECURE`:** aplicado na Activity **somente com o bloqueio por digital ligado**, impedindo captura de tela e miniatura em Recentes. Com o bloqueio desligado, a captura é permitida (decisão de produto).
- **Ajustes do aparelho:** o bloqueio por digital e os valores ocultos valem para o aparelho. Desligar o bloqueio exige a digital, e excluir uma conta só restaura esses ajustes quando não resta nenhuma conta.
- **Backup:** apenas criptografado e limitado. Não remova essa configuração.
- **Logs:** nenhum `Log` ou `println` com dado pessoal ou financeiro.
- **Open-Meteo:** o plano gratuito é para uso **não comercial**, e os dados são CC BY 4.0 (crédito com link na tela de Clima). Anúncios ou cobrança exigem o plano pago.

---

## 6. Regras de negócio

| Regra | Descrição | Implementação |
|---|---|---|
| RN01 | Todo lançamento pertence a exatamente uma propriedade | `Lancamento` (`init`) |
| RN02 | Excluir uma propriedade apaga os lançamentos e a previsão dela (CASCADE), com opção de desfazer | entidades do Room + `excluirComLancamentos` / `restaurar` |
| RN03 | Saldo do período = receitas − despesas | `domain/financeiro/ResumoFinanceiro.kt` |
| RN04 | Aviso de chuva (abaixo) | `domain/clima/RegraDeChuva.kt` |
| RN06 | Valor sempre positivo; o sinal vem do tipo | `Lancamento` (`init`) e `ResumoFinanceiro.totais` |
| RN07 | Data no máximo 12 meses no futuro, checada ao salvar | `Lancamento.dataDentroDoLimite` |

**RN04:** o app avisa quando, nas próximas N horas (padrão 12), a **chance máxima de chuva ≥ limite (padrão 60%) OU a chuva somada ≥ limite (padrão 2 mm)**.

- Os limites são configurados pelo usuário na tela "Aviso de chuva".
- A regra é a função pura `RegraDeChuva`, integralmente coberta por testes.
- Os textos sobre chuva ficam centralizados em `core/util/TextosDaChuva.kt` e são usados pelo Início, pelo Clima e pela notificação.

---

## 7. Build, testes e qualidade

### Comandos

```bash
./gradlew ktlintCheck        # formatação (ktlintFormat corrige automaticamente)
./gradlew detekt             # análise estática dos dois módulos: zero achados, sem baseline
./gradlew testDebugUnitTest  # testes JVM do app, incluindo Robolectric e capturas (obrigatório antes de commitar)
./gradlew :domain:test       # testes do módulo de domínio
./gradlew assembleDebug      # compila o app
./gradlew lintDebug          # Android Lint (qualquer erro quebra o build)
```

### Banco de dados

- Os testes de DAO e de migração ficam em `app/src/sharedTest` e rodam no JVM (Robolectric, dentro do `testDebugUnitTest` e do CI) e no aparelho (`connectedAndroidTest`, que exige emulador e não roda no CI). Toda mudança em DAO, entidade ou migração deve ser validada também no aparelho.
- **Mudou uma entidade?** Suba a versão do banco (hoje 4), escreva a migração em `data/local/Migracoes.kt` (registrada em `comMigracoes()`), exporte o schema e acrescente o teste em `MigracaoDoBancoTest`.
- **Nunca use `fallbackToDestructiveMigration`.**

### Particularidades das ferramentas

Não "corrija" estes pontos sem entender o motivo.

- **detekt:** o plugin Gradle 1.23 não roda no JDK 25. A tarefa `detekt` é um `JavaExec` que chama a CLI do detekt numa **JVM 21** separada (toolchain), com o Kotlin da configuração `detektCli` fixado em 2.0.21 (`app/build.gradle.kts`).
- **Robolectric:** roda com `sdk = 36`, gráficos `NATIVE` e `application = android.app.Application` (não sobe o `AgroSaldoApp`). Os argumentos de JVM `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` e `--add-opens=java.base/java.io=ALL-UNNAMED` já estão configurados.
- **Roborazzi:** o `CapturasDeTelaTest` grava PNGs em `app/build/capturas`. Não compara pixels; garante apenas que a tela desenha.
- **ktlint:** a regra `preview-annotation-naming` está desligada no `.editorconfig`, porque a multiprévia segue a nomenclatura em português (`@PreviasDaTela`). O detekt também ignora prévias privadas marcadas com ela.
- **AGP 9.5:** o `gradle.properties` define `android.onlyEnableUnitTestForTheTestedBuildType=false`, porque a prévia do Compose gerada para o release exige os testes unitários ligados nele. O CI continua rodando só o `testDebugUnitTest`.
- **Versão do Kotlin:** o AGP 9 usa a versão do plugin Kotlin presente no classpath. Os plugins `kotlin-jvm` e `kotlin-compose` do catálogo (versão `kotlin`) definem o Kotlin do app e do domínio juntos.
- **Testes no aparelho:** o `androidx.test:runner` fica declarado em `androidTestImplementation`; sem ele, o `connectedAndroidTest` não inicia.
- **`src/sharedTest`:** entra nos source sets `test` e `androidTest` por `kotlin.directories` (o Kotlin embutido no AGP 9 compila pelo conjunto `kotlin`). O detekt trata essa pasta como diretório de teste.
- **Módulo `:domain`:** usa o plugin `org.jetbrains.kotlin.jvm` com `java-test-fixtures`. O detekt do app também analisa as pastas do domínio. Propriedades públicas de outro módulo não permitem *smart cast*: copie para uma `val` local antes de checar `!= null`.
- **Hilt:**
  - `hiltViewModel()` vem de `androidx.hilt.lifecycle.viewmodel.compose`; o pacote `navigation.compose` está obsoleto.
  - Os formulários (`LancamentoFormViewModel`, `PropriedadeFormViewModel`) têm um construtor secundário `@Inject` que lê os argumentos da rota pelo `SavedStateHandle`. Por isso as chaves do rascunho do lançamento levam o prefixo `rascunho_`, já que o handle também recebe `tipo` e `id`. As chaves do formulário de propriedade (`nome`, `cultura`, `area`, `latitude`, `longitude`) não colidem com o `id`.
  - O iniciador do WorkManager foi removido do manifesto: o `AgroSaldoApp` é o `Configuration.Provider`, com a fábrica de Workers do Hilt.
  - O `GrafoDeDependenciasTest` sobe o grafo real no Robolectric (`kspTest` + `hilt-android-testing`).
- **Execução em paralelo:** rodar `lintDebug` junto com `compileDebugAndroidTestKotlin` ou `assembleRelease` pode falhar por corrida nos arquivos gerados do Hilt. O conjunto do CI roda limpo.
- **`testFixtures`:** precisa do Compose runtime no classpath (`testFixturesImplementation` do BOM e do runtime), por causa do compilador do Compose.

### Integração contínua (`.github/workflows/android.yml`)

- Executa `ktlintCheck detekt testDebugUnitTest :domain:test assembleDebug lintDebug` em todo push na `prod` e em todo PR. É sempre o conjunto completo.
- Os erros viram anotações no check run, o que permite ler a falha sem abrir o log.
- As capturas do Roborazzi são publicadas na branch **`ci/capturas`** (não apague), por isso o workflow tem `permissions: contents: write`.
- O `gradlew` tem o bit de execução no Git, e o CI ainda executa `chmod +x gradlew` antes do Gradle.
- O nome do ramo entra no commit das capturas por variável de ambiente (`RAMO`), nunca interpolado no script.

---

## 8. Fluxo de trabalho

1. **Plano antes do código.** Apresente os arquivos que vão mudar e o motivo, e aguarde aprovação.
2. **Uma tarefa por branch**, a partir da `prod`: `feat/...`, `fix/...`, `refactor/...`, `docs/...`, `chore/...`, `rodada/...`.
3. **Commits pequenos** em [Conventional Commits](https://www.conventionalcommits.org/), em português. Exemplo: `refactor(inicio): usa ResumoFinanceiro do domínio`.
4. **Autoria:** commits feitos com a conta do mantenedor (`Morettys2`), já configurada no `git config` local, **sem** `Co-Authored-By` nem outros trailers de atribuição.
5. **Testes acompanham a mudança.** Toda regra nova ganha teste, e todo bug corrigido ganha um teste que falharia antes da correção.
6. **CI verde antes do PR:** ktlint, detekt, testes, build e Lint, sem warnings novos do Kotlin.
7. **Pull request** com resumo das mudanças. **Nenhum merge sem aprovação:** o mantenedor revisa e testa no aparelho antes.
8. **Decisões de produto** (textos, comportamento visível ao usuário) são perguntadas, nunca presumidas.
9. **Documentação:**
   - o `README.md` é a vitrine do repositório e deve manter números e stack atualizados;
   - a `docs/DOCUMENTACAO_TECNICA.mdx` é a documentação técnica;
   - este arquivo é a referência de regras e decisões;
   - não crie CHANGELOG.

### Branches

| Branch | Papel |
|---|---|
| `prod` | Principal e padrão no GitHub. Todo trabalho parte dela e volta por PR; o CI roda em cada push |
| `ci/capturas` | Capturas de tela publicadas pelo CI. Não apague |
| `desenvolvimento` | Histórico anterior (antiga `main`), congelado como backup. Não receba commits |

---

## 9. Decisões registradas

| Data | Decisão | Motivo |
|---|---|---|
| out/2026 | App 100% local, sem API própria | Uso em campo sem sinal, um aparelho por produtor, sem custo de servidor e menor exposição de dados pessoais. Os ids já são UUID e as entidades têm `atualizado_em`, o que permite retomar a sincronização no futuro |
| out/2026 | Domínio em módulo Gradle próprio (`:domain`) | A separação entre domínio e Android passa a ser garantida pelo compilador |
| out/2026 | Hilt no lugar da DI manual (`Dependencias`) | Padrão do ecossistema, grafo verificado em compilação e testável |
| out/2026 | Relógio, relógio monotônico e despachantes injetados | Testes determinísticos e bloqueio imune à mudança de hora |
| out/2026 | detekt sem baseline | Os 115 achados antigos foram corrigidos ou justificados no ponto exato. Achado novo é corrigido, não ignorado |
| out/2026 | `FLAG_SECURE` só com o bloqueio por digital ligado | Equilíbrio entre privacidade e a possibilidade de o usuário tirar print |
| out/2026 | Transições: troca de aba instantânea; demais telas deslizam e a anterior some em 90 ms | O crossfade padrão de 700 ms deixava a tela anterior visível por baixo da nova (`core/navigation/TransicoesDeTela.kt`) |
| 28/10/2026 | Auditoria de código aplicada | Dados reais removidos do projeto; leitura resiliente do banco; bloqueio que resiste à exclusão de conta e ao reinício; DAOs testados também no JVM; prévias e espaçamentos padronizados em todas as telas |
| 02/11/2026 | Migração 3 → 4: `propriedades.usuario_id` obrigatório | Propriedades sem dono vão para a conta mais antiga do aparelho; sem conta, são removidas com o que pertence a elas. A versão 3 é a base da produção, sem migrações anteriores |
| 02/11/2026 | Atualização de dependências (DEP-01) | AGP 9.5.0, Gradle 9.8.2, Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00, WorkManager 2.12.0, core-ktx 1.19.1, splashscreen 1.2.0, Roborazzi 1.77.0, org.json 20260814; testes de tela com `createComposeRule` do pacote `junit4.v2` |

---

## 10. Roadmap e fora do escopo

### Próxima etapa: publicação na Play Store (R9)

- **Privacidade:**
  - política de privacidade em link público (GitHub Pages), com responsável e contato (LGPD), o IP recebido pela Open-Meteo, como apagar os dados e como funciona o backup;
  - trocar `ACCESS_FINE_LOCATION` por `ACCESS_COARSE_LOCATION` e arredondar as coordenadas enviadas para 0,02°.
- **Release:**
  - AAB (`bundleRelease`) com Play App Signing; a chave atual passa a ser a *upload key*;
  - senhas da chave em `~/.gradle/gradle.properties`;
  - build de release validado com R8/minify;
  - `localeFilters += "pt-rBR"`.
- **Play Console:**
  - formulário Data safety (localização, criptografia em trânsito);
  - declaração de recursos financeiros ("não oferece");
  - público adulto e classificação indicativa;
  - teste fechado com **12 testadores por 14 dias consecutivos**, exigido para contas pessoais novas.
- **Desejável:** exportação dos dados (portabilidade, LGPD) e textos movidos para `strings.xml`.

### Fora do escopo

| Item | Motivo |
|---|---|
| API própria, sincronização entre aparelhos e recuperação de senha por e-mail | O app é 100% local por decisão de produto (ver [Decisões registradas](#9-decisões-registradas)) |
| Controle da irrigação (acionar o pivô) | A previsão serve apenas para **recomendar** adiar a irrigação ("Dá para adiar o pivô") |
