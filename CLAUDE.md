# CLAUDE.md — AgroSaldo

Contexto permanente do projeto: regras e decisões. Leia inteiro antes de qualquer alteração.

A documentação técnica detalhada (pasta por pasta, arquivo por arquivo, bibliotecas, design e data classes) fica em `docs/DOCUMENTACAO_TECNICA.mdx` (um único arquivo MDX, com sumário), escrita para os outros devs do grupo. Quando o código mudar de forma relevante, atualize a seção correspondente nele.

---

## 1. O projeto

App Android nativo para o pequeno produtor rural controlar **receitas e despesas por propriedade** (sítio) e receber **aviso de chuva**, que recomenda adiar a irrigação do pivô.

- Projeto acadêmico (UCE, Engenharia de Software, UNIFAE) com usuário real: um produtor rural real (pai do João), que já usa o APK.
- A professora cobra **MVVM** e **persistência local com Room**. Há um protótipo no Figma que o app precisa seguir.
- O app é **100% local**: Room no celular, sem backend e sem sincronização. A única chamada de rede é a previsão da Open-Meteo.
- Versão atual: `versionCode 6` / `versionName 1.5`.

## 2. Quem está aprendendo

O João Pedro (GitHub **Morettys2**, dono do repo) é desenvolvedor PHP/Oracle/NestJS/Next.js e está **aprendendo Kotlin e Android neste projeto**.

- Explique o **porquê** das decisões de Android. Analogias com NestJS/React ajudam: ViewModel ≈ service com estado; Room ≈ TypeORM; Compose ≈ React com state; DAO ≈ repository do TypeORM.
- Comentários de código em **português**, explicando a intenção, como o resto do código já faz.
- Respostas para ele em PT-BR casual, com títulos, negrito e listas curtas.

---

## 3. Stack real (não troque sem discutir)

| Área | O que é usado |
|---|---|
| Linguagem / UI | Kotlin 2.4.21, Jetpack Compose (BOM 2026.09.00, Material 3) |
| Build | AGP 9.5.0 (Kotlin embutido no AGP, na versão do plugin Kotlin do catálogo), Gradle 9.8.2, Version Catalog (`gradle/libs.versions.toml`), KSP |
| JDK | Gradle roda no JDK 25 (toolchain do daemon); o código compila para Java 11 |
| Arquitetura | MVVM + Repository, com o domínio num **módulo Gradle próprio (`:domain`, Kotlin puro)**: o compilador recusa `android.*`, Room, Compose e Hilt ali |
| Banco | Room 2.8.5, banco versão 3 (tabelas `usuarios`, `propriedades`, `lancamentos`, `previsoes`), que é a base da produção: **sem migrações antigas**, a próxima parte da 3. Schema exportado em `app/schemas` |
| DI | **Hilt** 2.60.1 (+ AndroidX Hilt 1.4.0): `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel` e `@HiltWorker`; módulos em `di/` (`TempoModule`, `BancoModule`, `PreferenciasModule`, `RepositoriosModule`, `ServicosModule`); processador via KSP. Antes era DI manual (`Dependencias`), trocado em out/2026 por decisão do João |
| Rede | `HttpURLConnection` + `org.json` (`data/clima/FontePrevisao.kt`). Só a Open-Meteo, por HTTPS |
| Preferências | `SharedPreferences` (`data/preferencias`, `data/sessao`) |
| Segundo plano | WorkManager 2.12 (`aviso/VerificacaoChuvaWorker.kt`) |
| Outros | Navigation Compose, Biometric, SplashScreen, fonte Plus Jakarta Sans embutida |
| Testes | JUnit 4, kotlinx-coroutines-test, Robolectric 4.17, Roborazzi 1.77; Room em memória sobre o Robolectric para os DAOs (`src/sharedTest`); fakes em memória em `src/testFixtures` (sem MockK nem Turbine), que com uma sessão conferem posse como o Room (`ContratoDosRepositoriosTest` roda os mesmos cenários nos dois). O Worker é testado pela função `verificarChuva` e o agendamento pelo WorkManager de verdade no Robolectric, sem `work-testing` |
| Qualidade | ktlint (plugin 14.2, estilo `android_studio`, regras de Compose 0.6.8), detekt 1.23.8 **sem baseline (zero achados)**, Android Lint com `abortOnError` |

`minSdk 26`, `compileSdk` e `targetSdk` 37.

---

## 4. Estrutura e nomes

Pacote: `br.joao.agrosaldo`.

```
AgroSaldoApp.kt  Application (@HiltAndroidApp): canal de notificação e agendamento do Worker
MainActivity.kt  única Activity; FLAG_SECURE, capa de bloqueio, intent da notificação
aviso/           Worker, notificação e agendamento do aviso de chuva (Android)
core/            navigation/ (AppNavegacao, Navegador, GrafosDeNavegacao, RegrasDeNavegacao, TransicoesDeTela, BarraInferior, Rotas),
                 ui/ (componentes "Safra": botões, estados, seletor, cabeçalho),
                 util/ (Moeda, datas, Erros, TextosDaChuva), localizacao/, seguranca/
di/              módulos do Hilt (Tempo, Banco, Preferências, Repositórios, Serviços)
data/            SelecaoAtual, Desfazer, local/ (Room: entidades, DAOs, Transacao),
                 repository/ (implementações Room), clima/ (Open-Meteo), preferencias/, sessao/
domain/          MÓDULO :domain (domain/src/main/java/br/joao/agrosaldo/domain): model/, repository/ (interfaces),
                 financeiro/ (ResumoFinanceiro), clima/ (RegraDeChuva), conta/ (HashDeSenha, regras da conta e do bloqueio),
                 Relogio e Despachantes
feature/         inicio, lancamento, propriedade, clima, historico, conta, sessao (ViewModels globais da navegação);
                 cada uma com *Screen.kt + *ViewModel.kt (+ UiState e arquivos de partes da tela)
ui/theme/        Color.kt, Type.kt, Shape.kt, Espacos, Theme.kt, IconesAgro.kt (ícones desenhados), PreviasDaTela.kt (multiprévia)
```

Fora de `src/main`:

```
domain/src/test/       testes do domínio (JVM puro, `./gradlew :domain:test`)
domain/src/testFixtures/ fakes *Memoria das interfaces do domínio, DadosDeExemplo e relógios de teste
app/src/testFixtures/  AjustesAvisoMemoria e Semeador (dependem do app); reexporta os fakes do :domain
app/src/test/          testes JVM: ViewModels, dados, Worker, preferências, telas (Robolectric), capturas (Roborazzi), grafo do Hilt
app/src/sharedTest/    testes de DAO do Room: rodam no JVM (Robolectric, também no CI) e no aparelho (connectedAndroidTest)
app/schemas/           JSON do schema do Room (versão 3 em diante, um por versão; base dos testes de migração futuros)
config/detekt/         detekt.yml
.editorconfig          regras do ktlint
.github/workflows/android.yml  CI
```

**Convenção de nomes:** domínio em **português** (`Lancamento`, `Propriedade`, `aoSalvar`, `uiState`) com sufixos técnicos do Android em inglês (`Repository`, `Entity`, `Dao`, `Screen`, `ViewModel`, `UiState`). Mantenha esse padrão.

**Data classes:** são 65 (somando `app` e `:domain`), em 5 grupos: modelos do domínio, regras e resultados do domínio, entidades do Room, `UiState` das telas e tema/navegação. Modelos do domínio validam invariantes no `init` (`require`). As entidades do Room são separadas dos modelos e convertidas nos repositórios. As classes do tema são `@Immutable`.

---

## 5. Regras de código (o código atual já cumpre — não quebre)

1. **Nada de `!!`.** Trate nulo explicitamente.
2. **Nenhuma cor literal fora de `ui/theme`.** Use `MaterialTheme.colorScheme` ou `MaterialTheme.agro`.
3. **Nada de `GlobalScope`.** Use `viewModelScope`.
4. **O módulo `:domain` não importa Android**: nada de `android.*`, `androidx.*`, Room, Compose nem Hilt. Não é só convenção: é um módulo Kotlin JVM, e o build quebra se alguém tentar.
5. **ViewModel não importa Compose.**
6. **Dinheiro é `Long` em centavos.** Formatação só na UI (`core/util/Moeda.kt`). No banco, `INTEGER`, nunca `REAL`.
7. **Datas são `java.time`** (`LocalDate`, `YearMonth`, `Instant`). Nada de `LocalDate.now()` no `init` de modelo: foi o crash corrigido na R5. A regra de data futura (RN07) é checada ao salvar, com `Lancamento.dataDentroDoLimite`.
8. **Regra de negócio mora no domínio.** Saldo, ranking e afins são funções puras em `domain/` (ex.: `ResumoFinanceiro`), testadas sem Android. O ViewModel só junta dados e chama o domínio.
9. **Nada de estado global.** Estado compartilhado (ex.: `SelecaoAtual`, `Desfazer`) é `@Singleton` num módulo do Hilt e chega pelo construtor.
10. **Fakes e dados de exemplo ficam em `src/testFixtures`**, nunca em `src/main`: o APK não leva código de teste e o app começa sempre vazio, também no debug.
11. **Erros em coroutine:** use `tentar { }` (`core/util/Erros.kt`), que não engole `CancellationException`, e mostre a mensagem com `mensagemParaUsuario(...)`. Todo `Flow` lido do banco tem `.catch` e um campo de erro no UiState.
12. **Gravações que mexem em mais de uma tabela** vão numa transação (`data/local/Transacao.kt`).
13. **Composables sem estado** quando possível: recebem `uiState` e lambdas (ou uma classe `*Acoes` quando são muitas), não o ViewModel. Todo composable que desenha recebe `modifier`. Toda tela tem as prévias clara, escura e com fonte 2×: a multiprévia `@PreviasDaTela` (`ui/theme`).
14. **Use os componentes de `core/ui`** (`BotaoPrincipal`, `BotaoSecundario`, `BotaoDestaque`, `BotaoTonal`, `Carregando`, `EstadoVazio`, `EstadoErro`, `FaixaDeAviso`) e os espaçamentos de `Espacos`. Nada de botão refeito à mão nem `dp` solto para margem. Alvos de toque com no mínimo 48 dp e `heightIn`, não `height` fixo.
15. **Callback dentro de `LaunchedEffect`** passa por `rememberUpdatedState`.
16. **Propriedade privada com `_`** só existe se houver a pública correspondente (`_uiState` / `uiState`).
17. **Biblioteca nova** só com justificativa, e sempre pelo `libs.versions.toml`.
18. **Todo texto digitado tem limite**, valido no domínio (`require`) e cortado no ViewModel ao digitar ou colar: descrição do lançamento 200, nome da propriedade 80, cultura 60, nome da conta 80, e-mail 254, senha 128, área até 1.000.000 ha (e sempre finita), e 20 caracteres no texto da área e das coordenadas. O domínio é a última barreira: nunca confie só na tela. Registro gravado antes de um limite existir é lido ajustado a ele nos mapeadores (`data/local/Mapeadores.kt`), sem migração: uma linha antiga não pode esconder a lista nem fechar o app.
19. **Ler e apagar vão na mesma transação.** `LancamentoRepository.excluir` devolve um `LancamentoExcluido` (o lançamento e o instante da última gravação) ou `null`, e `restaurar` regrava com o mesmo instante: o "Desfazer" devolve exatamente o que saiu do banco, no mesmo lugar da lista.
20. **Tempo e threads vêm injetados:** hora do calendário por `java.time.Clock` (`LocalDate.now(relogio)`, nunca `now()` solto), duração por `RelogioMonotonico` e `Despachantes` no lugar de `Dispatchers.*`. Nos testes use `relogioEm`, `relogioPorMs`, `RelogioMutavel` e `DespachantesDeTeste` (`testFixtures`).

---

## 6. Segurança e privacidade — não negociável

- **Nenhum segredo no repositório.** `local.properties` fica no `.gitignore`.
- **A chave de assinatura (`C:\chaves\agrosaldo.jks`) NUNCA entra no Git, e nunca se gera uma chave nova**: com outra chave o APK não instala por cima do antigo e o produtor perde os dados.
- `.gitignore` precisa ter `*.jks`, `*.keystore`, `/app/release/`, `*.apk`, `*.aab`, `local.properties`.
- **Dados pessoais reais do produtor não entram em código, testes nem docs**: nada de CNPJ, endereço, telefone, e-mail, nome ou coordenadas da fazenda. Use dados fictícios.
- Senha: PBKDF2 (`domain/conta/HashDeSenha.kt`). Não enfraqueça.
- Bloqueio por senhas erradas usa relógio monotônico (`InstanteMonotonico`, imune a mudar a hora do celular) e contador por e-mail que sobrevive a reiniciar o app. Depois de reiniciar o aparelho, a espera recomeça inteira no boot novo: reiniciar e mexer na hora nunca a encurtam. Só sem o `BOOT_COUNT` do sistema o bloqueio cai para o relógio de parede.
- `FLAG_SECURE` na Activity **só com o bloqueio por digital ligado** (sem captura de tela nem miniatura em Recentes com valores). Com o bloqueio desligado, a captura é permitida: decisão de produto de out/2026. Não remova.
- O bloqueio por digital e os valores ocultos são ajustes **do aparelho**. Desligar o bloqueio pede a digital; excluir uma conta só volta esses ajustes ao padrão quando não sobra nenhuma conta.
- Backup criptografado/limitado. Não remova.
- Nada de `Log`/`println` com dado pessoal ou financeiro.
- Open-Meteo: o plano grátis é só para uso **não comercial**, e os dados são CC BY 4.0 (crédito com link na tela de Clima). Se o app ganhar anúncio ou cobrança, precisa do plano pago.

---

## 7. Regras de negócio

| Regra | O que diz | Onde |
|---|---|---|
| RN01 | Todo lançamento pertence a exatamente uma propriedade | `Lancamento` (`init`) |
| RN02 | Excluir propriedade apaga os lançamentos e a previsão dela (CASCADE), com "Desfazer" | entidades Room + `excluirComLancamentos` / `restaurar` |
| RN03 | Saldo do período = receitas − despesas | `domain/financeiro/ResumoFinanceiro.kt` |
| RN04 | Aviso de chuva (abaixo) | `domain/clima/RegraDeChuva.kt` |
| RN06 | Valor sempre positivo; o sinal vem do tipo | `Lancamento` (`init`) e `ResumoFinanceiro.totais` |
| RN07 | Data no máximo 12 meses no futuro, checada ao salvar | `Lancamento.dataDentroDoLimite` |

**RN04 — aviso de chuva:** avisa quando, nas próximas N horas (padrão 12), **chance máxima ≥ limite (padrão 60%) OU chuva somada ≥ limite (padrão 2 mm)**.

- Os limites são do usuário (tela "Aviso de chuva").
- A regra é a função pura `RegraDeChuva` e é toda coberta por teste.
- Os textos da chuva ficam num lugar só, `core/util/TextosDaChuva.kt`, usados pelo Início, pelo Clima e pela notificação.

---

## 8. Comandos

```bash
./gradlew ktlintCheck        # formatação (ktlintFormat arruma sozinho)
./gradlew detekt             # análise estática dos dois módulos; precisa dar zero achados (não há baseline)
./gradlew testDebugUnitTest  # testes JVM do app, incluindo Robolectric e capturas — rode SEMPRE antes de commitar
./gradlew :domain:test       # testes do módulo de domínio (rápidos, sem Android)
./gradlew assembleDebug      # compila o app inteiro
./gradlew lintDebug          # Android Lint (erro quebra o build)
```

- Os testes de DAO ficam em `app/src/sharedTest` e rodam nos dois lugares: no JVM com Robolectric (dentro do `testDebugUnitTest`, também no CI) e no aparelho (`connectedAndroidTest`, que precisa de emulador e **não roda na nuvem**). Se mexer em DAO, entidade ou migração, avise para rodar também no Android Studio.
- Mudou entidade do Room? Suba a versão do banco (a partir da 3), escreva a migração dela, exporte o schema e acrescente o teste de migração. Nunca use `fallbackToDestructiveMigration`.

### Detalhes de ferramenta (não "conserte" sem entender)

- **detekt** não é o plugin Gradle: o plugin 1.23 não roda no JDK 25. A tarefa `detekt` é um `JavaExec` que chama a CLI do detekt numa **JVM 21** separada (toolchain), com o Kotlin da configuração `detektCli` fixado em 2.0.21. Está no `app/build.gradle.kts`.
- **Robolectric** roda com `sdk = 36`, gráficos `NATIVE` e `application = android.app.Application` (não sobe o `AgroSaldoApp`). Os testes precisam dos argumentos de JVM `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` e `--add-opens=java.base/java.io=ALL-UNNAMED`, já configurados.
- **Roborazzi** (`CapturasDeTelaTest`) grava PNGs em `app/build/capturas`. Não compara pixels; só garante que a tela desenha.
- **ktlint:** a regra de Compose `preview-annotation-naming` está desligada no `.editorconfig`, porque a multiprévia segue o português do projeto (`@PreviasDaTela`). O detekt também ignora prévias privadas marcadas com ela.
- **AGP 9.5:** o `gradle.properties` tem `android.onlyEnableUnitTestForTheTestedBuildType=false`, porque a prévia do Compose que o AGP gera para o release exige os testes unitários ligados nele (o AGP 9 só os liga no debug). O CI continua rodando só o `testDebugUnitTest`.
- **Kotlin do app:** o AGP 9 traz o Kotlin embutido, mas usa a versão do plugin Kotlin que estiver no classpath do build. Os plugins `kotlin-jvm` e `kotlin-compose` do catálogo (versão `kotlin`) definem o Kotlin do app e do domínio juntos.
- **Testes no aparelho:** o `androidx.test:runner` (o `testInstrumentationRunner`) fica declarado no `androidTestImplementation`. Ele vinha por tabela com o Espresso; sem ele o `connectedAndroidTest` nem começa.
- **src/sharedTest:** entra nos source sets `test` e `androidTest` pelo `kotlin.directories` (o Kotlin embutido no AGP 9 compila pelo conjunto `kotlin`). O detekt isenta essa pasta como os outros diretórios de teste.
- **Módulo `:domain`:** o plugin é `org.jetbrains.kotlin.jvm` (no app o Kotlin vem embutido no AGP), com `java-test-fixtures`. O detekt do app analisa também as pastas do domínio. Propriedade pública de outro módulo não permite *smart cast*: copie para uma `val` local antes do `!= null`.
- **Hilt:** `hiltViewModel()` vem de `androidx.hilt.lifecycle.viewmodel.compose` (o pacote `navigation.compose` está obsoleto). Os formulários (`LancamentoFormViewModel`, `PropriedadeFormViewModel`) têm um construtor secundário `@Inject` que lê os argumentos da rota do `SavedStateHandle`; por isso as chaves do rascunho levam o prefixo `rascunho_` (o handle também recebe `tipo` e `id` da rota). O iniciador do WorkManager foi removido do manifesto: o `AgroSaldoApp` é o `Configuration.Provider` com a fábrica de Workers do Hilt. `GrafoDeDependenciasTest` sobe o grafo de verdade no Robolectric (`kspTest` + `hilt-android-testing`).
- Rodar o `lintDebug` em paralelo com `compileDebugAndroidTestKotlin` ou `assembleRelease` pode falhar por corrida nos arquivos gerados do Hilt; o conjunto do CI (`ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug`) roda limpo.
- O `testFixtures` precisa do Compose runtime no classpath (`testFixturesImplementation` do BOM e do runtime), por causa do compilador do Compose.

### CI (`.github/workflows/android.yml`)

- Roda `ktlintCheck detekt testDebugUnitTest :domain:test assembleDebug lintDebug` em push na `prod` e em todo PR. É sempre o conjunto completo: não há variável para trocar as tarefas.
- Os erros viram anotações no check run (é por elas que dá para ler a falha sem abrir o log).
- As capturas do Roborazzi são publicadas na branch **`ci/capturas`** (não apague). Por isso o workflow tem `permissions: contents: write`.
- O `gradlew` tem o bit de execução no Git (`git update-index --chmod=+x gradlew`), e o CI ainda roda `chmod +x gradlew` antes do Gradle.
- O nome do ramo entra no commit das capturas por variável de ambiente (`RAMO`), nunca colado dentro do script.

---

## 9. Como trabalhar neste repo

1. **Plano primeiro.** Antes de editar, apresente o plano: arquivos que vão mudar e por quê. Espere o OK.
2. **Uma tarefa por branch** (ex.: `rodada/r9-play-store`, `fix/...`). Nada direto na `prod` (a principal) sem o João pedir.
3. **Commits pequenos** em Conventional Commits, em português: `refactor(inicio): usa ResumoFinanceiro do domínio`.
4. **Autor dos commits: sempre o João.** `user.name "Morettys2"` e `user.email "m0rettyy20@gmail.com"`, **sem** `Co-Authored-By` do Claude nem outro trailer de atribuição. Ele não quer que o histórico pareça feito pelo Claude.
5. **Teste junto:** cada regra nova ganha teste. Cada bug corrigido ganha um teste que falharia antes.
6. **Antes do PR**, o CI precisa ficar verde (ktlint, detekt, testes, build e lint) e sem warning novo do Kotlin.
7. **Abra o PR** com o resumo e **não faça merge** sem o OK: o João revisa e testa no celular primeiro.
8. Se algo depende de decisão de produto (texto, comportamento visível ao produtor), **pergunte** em vez de assumir.
9. Não recrie README nem CHANGELOG. O arquivo `docs/DOCUMENTACAO_TECNICA.mdx` é a documentação técnica para os devs do grupo e este arquivo continua sendo a referência de regras e decisões.
10. **Branches:** `prod` é a principal (padrão no GitHub): todo trabalho parte dela e volta para ela por PR, e o CI roda em push nela e em todo PR. `desenvolvimento` é a antiga `main`, congelada como backup (tem as migrações antigas do banco e a documentação técnica de 15/10): não receba commits nela. O banco parte da versão 3 na `prod`; a próxima migração parte da 3.
---

## 10. Histórico e próximos passos

### Feito

- **R1–R4:** app base (contas, propriedades, lançamentos, histórico, clima, aviso de chuva).
- **R5 (1.5):** bugs. Crash de lançamento futuro com relógio atrasado (RN07 saiu do `init`), formulário de propriedade travado contra toque duplo e NaN, exclusão/desfazer em transação, notificação com `singleTop`, uma busca de clima por vez.
- **Auditoria de código (out/2026), toda corrigida:** A1 bloqueio imune ao relógio; A2 centavos e arredondamento nas listas; A3 crash ao excluir propriedade durante o download da previsão; M1–M12 (contador por e-mail, capa de bloqueio cobrindo diálogos, aceite LGPD sem atalho, `FLAG_SECURE`, backup, aviso registrado só depois de notificar, notificação abrindo a propriedade certa, Clima atualizando ao voltar, permissão de notificação negada duas vezes, teclado com sinal de menos, área "1.000", rascunho sobrevivendo à morte do processo); B1–B27 menores.
- **R6:** regras no domínio (`ResumoFinanceiro`, `propriedadeAtual`), `SelecaoAtual` injetada, container `Dependencias`, fakes para `testFixtures` (app começa vazio), `.catch` nas leituras do banco.
- **R7:** componentes compartilhados de botão e estado, `Espacos`, um seletor de propriedade só (48 dp, com role), acessibilidade (aba ≥ 11 sp, roles, previews 2×), textos unificados.
- **Transições de tela (branch `prod`):** o crossfade padrão de 700 ms do Navigation Compose deixava a tela antiga "fantasma" por baixo da nova. Agora trocar de aba é instantâneo e as demais telas deslizam, com a antiga sumindo em 90 ms (`core/navigation/TransicoesDeTela.kt`).
- **Documentação técnica em MDX (`docs/`)** para os devs do grupo, e branch `prod` como principal (a antiga `main` virou `desenvolvimento`, backup).
- **Detekt sem baseline:** os 115 achados antigos foram resolvidos (constantes nomeadas, `aoSalvar` e `verificar` quebrados em funções menores, `Desfazer.desfazerExclusao`) ou justificados no `detekt.yml` (testes que capturam exceção de propósito, guardas, telas Compose) ou com `@Suppress` comentado no ponto exato. Achado novo = corrigir, não baselinar.
- **Arquitetura profissional (branch `refactor/arquitetura-profissional`):** relógio, relógio monotônico e dispatchers injetáveis; DI manual trocada por Hilt (módulos em `di/`, `@HiltViewModel`, `@HiltWorker`); `LimpezaDaConta` saiu de dentro do container.
- **Auditoria de arquitetura (out/2026):** MVVM conferido de ponta a ponta. Corrigidos: Flow do nome do usuário sem tratamento de erro no Início; transação obrigatória nos repositórios (a `TransacaoDireta` foi para o `testFixtures`); despachante de E/S injetado na chamada de rede; nenhum valor padrão de relógio, despachantes ou `Desfazer` em produção (os testes passam relógio fixo); `FontePrevisao` pelo Hilt; `PropriedadeRepository.excluir(id)` sem uso removido; `SessaoViewModel` e `PrivacidadeViewModel` em `feature/sessao`. Pendente, sem pressa: `propriedades.usuario_id` anulável e `adotarPropriedadesSemDono` são legado dos dados de exemplo (mexer exige a migração 4).
- **R8:** arquivos grandes quebrados, `LancamentoFormAcoes`, ktlint + detekt + Lint `abortOnError`, CI, testes de tela (Robolectric), capturas (Roborazzi), testes do Worker e das preferências.
- **Correções da auditoria de código (28/10/2026, branch `fix/auditoria-2026-10-28`, relatório em `docs/AUDITORIA_2026-10-28.md`):** dados reais do produtor fora do projeto (o histórico antigo do Git ainda os tem: não tornar o repositório público sem limpar); leitura resiliente (registros antigos lidos ajustados aos limites, `.catch` em todas as leituras do banco, `tentar` nas chamadas suspensas); bloqueio do aparelho que não cai ao excluir uma conta nem ao reiniciar o celular; limites cortados ao digitar e relógio sempre injetado; DAOs testados também no JVM; ano mês a mês e comparativo no `ResumoFinanceiro`; `@PreviasDaTela` e `Espacos` em todas as telas; cultura vazia gravada vazia; desfazer com a ordem e a seleção originais; limpeza de build, CI, Lint e código sem uso. As versões das bibliotecas ficaram para um PR próprio (abaixo). Em 02/11/2026, na mesma branch: teste do `AgendaWorkManager`, fakes com sessão e posse e o `androidx.test:runner` declarado (sumira junto com o Espresso e o `connectedAndroidTest` não começava).
- **Bibliotecas (02/11/2026, branch `chore/atualiza-dependencias`, DEP-01):** AGP 9.5.0 com Gradle 9.8.2, Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00, WorkManager 2.12.0, core-ktx 1.19.1, splashscreen 1.2.0, Roborazzi 1.77.0 e org.json 20260814. Os testes de tela usam o `createComposeRule` do pacote `junit4.v2`. Lint sem nenhum aviso.

### R9 — Play Store (futuro, sem data)

- Política de privacidade num link público (GitHub Pages): responsável e contato (LGPD), o IP que a Open-Meteo recebe, como apagar os dados, o backup.
- Trocar `ACCESS_FINE_LOCATION` por `COARSE` e arredondar as coordenadas enviadas para 0,02°.
- Release: AAB (`bundleRelease`), Play App Signing (a chave atual vira só a *upload key*), senhas da chave em `~/.gradle/gradle.properties`, testar o build de release com R8/minify, `localeFilters += "pt-rBR"`.
- Play Console: Data safety (localização, criptografada em trânsito), declaração de recursos financeiros ("não oferece"), público adulto, classificação indicativa.
- Conta pessoal nova: teste fechado com **12 testadores por 14 dias seguidos** antes de pedir produção (planejar umas 3 semanas).
- Desejável: exportar os dados (portabilidade LGPD) e mover os textos para `strings.xml`.

### Fora do escopo (decidido em out/2026)

- **API própria (NestJS):** o app continua 100% local. Sem sincronização, sem vários celulares e sem "esqueci minha senha" por e-mail. Os ids já são UUID e as entidades têm `atualizado_em`, então dá para retomar se um dia fizer sentido.
- **Controle de irrigação (pivô):** não será implementado. A previsão serve só para recomendar adiar a irrigação ("Dá para adiar o pivô").
