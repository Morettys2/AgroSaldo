# CLAUDE.md — AgroSaldo

Contexto permanente do projeto, e a **única** documentação dentro do repositório. Leia inteiro antes de qualquer alteração.

A documentação técnica completa (pasta por pasta, bibliotecas, design) e a lista de todas as data classes ficam fora do repo, em dois documentos no Claude ("AgroSaldo — Documentação técnica" e "AgroSaldo — Data classes"). Quando o código mudar de forma relevante, avise que eles precisam ser atualizados.

---

## 1. O projeto

App Android nativo para o pequeno produtor rural controlar **receitas e despesas por propriedade** (sítio) e receber **aviso de chuva**, que recomenda adiar a irrigação do pivô.

- Projeto acadêmico (UCE, Engenharia de Software, UNIFAE) com usuário real: o pai do João, produtor de Itobi/SP, que já usa o APK.
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
| Linguagem / UI | Kotlin 2.2.10, Jetpack Compose (BOM 2026.02.01, Material 3) |
| Build | AGP 9.3.2 (Kotlin embutido no AGP), Gradle 9.5.0, Version Catalog (`gradle/libs.versions.toml`), KSP |
| JDK | Gradle roda no JDK 25 (toolchain do daemon); o código compila para Java 11 |
| Arquitetura | MVVM + Repository, com camada `domain` pura (Kotlin sem Android) |
| Banco | Room 2.8.5, banco versão 3 (tabelas `usuarios`, `propriedades`, `lancamentos`, `previsoes`), migrações em `data/local` e schemas em `app/schemas` |
| DI | **Manual**: `di/Dependencias.kt`, criado e guardado pelo `AgroSaldoApp` e lido nas `Factory` de cada ViewModel via `CreationExtras.dependencias` (`APPLICATION_KEY`). **Sem** Hilt/Koin, por decisão: é projeto de aprendizado |
| Rede | `HttpURLConnection` + `org.json` (`data/clima/FontePrevisao.kt`). Só a Open-Meteo, por HTTPS |
| Preferências | `SharedPreferences` (`data/preferencias`, `data/sessao`) |
| Segundo plano | WorkManager 2.11 (`aviso/VerificacaoChuvaWorker.kt`) |
| Outros | Navigation Compose, Biometric, SplashScreen, fonte Plus Jakarta Sans embutida |
| Testes | JUnit 4, kotlinx-coroutines-test, Robolectric 4.17, Roborazzi 1.76, work-testing; fakes em memória em `src/testFixtures` (sem MockK nem Turbine) |
| Qualidade | ktlint (plugin 14.2, estilo `android_studio`, regras de Compose 0.6.8), detekt 1.23.8 com baseline, Android Lint com `abortOnError` |

`minSdk 26`, `compileSdk` e `targetSdk` 37.

---

## 4. Estrutura e nomes

Pacote: `br.joao.agrosaldo`.

```
AgroSaldoApp.kt  Application: cria o Dependencias e agenda o Worker
MainActivity.kt  única Activity; FLAG_SECURE, capa de bloqueio, intent da notificação
aviso/           Worker, notificação e agendamento do aviso de chuva (Android)
core/            navigation/ (AppNavegacao, Navegador, GrafosDeNavegacao, BarraInferior, Rotas),
                 ui/ (componentes "Safra": botões, estados, seletor, cabeçalho),
                 util/ (Moeda, datas, Erros, TextosDaChuva), localizacao/, seguranca/
di/              Dependencias (container da DI manual)
data/            SelecaoAtual, Desfazer, local/ (Room: entidades, DAOs, migrações, Transacao),
                 repository/ (implementações Room), clima/ (Open-Meteo), preferencias/, sessao/
domain/          model/, repository/ (interfaces), financeiro/ (ResumoFinanceiro), clima/ (RegraDeChuva),
                 conta/ (HashDeSenha, regras da conta e do bloqueio)
feature/         inicio, lancamento, propriedade, clima, historico, conta;
                 cada uma com *Screen.kt + *ViewModel.kt (+ UiState e arquivos de partes da tela)
ui/theme/        Color.kt, Type.kt, Shape.kt, Espacos, Theme.kt, IconesAgro.kt (ícones desenhados)
```

Fora de `src/main`:

```
app/src/testFixtures/  fakes *Memoria, DadosDeExemplo, Semeador (usados pelos dois tipos de teste)
app/src/test/          testes JVM: domínio, ViewModels, Worker, preferências, telas (Robolectric), capturas (Roborazzi)
app/src/androidTest/   testes instrumentados: DAOs e migrações do Room (precisam de emulador)
app/schemas/           JSON dos schemas do Room (um por versão; usado nos testes de migração)
config/detekt/         detekt.yml
app/detekt-baseline.xml achados antigos do detekt (121) que o CI não cobra
.editorconfig          regras do ktlint
.github/workflows/android.yml  CI
```

**Convenção de nomes:** domínio em **português** (`Lancamento`, `Propriedade`, `aoSalvar`, `uiState`) com sufixos técnicos do Android em inglês (`Repository`, `Entity`, `Dao`, `Screen`, `ViewModel`, `UiState`). Mantenha esse padrão.

**Data classes:** são 58 no `src/main`, em 5 grupos: modelos do domínio, regras e resultados do domínio, entidades do Room, `UiState` das telas e tema/navegação. Modelos do domínio validam invariantes no `init` (`require`). As entidades do Room são separadas dos modelos e convertidas nos repositórios. As classes do tema são `@Immutable`.

---

## 5. Regras de código (o código atual já cumpre — não quebre)

1. **Nada de `!!`.** Trate nulo explicitamente.
2. **Nenhuma cor literal fora de `ui/theme`.** Use `MaterialTheme.colorScheme` ou `MaterialTheme.agro`.
3. **Nada de `GlobalScope`.** Use `viewModelScope`.
4. **`domain/` não importa Android**: nada de `android.*`, `androidx.*`, Room nem Compose.
5. **ViewModel não importa Compose.**
6. **Dinheiro é `Long` em centavos.** Formatação só na UI (`core/util/Moeda.kt`). No banco, `INTEGER`, nunca `REAL`.
7. **Datas são `java.time`** (`LocalDate`, `YearMonth`, `Instant`). Nada de `LocalDate.now()` no `init` de modelo: foi o crash corrigido na R5. A regra de data futura (RN07) é checada ao salvar, com `Lancamento.dataDentroDoLimite`.
8. **Regra de negócio mora no domínio.** Saldo, ranking e afins são funções puras em `domain/` (ex.: `ResumoFinanceiro`), testadas sem Android. O ViewModel só junta dados e chama o domínio.
9. **Nada de estado global.** Estado compartilhado (ex.: `SelecaoAtual`) é criado no `Dependencias` e passado no construtor.
10. **Fakes e dados de exemplo ficam em `src/testFixtures`**, nunca em `src/main`: o APK não leva código de teste e o app começa sempre vazio, também no debug.
11. **Erros em coroutine:** use `tentar { }` (`core/util/Erros.kt`), que não engole `CancellationException`, e mostre a mensagem com `mensagemParaUsuario(...)`. Todo `Flow` lido do banco tem `.catch` e um campo de erro no UiState.
12. **Gravações que mexem em mais de uma tabela** vão numa transação (`data/local/Transacao.kt`).
13. **Composables sem estado** quando possível: recebem `uiState` e lambdas (ou uma classe `*Acoes` quando são muitas), não o ViewModel. Todo composable que desenha recebe `modifier`. Toda tela tem `@Preview` claro, escuro e com fonte 2×.
14. **Use os componentes de `core/ui`** (`BotaoPrincipal`, `BotaoSecundario`, `BotaoDestaque`, `BotaoTonal`, `Carregando`, `EstadoVazio`, `EstadoErro`, `FaixaDeAviso`) e os espaçamentos de `Espacos`. Nada de botão refeito à mão nem `dp` solto para margem. Alvos de toque com no mínimo 48 dp e `heightIn`, não `height` fixo.
15. **Callback dentro de `LaunchedEffect`** passa por `rememberUpdatedState`.
16. **Propriedade privada com `_`** só existe se houver a pública correspondente (`_uiState` / `uiState`).
17. **Biblioteca nova** só com justificativa, e sempre pelo `libs.versions.toml`.

---

## 6. Segurança e privacidade — não negociável

- **Nenhum segredo no repositório.** `local.properties` fica no `.gitignore`.
- **A chave de assinatura (`C:\chaves\agrosaldo.jks`) NUNCA entra no Git, e nunca se gera uma chave nova**: com outra chave o APK não instala por cima do antigo e o produtor perde os dados.
- `.gitignore` precisa ter `*.jks`, `*.keystore`, `/app/release/`, `*.apk`, `*.aab`, `local.properties`.
- **Dados pessoais reais do produtor não entram em código, testes nem docs**: nada de CNPJ, endereço, telefone, e-mail, nome ou coordenadas da fazenda. Use dados fictícios.
- Senha: PBKDF2 (`domain/conta/HashDeSenha.kt`). Não enfraqueça.
- Bloqueio por senhas erradas usa relógio monotônico (`InstanteMonotonico`, imune a mudar a hora do celular) e contador por e-mail que sobrevive a reiniciar o app.
- `FLAG_SECURE` na Activity (sem miniatura em Recentes com valores) e backup criptografado/limitado. Não remova.
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
| RN06 | Valor sempre positivo; o sinal vem do tipo | `Lancamento` (`init`, `valorComSinal`) |
| RN07 | Data no máximo 12 meses no futuro, checada ao salvar | `Lancamento.dataDentroDoLimite` |

**RN04 — aviso de chuva:** avisa quando, nas próximas N horas (padrão 12), **chance máxima ≥ limite (padrão 60%) OU chuva somada ≥ limite (padrão 2 mm)**.

- Os limites são do usuário (tela "Aviso de chuva").
- A regra é a função pura `RegraDeChuva` e é toda coberta por teste.
- Os textos da chuva ficam num lugar só, `core/util/TextosDaChuva.kt`, usados pelo Início, pelo Clima e pela notificação.

---

## 8. Comandos

```bash
./gradlew ktlintCheck        # formatação (ktlintFormat arruma sozinho)
./gradlew detekt             # análise estática; só cobra código novo (baseline)
./gradlew testDebugUnitTest  # testes JVM, incluindo Robolectric e capturas — rode SEMPRE antes de commitar
./gradlew assembleDebug      # compila o app inteiro
./gradlew lintDebug          # Android Lint (erro quebra o build)
./gradlew detektBaseline     # regenera o baseline depois de limpar achados antigos
```

- Os testes instrumentados (`connectedAndroidTest`: DAOs e migrações) precisam de emulador e **não rodam na nuvem**. Se mexer em DAO, entidade ou migração, avise para rodar no Android Studio.
- Mudou entidade do Room? Suba a versão do banco, escreva a migração, exporte o schema e acrescente o teste de migração.

### Detalhes de ferramenta (não "conserte" sem entender)

- **detekt** não é o plugin Gradle: o plugin 1.23 não roda no JDK 25. A tarefa `detekt` é um `JavaExec` que chama a CLI do detekt numa **JVM 21** separada (toolchain), com o Kotlin da configuração `detektCli` fixado em 2.0.21. Está no `app/build.gradle.kts`.
- **Robolectric** roda com `sdk = 36`, gráficos `NATIVE` e `application = android.app.Application` (não sobe o `AgroSaldoApp`). Os testes precisam dos argumentos de JVM `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` e `--add-opens=java.base/java.io=ALL-UNNAMED`, já configurados.
- **Roborazzi** (`CapturasDeTelaTest`) grava PNGs em `app/build/capturas`. Não compara pixels; só garante que a tela desenha.
- O `testFixtures` precisa do Compose runtime no classpath (`testFixturesImplementation` do BOM e do runtime), por causa do compilador do Compose.

### CI (`.github/workflows/android.yml`)

- Roda `ktlintCheck detekt testDebugUnitTest assembleDebug lintDebug` em push na `main` e em todo PR.
- Os erros viram anotações no check run (é por elas que dá para ler a falha sem abrir o log).
- As capturas do Roborazzi são publicadas na branch **`ci/capturas`** (não apague). Por isso o workflow tem `permissions: contents: write`.
- O `gradlew` precisa do bit de execução no Git (`git update-index --chmod=+x gradlew`); sem ele o CI falha com exit 126.

---

## 9. Como trabalhar neste repo

1. **Plano primeiro.** Antes de editar, apresente o plano: arquivos que vão mudar e por quê. Espere o OK.
2. **Uma tarefa por branch** (ex.: `rodada/r9-play-store`, `fix/...`). Nada direto na `main` sem o João pedir.
3. **Commits pequenos** em Conventional Commits, em português: `refactor(inicio): usa ResumoFinanceiro do domínio`.
4. **Autor dos commits: sempre o João.** `user.name "Morettys2"` e `user.email "m0rettyy20@gmail.com"`, **sem** `Co-Authored-By` do Claude nem outro trailer de atribuição. Ele não quer que o histórico pareça feito pelo Claude.
5. **Teste junto:** cada regra nova ganha teste. Cada bug corrigido ganha um teste que falharia antes.
6. **Antes do PR**, o CI precisa ficar verde (ktlint, detekt, testes, build e lint) e sem warning novo do Kotlin.
7. **Abra o PR** com o resumo e **não faça merge** sem o OK: o João revisa e testa no celular primeiro.
8. Se algo depende de decisão de produto (texto, comportamento visível ao produtor), **pergunte** em vez de assumir.
9. Não recrie README, CHANGELOG nem `docs/`: este arquivo é a referência do repo, por decisão do João.

---

## 10. Histórico e próximos passos

### Feito

- **R1–R4:** app base (contas, propriedades, lançamentos, histórico, clima, aviso de chuva).
- **R5 (1.5):** bugs. Crash de lançamento futuro com relógio atrasado (RN07 saiu do `init`), formulário de propriedade travado contra toque duplo e NaN, exclusão/desfazer em transação, notificação com `singleTop`, uma busca de clima por vez.
- **Auditoria de código (out/2026), toda corrigida:** A1 bloqueio imune ao relógio; A2 centavos e arredondamento nas listas; A3 crash ao excluir propriedade durante o download da previsão; M1–M12 (contador por e-mail, capa de bloqueio cobrindo diálogos, aceite LGPD sem atalho, `FLAG_SECURE`, backup, aviso registrado só depois de notificar, notificação abrindo a propriedade certa, Clima atualizando ao voltar, permissão de notificação negada duas vezes, teclado com sinal de menos, área "1.000", rascunho sobrevivendo à morte do processo); B1–B27 menores.
- **R6:** regras no domínio (`ResumoFinanceiro`, `propriedadeAtual`), `SelecaoAtual` injetada, container `Dependencias`, fakes para `testFixtures` (app começa vazio), `.catch` nas leituras do banco.
- **R7:** componentes compartilhados de botão e estado, `Espacos`, um seletor de propriedade só (48 dp, com role), acessibilidade (aba ≥ 11 sp, roles, previews 2×), textos unificados.
- **R8:** arquivos grandes quebrados, `LancamentoFormAcoes`, ktlint + detekt + Lint `abortOnError`, CI, testes de tela (Robolectric), capturas (Roborazzi), testes do Worker e das preferências.

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
