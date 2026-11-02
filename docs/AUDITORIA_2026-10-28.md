# Auditoria de código — AgroSaldo (`prod`, 28/10/2026)

> **Atualização (28/10/2026):** os achados foram tratados na branch `fix/auditoria-2026-10-28`. O resultado de cada um está na coluna **Status** da seção 3, e os commits e conferências na seção 8.
>
> **Atualização (02/11/2026):** os dois parciais de teste (TST-01 e FAKE-01) foram fechados, e os testes de banco rodaram no emulador. Só o DEP-01 segue parcial, por decisão: as versões vão num PR próprio.

> **Commit auditado:** `a735a1b` (docs: roadmap com os próximos passos do app), branch `prod`.
> **Modo:** somente leitura. Nenhum arquivo de código foi alterado; este relatório é o único arquivo criado.
> **Escopo:** `app/src/main`, `domain/src/main`, todos os testes e `testFixtures`, arquivos de build, manifesto, `res/xml`, `detekt.yml`, `.editorconfig`, CI e as três docs em `docs/`.

---

## 1. Resumo executivo

O código está em bom estado. As ferramentas do CI passam limpas (ktlint, detekt com zero achados, 499 testes, build e Lint sem erro). O `:domain` não importa nada de Android, não há `!!`, `GlobalScope`, `Log` nem cor literal, e o hash de senha está correto. Os problemas se concentram em **dois pontos**: o app não resiste bem a erro de leitura do banco, e a documentação promete garantias de segurança que o código só cumpre em parte.

**Skills de review usadas:**
- `engineering:code-review`: dimensões de segurança, performance, correção e manutenção.
- `security-review`: método em 3 fases e filtro de falso positivo com confiança ≥ 8. O diff da `prod` estava vazio, então apliquei o método ao código inteiro.
- `engineering:tech-debt`: priorização do plano.
- `engineering:testing-strategy`: lacunas de teste.

As partes das skills que pedem sub-agentes ou comentários em PR não foram usadas: a auditoria foi feita direto e sem escrever em lugar nenhum.

**Achados: 22.** 🔴 1 · 🟠 4 · 🟡 8 · 🔵 9. Dois estão marcados **"A confirmar"**.

**Os 3 mais urgentes:**
1. **BUG-01 + FLW-01:** um registro antigo com texto acima dos limites criados em 18/10 faz a lista inteira sumir. Se for uma propriedade, **o app fecha ao abrir**, porque o Início tem coletores do banco sem `.catch`.
2. **SEG-01:** excluir *qualquer* conta desliga o bloqueio por digital do aparelho, sem pedir a digital. Basta criar uma conta e excluí-la para tirar a proteção do produtor.
3. **SEG-02 (A confirmar na época):** um nome de pessoa e coordenadas com 8 casas decimais aparecem em testes e prévias. Depois se confirmou que eram o nome real do produtor e coordenadas reais da propriedade, o que quebra a regra de dados pessoais (`CLAUDE.md` §6).

> **Calibração de severidade:** quebra de regra do `CLAUDE.md` **com** efeito para o produtor ficou em 🟠; quebra **sem** efeito visível hoje ficou em 🟡.

---

## 2. Resultado das ferramentas

| Ferramenta | Resultado | Observação |
| --- | --- | --- |
| `ktlintCheck` | ✅ passou | `:app` e `:domain` |
| `detekt` | ✅ passou | 0 achados, sem baseline |
| `testDebugUnitTest` | ✅ passou | 419 testes, 0 falhas. Rodado de novo com `--rerun`, porque a 1ª execução veio do cache do Gradle |
| `:domain:test` | ✅ passou | 80 testes, 0 falhas (também com `--rerun`) |
| `assembleDebug` | ✅ passou | |
| `lintDebug` | ✅ passou | 0 erros e **27 avisos** (ver LINT-01, MORTO-01 e DEP-01) |
| Avisos do Kotlin | ✅ 0 | `:domain:compileKotlin` e `:app:compileDebugKotlin` recompilados com `--rerun` (fontes de produção) |
| `assembleRelease` (extra) | ✅ compilou | R8 ligado; gera um APK não assinado de 2,8 MB. **Não prova que roda:** falta abrir o build de release num aparelho (já previsto no R9) |
| `connectedAndroidTest` | ⏸️ não rodou | Precisa de emulador (15 testes de DAO) |
| Vulnerabilidades (CVE) | ⏸️ não verificado | Sem acesso a uma base de CVE; só o aviso de versões do Lint |

Ambiente: Windows 11, Gradle 9.5.0 com o daemon no JDK 25, execução em 28/10/2026. Depois de tudo, o `git status` mostrava só as mudanças em `.idea/` que já existiam antes da auditoria.

---

## 3. Tabela de achados

| ID | Sev. | Área | Arquivo | Resumo | Status |
| --- | --- | --- | --- | --- | --- |
| BUG-01 | 🔴 | 1 · Bugs / 4 · Dados | `data/local/Mapeadores.kt:20` | Linha antiga fora dos limites novos esconde a lista inteira e, em propriedades, fecha o app | ✅ corrigido: mapeadores leem registros antigos ajustados aos limites, sem migração |
| FLW-01 | 🟠 | 3 · Flow | `feature/inicio/InicioViewModel.kt:135` | 4 coletores do banco sem `.catch`: erro de leitura derruba o app | ✅ corrigido, e também o `precisaNovoAceite` do `SessaoViewModel`, achado durante a correção |
| SEG-01 | 🟠 | 5 · Segurança | `data/LimpezaDaConta.kt:48` | Excluir qualquer conta desliga o bloqueio por digital do aparelho sem pedir digital | ✅ corrigido: os ajustes do aparelho só voltam ao padrão quando não sobra nenhuma conta |
| SEG-02 | 🟠 | 5 · Segurança | testes e prévias (vários) | **A confirmar:** possível nome e coordenadas reais do produtor no código | ✅ corrigido no código, nos testes, nas docs e nas capturas; o histórico antigo do Git ainda tem os dados (não reescrito, por decisão) |
| SEG-03 | 🟠 | 5 · Segurança | `MainActivity.kt:105` | **A confirmar (produto):** `FLAG_SECURE` só com bloqueio ligado; o `CLAUDE.md` diz que é sempre | ✅ decisão: manter só com o bloqueio ligado; `CLAUDE.md`, doc técnica e banca corrigidos |
| FLW-02 | 🟡 | 3 · Coroutines | `feature/lancamento/LancamentoFormViewModel.kt:155` | Chamadas suspensas sem `tentar { }` em 11 pontos de 9 ViewModels | ✅ corrigido nos 11 pontos e no `SessaoViewModel` (`validarSessao` e abertura do Clima) |
| SEG-04 | 🟡 | 5 · Segurança | `domain/.../conta/ContaService.kt:310` | Depois de reiniciar o celular, adiantar a hora encerra o bloqueio por tentativas | ✅ corrigido: depois de reiniciar, a espera recomeça inteira no boot novo |
| ENT-01 | 🟡 | 5 · Entrada | `feature/conta/CadastroViewModel.kt:51` | Nome, e-mail e senha não são cortados ao digitar; `Usuario.init` sem limite de tamanho | ✅ corrigido |
| REL-01 | 🟡 | 1 · Datas | `feature/*/*ViewModel.kt` (UiState) | `LocalDate.now()` sem `Clock` em 5 UiState e 2 pontos de UI | ✅ corrigido; o único `now()` de produção é o do `RelogioDoAparelho` |
| ARQ-01 | 🟡 | 2 · Arquitetura | `feature/historico/HistoricoViewModel.kt:119` | Melhor/pior mês e comparativo de propriedades calculados no ViewModel | ✅ corrigido; `lerArea` e `ficaNoBrasil` ficaram no ViewModel, como a auditoria admitia |
| TST-01 | 🟡 | 8 · Testes | `app/src/androidTest/...` | SQL real só roda no emulador (nunca no CI); faltam testes para FLW-01, SEG-01 e BUG-01 | ✅ corrigido: DAOs no JVM, todos os testes da tabela e o `AgendaWorkManager` (a fila no WorkManager de verdade, no Robolectric, sem `work-testing`) |
| UI-01 | 🟡 | 6 · Compose | 13 arquivos de tela | 13 de 17 telas sem `@Preview` escuro; 8 sem fonte 2× | ✅ corrigido: `@PreviasDaTela` nas 17 telas |
| BLD-01 | 🟡 | 9 · Build | `app/build.gradle.kts:59` | Sobras de baseline: ktlint aponta para arquivo inexistente; detekt usa baseline se o arquivo aparecer | ✅ corrigido |
| DAD-01 | 🔵 | 4 · Dados | `data/repository/PropriedadeRepositoryRoom.kt:82` | "Desfazer" não devolve `atualizado_em` nem a propriedade selecionada | ✅ corrigido: `LancamentoExcluido`, instantes originais e a seleção de volta |
| BUG-02 | 🔵 | 1 · Bugs | `feature/propriedade/PropriedadeFormViewModel.kt:315` | Cultura vazia vira o texto "Não informada" gravado no banco | ✅ corrigido: cultura em branco gravada vazia; "Não informada" só na tela |
| FAKE-01 | 🔵 | 8 · Testes | `domain/src/testFixtures/.../PropriedadeRepositoryMemoria.kt:21` | Fakes não ordenam nem conferem sessão/posse como o Room | ✅ corrigido: o fake ordena como o Room e, com uma sessão, confere sessão e posse; o `ContratoDosRepositoriosTest` roda os mesmos cenários no Room e nos fakes |
| CI-01 | 🔵 | 9 · CI | `.github/workflows/android.yml:39` | `vars.GRADLE_TASKS` troca o que o CI roda; `github.head_ref` interpolado no `run:` | ✅ corrigido |
| UI-02 | 🔵 | 6 · Compose | `feature/lancamento/LancamentoListaScreen.kt:201` | `dp` soltos para margem em vez de `Espacos` | ✅ corrigido; ajustes de até 2 dp onde 14 e 18 dp viraram 12 e 20 dp |
| LINT-01 | 🔵 | 9 · Qualidade | `feature/lancamento/LancamentoFormViewModel.kt:107` | Avisos do Lint: `VisibleForTests`, `InlinedApi`, `RedundantLabel` | ✅ corrigido: Lint de 27 para 10 avisos, só de versão |
| DEP-01 | 🔵 | 9 · Dependências | `gradle/libs.versions.toml` | Dependências desatualizadas; nome de versão enganoso; artefato do Hilt indireto | ⚠️ parcial, por decisão: só o catálogo (`coroutines` e o artefato do Hilt); as versões não foram atualizadas |
| MORTO-01 | 🔵 | 11 · Manutenção | vários | Funções, recursos, dependências e arquivos `.idea` sem uso | ✅ corrigido |
| DOC-01 | 🔵 | 10 · Documentação | `CLAUDE.md`, comentários | Divergências menores e comentários desatualizados (detalhe na seção 5) | ✅ corrigido, com toda a tabela da seção 5 |

Caminhos curtos: `feature/...`, `data/...`, `core/...` e `di/...` ficam em `app/src/main/java/br/joao/agrosaldo/`; `domain/...` fica em `domain/src/main/java/br/joao/agrosaldo/domain/`.

---

## 4. Detalhe dos achados

### Área 1 — Bugs e correção

#### BUG-01 · 🔴 Linha antiga fora dos limites novos esconde a lista e pode fechar o app

- **Arquivos:** `app/src/main/java/br/joao/agrosaldo/data/local/Mapeadores.kt:20` (`PropriedadeEntity.paraDominio`) e `:51` (`LancamentoEntity.paraDominio`); `data/repository/PropriedadeRepositoryRoom.kt:42`; `data/repository/LancamentoRepositoryRoom.kt:34`. Os limites ficam em `domain/.../model/Propriedade.kt:21-25` e `domain/.../model/Lancamento.kt:64`.
- **O que está errado:** os mapeadores montam o modelo do domínio, que valida tudo com `require` no `init`. Os limites de descrição (200), nome da propriedade (80), cultura (60) e área (até 1.000.000 ha) entraram em 18/10/2026 (commit `a092fde`), quando o produtor já usava o app. Basta **uma** linha antiga fora do limite para `entidades.map { it.paraDominio() }` lançar `IllegalArgumentException`, e aí nenhuma linha da lista chega à tela.
- **Cenário:**
  1. Numa versão anterior a 18/10, o produtor colou um texto de 250 caracteres na descrição de um lançamento ou escreveu uma cultura longa.
  2. Ele instala o APK novo.
  3. Lançamentos, Histórico e Sítios mostram "Não foi possível carregar… Tentando de novo…" para sempre: o `repetirAposErro` tenta a cada 30 s e falha do mesmo jeito.
  4. Se a linha ruim for de **propriedade**, o `InicioViewModel` falha nos coletores sem `.catch` (FLW-01) e **o app fecha ao abrir**, porque o Início é a primeira tela. O aviso de chuva também para: o Worker falha a cada 12 h em `VerificadorDeChuva.verificar`.
  5. Para o produtor, parece que os dados sumiram. A reação natural é reinstalar, e aí os dados se perdem de verdade.
- **O que falta confirmar:** só a existência de uma linha assim no celular. O caminho no código está confirmado. Para conferir, use o Database Inspector do Android Studio (App Inspection):
  ```sql
  SELECT 'lancamento', id FROM lancamentos WHERE length(descricao) > 200
  UNION ALL
  SELECT 'propriedade', id FROM propriedades
   WHERE length(nome) > 80 OR length(cultura) > 60 OR area_hectares > 1000000;
  ```
- **Correção sugerida:** normalizar na leitura, sem afrouxar o domínio para gravações novas. O próprio KDoc de `Lancamento` já explica o risco ("no `init`, um registro já gravado poderia falhar ao ser lido"), só que para a data.
  ```kotlin
  fun LancamentoEntity.paraDominio(): Lancamento = Lancamento(
      // ...
      // Linhas gravadas antes do limite (18/10/2026) não podem esconder a lista inteira.
      descricao = descricao.take(Lancamento.DESCRICAO_MAXIMA)
  )
  ```
  Faça o mesmo com `nome.take(Propriedade.NOME_MAXIMO)`, `cultura.take(Propriedade.CULTURA_MAXIMA)` e `areaHectares.coerceAtMost(Propriedade.AREA_MAXIMA_HECTARES)`. A alternativa é uma migração 3→4 com `UPDATE … SET descricao = substr(descricao, 1, 200)`, que exige teste de migração no emulador. Teste novo: um DAO falso com uma descrição de 250 caracteres, e a lista precisa aparecer. Esse teste falha hoje.
- **Regra violada:** §5 regra 7 (é a mesma família do crash da R5: validação no `init` quebrando a leitura de dado gravado) e regra 18.

#### REL-01 · 🟡 `now()` sem relógio injetado em valores padrão e na UI

- **Arquivos:**
  - UiState: `feature/lancamento/LancamentoFormViewModel.kt:43` e `:62`; `feature/lancamento/LancamentoListaViewModel.kt:43`; `feature/propriedade/PropriedadeListaViewModel.kt:33`; `feature/inicio/InicioViewModel.kt:45` e `:58`; `feature/historico/HistoricoViewModel.kt:43`.
  - UI: `feature/lancamento/CamposDoLancamento.kt:279` e `aviso/NotificadorDeChuva.kt:124`.
- **O que está errado:** os valores padrão desses `UiState` usam `LocalDate.now()`, `YearMonth.now()` ou `LocalDateTime.now()` sem o `Clock`. Os ViewModels sobrescrevem quase todos com o relógio injetado, mas o estado inicial (`carregando = true`) usa o mês do relógio real, e qualquer construção nova que esquecer o campo volta a depender dele. O `CLAUDE.md` §10 diz que não há "nenhum valor padrão de relógio… em produção".
- **Cenário:** um teste com `relogioEm(31/12/2025)` que leia o estado antes do `advanceUntilIdle` vê o mês de hoje, e passa ou falha conforme a data em que roda. No calendário do formulário, sem data no campo, a abertura usa o "hoje" do sistema em vez do `hoje()` do ViewModel.
- **Correção sugerida:** tornar o campo obrigatório (`val mes: YearMonth`, sem padrão) ou usar um valor neutro que o ViewModel sempre preenche. Também `CalendarioDialogo(hoje: LocalDate, …)` e `mostrarExemplo(context, propriedadeId, agora)`. O `Dispatchers.Unconfined` em `di/TempoModule.kt:41` é aceitável, por estar na raiz da composição; vale um comentário dizendo isso.
- **Regra violada:** §5 regra 20.

#### BUG-02 · 🔵 Cultura vazia é gravada como o texto "Não informada"

- **Arquivo:** `feature/propriedade/PropriedadeFormViewModel.kt:315`.
- **O que está errado:** sem cultura, o app grava a string "Não informada" no banco.
- **Cenário:** ao editar a propriedade, o campo já vem preenchido com "Não informada" como se a pessoa tivesse digitado. Para apagar, ela precisa limpar o texto, e salvar vazio grava a mesma frase de novo.
- **Correção sugerida:** gravar vazio e mostrar "Não informada" só na tela. Como muda algo que o produtor vê, **pergunte ao João antes** (§9 item 8).

### Área 2 — Arquitetura e MVVM

Conferido e certo: o `:domain` não tem nenhum `import android.*`/`androidx.*`; nenhum ViewModel importa Compose; não há `object` com estado nem `GlobalScope`; as telas recebem `uiState` e lambdas; as interfaces dos repositórios ficam no domínio e as gravações em várias tabelas passam pela `Transacao`.

#### ARQ-01 · 🟡 Cálculos de negócio no ViewModel

- **Arquivos:** `feature/historico/HistoricoViewModel.kt:119-149` (totais por mês, melhor e pior mês, escala do gráfico) e `feature/propriedade/PropriedadeListaViewModel.kt:62-78` (saldo por propriedade, proporção e total). Em menor grau, `feature/propriedade/PropriedadeFormViewModel.kt:82` (`lerArea`) e `:378` (`ficaNoBrasil`).
- **O que está errado:** a R6 levou saldo e ranking para o `ResumoFinanceiro`, mas o comparativo entre propriedades e a regra de melhor/pior mês (só com 2 ou mais meses passados, sem empate) ficaram no ViewModel, sem teste no `:domain`.
- **Cenário:** quando vier a exportação do R9 ou um relatório, alguém reescreve "melhor mês" com outra semântica (por exemplo, contando mês futuro) e as telas passam a discordar.
- **Correção sugerida:** criar `ResumoFinanceiro.mesesDoAno(…)`, `melhorEPiorMes(…)` e `comparativoDePropriedades(…)` como funções puras, testadas no `ResumoFinanceiroTest`; o ViewModel só chama. `lerArea` é leitura de entrada e pode ficar; `ficaNoBrasil` caberia em `Propriedade`.
- **Regra violada:** §5 regra 8.

### Área 3 — Coroutines, Flow e concorrência

Conferido e certo: o toque duplo em "Salvar" é barrado (`salvando`/`salvo`); o Clima faz uma busca por vez (`Mutex` no repositório e `geracao` no ViewModel); o "Desfazer" não roda duas vezes (`usada` + `NonCancellable`); os `stateIn` usam `viewModelScope`; todo estado é coletado com `collectAsStateWithLifecycle`; `tentar { }` relança `CancellationException`.

#### FLW-01 · 🟠 Quatro coletores do banco sem `.catch`

- **Arquivos:** `feature/inicio/InicioViewModel.kt:135-138` (`propriedadeAtual.collectLatest`) e `:142-147` (cartão de chuva); `feature/clima/ClimaViewModel.kt:130-136`; `feature/conta/ContaViewModel.kt:63-86`.
- **O que está errado:** esses coletores leem o banco (`propriedadeRepository.observarTodas()`, `clima.observar(…)`, `conta.observarUsuarioLogado()`) sem `.catch` nem `repetirAposErro`. Uma exceção dentro de `viewModelScope.launch` sem tratador derruba o processo. O `ContaUiState` nem tem campo de erro. O próprio código conhece o risco: o comentário em `InicioViewModel.kt:125` diz "Sem isto, um erro do banco escaparia do launch e fecharia o app", mas só o coletor do nome foi protegido.
- **Cenário:** banco travado por um instante (`SQLiteDatabaseLockedException`, o caso que os testes simulam), disco cheio ou a linha antiga de BUG-01. O app fecha no Início, no Clima ou na Conta, em vez de mostrar a mensagem e tentar de novo.
- **Correção sugerida:**
  ```kotlin
  // Um único Flow compartilhado pelos dois coletores, com o mesmo tratamento das outras leituras.
  private val propriedadeAtual = combine(propriedadeRepository.observarTodas(), selecao.propriedadeId) { lista, id ->
      ResumoFinanceiro.propriedadeAtual(lista, id)
  }.distinctUntilChanged()
      .repetirAposErro { erro -> _uiState.update { it.copy(erro = erro.mensagemParaUsuario(MENSAGEM_ERRO)) } }
  ```
  Na Conta, use `.repetirAposErro { … }` e acrescente `erro: String?` ao `ContaUiState`. Para os testes, copie o padrão de `ResumosViewModelTest` ("erro ao ler as propriedades vira mensagem amigável…") para Início, Clima e Conta; os três falham hoje.
- **Regra violada:** §5 regra 11.

#### FLW-02 · 🟡 Chamadas suspensas sem `tentar { }`

- **Arquivos:**
  - `init` dos formulários: `feature/lancamento/LancamentoFormViewModel.kt:157` e `:163`; `feature/propriedade/PropriedadeFormViewModel.kt:166`; `feature/clima/AjustesAvisoViewModel.kt:72`; `feature/conta/AlterarSenhaViewModel.kt:48`.
  - Ações de conta: `feature/conta/LoginViewModel.kt:42`, `feature/conta/CadastroViewModel.kt:69` e `:97`, `feature/conta/ContaViewModel.kt:107`, `feature/conta/NovoConsentimentoViewModel.kt:30`.
  - Clima: `feature/clima/ClimaViewModel.kt:193-209`, onde `buscar` só tem `finally`.
- **O que está errado:** são chamadas que tocam o banco ou a rede fora de `tentar { }`. Os resultados selados do `ContaService` cobrem regras de negócio, não falhas do SQLite.
- **Cenário:** abrir um lançamento para editar com o banco travado fecha o app em vez de mostrar "Não foi possível carregar". No login, uma falha de disco ao gravar o rehash (`atualizarSenha`) derruba o app com `entrando = true`. No Clima, um `SQLiteFullException` ao gravar o cache, relançado de propósito em `data/repository/ClimaRepositoryRoom.kt:118`, escapa do `buscar`.
- **Correção sugerida:** `tentar { conta.entrar(email, senha) }.onSuccess { … }.onFailure { erro -> … erro.mensagemParaUsuario("Não foi possível entrar. Tente de novo.") }`, e o mesmo nos `init`.
- **Regra violada:** §5 regra 11.

### Área 4 — Room e dados

Conferido e certo:
- O schema `app/schemas/.../3.json` bate com as entidades: o build regenerou o arquivo e ele não mudou.
- As chaves estrangeiras têm CASCADE de usuário para propriedade e de propriedade para lançamento e previsão; o índice único em `email` existe.
- Os índices cobrem as consultas, o `@Upsert` é usado no lugar de `REPLACE` (com o comentário do CASCADE) e toda consulta filtra pelo dono.
- `excluir` lê e apaga na mesma transação.
- Não há N+1 no banco; as somas em memória estão documentadas como melhoria futura.

#### DAD-01 · 🔵 "Desfazer" não devolve `atualizado_em` nem a seleção

- **Arquivos:** `data/repository/PropriedadeRepositoryRoom.kt:82`; o desfazer do lançamento em `feature/lancamento/LancamentoFormViewModel.kt` (`lancamentoRepository.salvar(apagado)`); `feature/propriedade/PropriedadeFormViewModel.kt:342`.
- **O que está errado:** o desfazer regrava com `atualizado_em = agora`, porque esse campo não existe no modelo do domínio. Além disso, excluir a propriedade limpa a seleção, e o desfazer não a devolve.
- **Cenário:**
  - Excluir e desfazer um lançamento faz ele subir para o topo do dia (a ordem é `data DESC, atualizado_em DESC`).
  - Restaurar uma propriedade dá o mesmo `atualizado_em` a todos os lançamentos dela, e a ordem dentro do dia fica a critério do SQLite.
  - Depois de restaurar a propriedade, o Início mostra a primeira em ordem alfabética, e não a restaurada.
- **Correção sugerida:** levar o `atualizadoEm` original no que `excluir`/`excluirComLancamentos` devolvem e usá-lo no `restaurar`; no desfazer da propriedade, chamar `selecao.selecionar(id)`.
- **Regra violada:** o espírito da regra 19 ("guardar exatamente o que saiu do banco").

### Área 5 — Segurança e privacidade (LGPD)

Conferido e certo:
- **Senha:** PBKDF2-HMAC-SHA256 com 600.000 iterações, sal de 16 bytes de `SecureRandom`, normalização NFKC, comparação com `MessageDigest.isEqual`, rehash transparente e hash fictício contra enumeração por tempo.
- **Logs e segredos:** nenhum `Log`, `println` ou `printStackTrace`. O histórico do Git não tem `*.jks`, `*.keystore`, APK, AAB nem `local.properties`, e não há senhas de assinatura.
- **Rede e backup:** só HTTPS (`usesCleartextTraffic="false"`), com timeouts. Backup só criptografado da API 28 em diante; nada no Android 8; sessão e `seguranca.xml` ficam fora do backup.
- **Permissões e notificação:** a localização só é pedida no toque. A notificação usa `VISIBILITY_PRIVATE` com versão pública e `PendingIntent` imutável.

#### SEG-01 · 🟠 Excluir qualquer conta desliga o bloqueio por digital do aparelho

- **Arquivo:** `app/src/main/java/br/joao/agrosaldo/data/LimpezaDaConta.kt:47-48`, chamado de `domain/.../conta/ContaService.kt:247`.
- **O que está errado:** ao excluir uma conta, `apagarRastrosDaConta` desliga `bloqueio` e `privacidade`, que são ajustes **do aparelho** e valem para todas as contas. Na tela de Conta, desligar o bloqueio exige a digital (`Biometria.confirmarMudancaDoBloqueio`); por este caminho, não.
- **Cenário** (confiança 9/10, confirmado lendo o código):
  1. O celular do produtor está desbloqueado e o app tem o bloqueio por digital ligado.
  2. Outra pessoa abre o app, toca em "Usar senha" (isso só encerra a sessão), vai em "Criar conta" e cria uma conta qualquer, porque o cadastro é livre.
  3. Em Conta > Excluir conta, ela digita a senha que acabou de criar.
  4. `bloqueio.definir(false)`: o bloqueio e o `FLAG_SECURE` somem para o produtor, que só percebe que precisou entrar de novo. O "olho" dos valores ocultos também volta ao padrão.
- **Correção sugerida:** só voltar os ajustes do aparelho ao padrão quando não sobrar nenhuma conta.
  ```kotlin
  suspend fun apagarRastrosDaConta(usuarioId: String) {
      // Bloqueio e valores ocultos são do aparelho: outra conta pode depender deles.
      if (usuarioDao.contar() == 0) {
          privacidade.definir(false)
          bloqueio.definir(false)
      }
      // ...
  }
  ```
  `contar()` seria um `SELECT COUNT(*) FROM usuarios` novo. Teste: com duas contas e o bloqueio ligado, excluir uma não pode desligar o bloqueio. Falha hoje.
- **Regra violada:** §6 (bloqueio) e a promessa em `docs/PERGUNTAS_DA_BANCA.mdx:128`: "Ligar e desligar o bloqueio também pede a digital".

#### SEG-02 · 🟠 Confirmado — nome e localização reais do produtor no código

- **Arquivos:**
  - **Nome real do produtor** (às vezes também num e-mail de exemplo), 24 ocorrências em 7 arquivos: `domain/.../conta/RegrasDeConta.kt:92` (KDoc de produção), as prévias de `feature/conta/CadastroScreen.kt` e `feature/conta/ContaScreen.kt`, e os testes `domain/src/test/.../ContaTest.kt` (11), `app/src/test/.../feature/ContaViewModelsTest.kt` (5), `app/src/test/.../data/RelogiosEUsuarioLogadoTest.kt` (4) e `app/src/test/.../data/CamadaDeDadosTest.kt` (1).
  - **Coordenadas reais da propriedade:** `app/src/test/.../feature/PropriedadeFormViewModelTest.kt:250` usava uma leitura de GPS com 8 casas decimais; `app/src/test/.../clima/ClimaTest.kt` e as prévias usavam outros pontos da mesma região, junto com nomes de propriedade que podiam ser reais.
- **O que faltava confirmar:** se o nome era do produtor e se os pontos ficavam na propriedade. Confirmado pelo João em 28/10/2026: eram dados reais.
- **Cenário:** se forem reais, o nome do produtor e a localização da fazenda estão no código-fonte e no histórico do Git desde 30/09 e 01/10 (commits `2a4fe1d` e `a34c1f1`), à vista de quem tiver acesso ao repositório. Não consegui verificar se o repositório é público, porque o `gh` não está instalado.
- **Correção sugerida:** trocar por dados claramente fictícios, como "Maria Souza" (que já aparece num teste) e coordenadas redondas como `-22.0, -47.0`. Reescrever o histórico da `prod` é destrutivo: só faça se o João decidir, depois de avaliar.
- **Regra violada:** §6: "Dados pessoais reais do produtor não entram em código, testes nem docs".

#### SEG-03 · 🟠 A confirmar (decisão de produto) — `FLAG_SECURE` só com o bloqueio ligado

- **Arquivo:** `app/src/main/java/br/joao/agrosaldo/MainActivity.kt:105-116`.
- **O que está errado:** o `FLAG_SECURE` só é ligado quando o bloqueio por digital está ativo. O `CLAUDE.md` §6 (linha 124) e a doc técnica §22 (linha 3767) dizem "`FLAG_SECURE` na Activity (sem miniatura em Recentes com valores)", sem condição. As seções 2 e 12 da doc técnica, por outro lado, descrevem o comportamento condicional.
- **Cenário:** com o bloqueio desligado, que é o padrão de conta nova, a miniatura em Recentes mostra o saldo, e um app de gravação de tela captura os valores.
- **Correção sugerida:** é uma decisão do João entre (a) `FLAG_SECURE` sempre, o que impede o print de tela usado para dar suporte, ou (b) manter o comportamento e corrigir o `CLAUDE.md` §6 e a doc §22.
- **Regra violada:** §6 ("Não remova"), conforme o texto atual.

#### SEG-04 · 🟡 Depois de reiniciar o celular, o bloqueio por tentativas segue o relógio de parede

- **Arquivo:** `domain/src/main/java/br/joao/agrosaldo/domain/conta/ContaService.kt:310-328`. O teste `domain/src/test/.../ContaTest.kt:333` fixa esse comportamento.
- **O que está errado:** o relógio monotônico só vale dentro do mesmo boot. Depois de um reinício, ou num aparelho que não informa `BOOT_COUNT`, a espera passa a ser medida pelo relógio de parede, e adiantar a hora a encerra.
- **Cenário:** depois de 5 senhas erradas, o bloqueio é de 30 s e dobra até 15 min. Reiniciar o celular (cerca de 1 min) e adiantar a hora em 15 min libera a próxima tentativa. A força bruta passa de 1 tentativa a cada 15 min para cerca de 1 por minuto. O `CLAUDE.md` §6 e a banca (2.2) dizem que o bloqueio é "imune a mudar a hora".
- **Correção sugerida:** depois de um reinício, nunca encurtar a espera. Se o boot gravado for diferente do atual e ainda houver falhas acumuladas (`falhasSeguidas >= MAXIMO_TENTATIVAS`), reancorar no boot novo um bloqueio com a duração correspondente às falhas, em vez de confiar no relógio de parede. O custo: quem reiniciar depois de um bloqueio espera de novo na próxima tentativa errada. Ajuste também a doc para dizer exatamente o que vale.
- **Regra violada:** §6, conforme o texto.

#### ENT-01 · 🟡 Limites de texto não cortados ao digitar

- **Arquivos:** `feature/conta/CadastroViewModel.kt:51-53`; `feature/conta/LoginViewModel.kt:28-29`; `feature/propriedade/PropriedadeFormViewModel.kt:203-216`; `domain/.../model/Usuario.kt:21-25`.
- **O que está errado:**
  - No cadastro e no login, nome (80), e-mail (254) e senha (128) não são cortados ao digitar ou colar.
  - Área, latitude e longitude não têm limite de texto.
  - O `init` de `Usuario` só confere "não vazio"; o tamanho é validado no `ContaService`, não num `require`.
  - O commit `a092fde` diz "e-mail (254)… cortados no ViewModel ao digitar", mas o corte não está no código.
- **Cenário:** ao colar 1.000 caracteres no e-mail do cadastro, o campo aceita tudo e só no "Continuar" aparece "Informe um e-mail válido", sem explicar que o problema é o tamanho.
- **Correção sugerida:**
  ```kotlin
  fun aoMudarEmail(v: String) = _uiState.update { it.copy(email = v.trim().take(RegrasDeConta.EMAIL_MAXIMO), erroEmail = null) }
  ```
  Faça o mesmo para nome e senha, e dê um teto de uns 20 caracteres para área e coordenadas. Pôr `require` de tamanho no `Usuario` exige normalizar na leitura, pelo mesmo motivo de BUG-01.
- **Regra violada:** §5 regra 18.

### Área 6 — Compose e interface

Conferido e certo: nenhuma cor literal fora de `ui/theme`; os callbacks de `LaunchedEffect` passam por `rememberUpdatedState`; as listas preguiçosas têm `key`; os alvos de toque são de 48 dp ou mais, com `heightIn`; os formulários de lançamento e de propriedade guardam o rascunho no `SavedStateHandle`; a atribuição CC BY da Open-Meteo tem link na tela de Clima.

#### UI-01 · 🟡 Prévias escuras e de fonte 2× faltando

- **O que está errado:**
  - **Sem prévia escura (13 de 17 telas):** AjustesAviso, AlterarSenha, Cadastro, Consentimento, Conta, Login, NovoConsentimento, OfertaDeBloqueio, Politica, TelaBloqueio, Historico, PropriedadeForm e PropriedadeLista.
  - **Sem fonte 2× (8 telas):** AjustesAviso, AlterarSenha, Cadastro, Consentimento, NovoConsentimento, OfertaDeBloqueio, Politica e TelaBloqueio.
- **Cenário:** um contraste ruim da tela de bloqueio ou do consentimento no modo escuro só aparece no celular. As capturas do Roborazzi cobrem 10 telas, mas a regra fala das prévias.
- **Correção sugerida:** uma multiprévia em `ui/theme`, já que o tema segue `isSystemInDarkTheme()`:
  ```kotlin
  @Preview(name = "Claro", showBackground = true)
  @Preview(name = "Escuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
  @Preview(name = "Fonte 2x", showBackground = true, fontScale = 2f)
  annotation class PreviasDaTela
  ```
- **Regra violada:** §5 regra 13.

#### UI-02 · 🔵 `dp` soltos para margem

- **Arquivos:** `feature/lancamento/LancamentoListaScreen.kt:201` e `:216-217` e `feature/lancamento/TopoDoLancamento.kt:75` usam `20.dp` onde a linha vizinha usa `Espacos.margemDaTela`. Também há `core/navigation/AppNavegacao.kt:260` (`84.dp`), `feature/conta/LoginScreen.kt:77` (`40.dp`), `28.dp` em `feature/conta/ConsentimentoScreen.kt:71`, `feature/conta/TelaBloqueio.kt:89` e `feature/clima/AjustesAvisoScreen.kt:218`, e `14.dp`/`18.dp` em mais 9 lugares. `BarraInferior`, `OfertaDeBloqueioRoute` e `OfertaDeBloqueioDialog` não recebem `modifier`.
- **Correção sugerida:** `Espacos.margemDaTela` no lugar dos `20.dp`; uma constante nomeada para os `84.dp` (folga do rodapé do formulário); decidir se 14, 18 e 28 entram na escala ou viram o valor vizinho.
- **Regra violada:** §5 regras 13 e 14.

### Área 7 — Hilt, WorkManager e ciclo de vida

**Sem achados.** Conferido:
- **Hilt:** `@Singleton` só no que é compartilhado; os DAOs ficam sem escopo.
- **Agendamento:** o Worker roda a cada 12 h com política `KEEP` e exige rede (`CONNECTED`). O backoff é exponencial, começando em 15 min, e o retry só acontece em falha passageira, no máximo 3 vezes.
- **Casos de borda:** sem permissão de notificação, o aviso não é registrado. Sem usuário logado, a verificação nem começa. Se a conta mudar no meio da verificação, ela para.
- **Notificação:** cada propriedade tem a sua tag e o seu `data`, e o id que vem de fora é conferido em `SessaoViewModel.prepararAberturaDoClima`.

### Área 8 — Testes

Conferido e certo: as regras de negócio (RN01 a RN07, `RegraDeChuva`, `ResumoFinanceiro`) estão todas cobertas; os testes usam relógio fixo (`relogioEm`, `relogioPorMs`) e não usam `Thread.sleep`; o Worker é testado no JVM.

#### TST-01 · 🟡 O SQL real nunca roda no CI; faltam testes dos achados acima

- **O que está errado:**
  - Os 15 testes de DAO, que cobrem o SQL de verdade (o JOIN de posse, o CASCADE e o `@Upsert` sem `REPLACE`), só rodam no emulador. Os testes de JVM usam DAOs falsos (`app/src/test/.../data/CamadaDeDadosTest.kt`).
  - Não há teste para a falha de leitura de propriedades e da previsão no Início, no Clima e na Conta (FLW-01), para o `LimpezaDaConta` (ele só aparece no `GrafoDeDependenciasTest`), para a linha antiga fora do limite (BUG-01) nem para o `AgendaWorkManager`.
- **Cenário:** alguém troca o `@Upsert` de `PropriedadeDao` por `@Insert(onConflict = REPLACE)`. A partir daí, editar uma propriedade apaga todos os lançamentos dela pelo CASCADE, e o CI continua verde.
- **Correção sugerida:** o Robolectric já está no projeto. Rode os testes de DAO também no JVM com `Room.inMemoryDatabaseBuilder`, usando `@RunWith(AndroidJUnit4::class)`, e mantenha o `androidTest` para o aparelho. Casos de teste sugeridos:

  | Caso | Tipo | Falha hoje? |
  | --- | --- | --- |
  | Início com `observarTodas()` falhando na 1ª leitura mostra erro e não fecha | ViewModel (JVM) | sim |
  | Clima, idem | ViewModel (JVM) | sim |
  | Conta, idem, com `erro` no estado | ViewModel (JVM) | sim |
  | Lançamento com descrição de 250 caracteres no banco aparece na lista | Repositório + DAO falso | sim |
  | Excluir uma de duas contas não desliga o bloqueio | `ContaService` + `LimpezaDaConta` | sim |
  | Reiniciar e adiantar a hora mantém o bloqueio por tentativas | `ContaService` | sim |
  | Editar uma propriedade não apaga os lançamentos | DAO no Robolectric | não (protege contra regressão) |

- **Regra violada:** §9 item 5 ("cada bug corrigido ganha um teste que falharia antes").

#### FAKE-01 · 🔵 Fakes divergem do Room

- **Arquivo:** `domain/src/testFixtures/java/br/joao/agrosaldo/data/repository/PropriedadeRepositoryMemoria.kt:21`.
- **O que está errado:** o fake devolve as propriedades na ordem de inserção, enquanto o Room ordena em pt-BR (`emOrdemAlfabetica`). Os fakes de propriedade e de lançamento também não exigem sessão nem conferem a posse.
- **Cenário:** um teste de "sem seleção, usa a primeira propriedade" passa com o fake, onde "primeira" é a primeira inserida; no app, é a primeira em ordem alfabética.
- **Correção sugerida:** `override fun observarTodas() = propriedadesGuardadas.map { it.emOrdemAlfabetica() }`.

### Área 9 — Build, CI e qualidade

#### BLD-01 · 🟡 Configuração de baseline que sobrou

- **Arquivo:** `app/build.gradle.kts`.
  - `:59`: o ktlint aponta para `ktlint-baseline.xml`, arquivo que não existe.
  - `:62-63`: o comentário ainda diz "O baseline guarda os achados antigos, para o CI cobrar só código novo".
  - `:100-110`: a tarefa `detektBaseline` continua existindo, e o `detekt` passa a usar `detekt-baseline.xml` **automaticamente** se o arquivo aparecer.
- **Cenário:** alguém roda `./gradlew detektBaseline` (ou `ktlintGenerateBaseline`) para "limpar o vermelho" e comita o arquivo. O CI passa a ignorar os achados antigos, o contrário da regra "sem baseline".
- **Correção sugerida:** remover o `baseline.set(…)` do ktlint, a tarefa `detektBaseline` e o `if (baselineDoDetekt.exists())`, e corrigir o comentário.
- **Regra violada:** §3 (detekt sem baseline, zero achados).

#### CI-01 · 🔵 Pontos frágeis no workflow

- **Arquivo:** `.github/workflows/android.yml`.
  - `:39`: `${{ vars.GRADLE_TASKS || '…' }}` deixa uma variável do repositório trocar o que o CI roda sem mudar o arquivo; o check fica verde rodando menos tarefas.
  - `:93`: `${{ github.head_ref }}` interpolado dentro de `run:` é o padrão clássico de injeção de script do Actions. Hoje não é explorável, porque um PR de fork roda com token só de leitura e já executa o Gradle do próprio PR. Por isso ficou em 🔵.
  - `:37`: o `chmod +x gradlew` torna desatualizada a frase do `CLAUDE.md` §8 sobre o exit 126.
- **Correção sugerida:** passar o nome do ramo por `env:` (`RAMO: ${{ github.head_ref || github.ref_name }}`) e usar `"$RAMO"` no comando; documentar `GRADLE_TASKS` no `CLAUDE.md` ou removê-la.

#### LINT-01 · 🔵 Avisos do Android Lint

- **`VisibleForTests`** em `feature/lancamento/LancamentoFormViewModel.kt:107` e `feature/propriedade/PropriedadeFormViewModel.kt:124`: o construtor primário usa `SavedStateHandle()` como valor padrão, e o AndroidX marca esse construtor como exclusivo de teste. Tire o padrão e passe `SavedStateHandle()` explicitamente nos testes.
- **`InlinedApi`** em `core/ui/PermissaoNotificacao.kt:77` e `:103`: falso positivo, porque o ramo só roda no Android 13+. Um `@RequiresApi` ou um comentário de supressão resolve.
- **`RedundantLabel`** em `app/src/main/AndroidManifest.xml:35`: o `android:label` da Activity repete o da aplicação.
- **`ObsoleteSdkInt`** e `UnusedResources`: ver MORTO-01.

#### DEP-01 · 🔵 Dependências e catálogo

- **Versões novas apontadas pelo Lint:** AGP 9.3.2 → 9.3.3 ou 9.5.0; Compose BOM 2026.02.01 → 2026.09.00; Kotlin 2.2.10 → 2.4.21; WorkManager 2.11.0 → 2.12.0; core-ktx 1.19.0 → 1.19.1; core-splashscreen 1.0.1 → 1.2.0; Roborazzi 1.76.0 → 1.77.0; org.json (só em teste). Não verifiquei CVEs.
- **Catálogo:**
  - A versão `coroutinesTest` (`gradle/libs.versions.toml:17`) também é usada pelo `kotlinx-coroutines-core`; um nome como `coroutines` seria mais claro.
  - O `androidx-hilt-navigation-compose` só serve para trazer, por tabela, o `hilt-lifecycle-viewmodel-compose`: nenhum arquivo importa `androidx.hilt.navigation`. Melhor declarar o artefato certo.
- **Sugestão:** atualizar o BOM e o Kotlin num PR próprio, testando no celular.
- **Regra relacionada:** §5 regra 17.

### Área 10 — Divergências entre documentação e código

#### DOC-01 · 🔵 Divergências menores e comentários desatualizados

Os detalhes estão na tabela da seção 5. Os itens sem efeito no app:
- `work-testing` citado no `CLAUDE.md` §3;
- a frase do exit 126 no §8;
- a lista de `core/navigation/` sem `RegrasDeNavegacao.kt`;
- o comentário de baseline no `app/build.gradle.kts`;
- o KDoc de `data/SelecaoAtual.kt:62`, que diz "ON_RESUME" (a `MainActivity` chama no `onStart`);
- o comentário de `feature/clima/AjustesAvisoViewModel.kt:67`, que fala de "propriedades de outra conta" (os ajustes já são por conta);
- a banca chamando a sincronização online de "próximo passo natural", quando o `CLAUDE.md` §10 a deixou fora do escopo.

### Área 11 — Código morto e manutenção

Conferido e certo: nenhum `TODO`, `FIXME` ou `HACK`; os nomes seguem a convenção (domínio em português, sufixos técnicos em inglês); nenhum arquivo passa de 500 linhas.

#### MORTO-01 · 🔵 Código, recursos e dependências sem uso

- **Funções:**
  - Sem uso nenhum: `core/util/FormatoClima.kt:34` (`horaCurta`).
  - Sem uso em produção (só nos testes, ou em nenhum lugar): `core/util/Datas.kt:51` (`pertenceAo`), `feature/lancamento/LancamentoFormViewModel.kt:67` (`nomePropriedadeSelecionada`), `domain/.../model/Lancamento.kt:70` (`valorComSinal`, citado no RN06, embora o `ResumoFinanceiro` use `tipo` direto), `data/local/dao/PropriedadeDao.kt:54` (`contarDoUsuario`), `domain/.../clima/Previsao.kt:12` (`CondicaoTempo.molhado`) e `domain/.../financeiro/ResumoFinanceiro.kt:28` (`TotaisDoPeriodo.VAZIO`).
- **Recursos:** `res/values/colors.xml` (7 cores do template, nenhuma usada); `res/drawable/ic_launcher_background.xml` e `ic_launcher_foreground.xml` (o ícone usa `ic_agrosaldo_*`); `res/mipmap-anydpi-v26/`, que duplica `mipmap-anydpi/` com `minSdk 26`.
- **Dependências:** `espresso-core`, `compose-ui-test-junit4` e o BOM no `androidTestImplementation` (`app/build.gradle.kts:145` e `:206-207`), já que o único teste instrumentado é de DAO.
- **Outros:** `app/src/main/keepRules/rules.keep` só tem o comentário do template; 8 arquivos de `.idea/` estão versionados e geram diff a cada sincronização do Android Studio, como no `git status` de hoje.
- **Correção sugerida:** apagar. Se o RN06 continuar citando `valorComSinal`, use-o no `ResumoFinanceiro.totais` ou ajuste a doc.

### Descartados na triagem de segurança

Pelo filtro da `security-review` (confiança abaixo de 8 ou fora dos critérios):
- **Injeção por `github.head_ref`:** rebaixada para endurecimento (CI-01).
- **Gravação assíncrona (`apply()`) do contador de tentativas:** matar o app no intervalo exato entre a resposta e a gravação não é prático.
- **`MainActivity` exportada recebendo `propriedade_id` de outro app:** o id só é usado se a propriedade for do usuário conectado.
- **Banco sem SQLCipher e `adotarPropriedadesSemDono`:** endurecimento e pendência já registrada no `CLAUDE.md`, não vulnerabilidade nova.
- **Cadastro revelando se o e-mail já tem conta (`emailEmUso`):** decisão documentada no código; as contas são locais.
- **Senha enorme no login (custo do PBKDF2):** negação de serviço local, fora dos critérios.

---

## 5. Divergências de documentação

| Documento | O que diz | O que o código faz |
| --- | --- | --- |
| `CLAUDE.md:124` e `DOCUMENTACAO_TECNICA.mdx:3767` | "`FLAG_SECURE` na Activity (sem miniatura em Recentes com valores)" | Só com o bloqueio por digital ligado (`MainActivity.kt:105-116`). As seções 2 e 12 da doc técnica dizem isso certo (SEG-03) |
| `CLAUDE.md:123`, `DOCUMENTACAO_TECNICA.mdx:3766` e `PERGUNTAS_DA_BANCA.mdx:121` | Bloqueio por tentativas "imune a mudar a hora"; "adiantar a hora do celular não destrava" | Depois de reiniciar, vale o relógio de parede (`ContaService.kt:315-318`; teste `ContaTest.kt:333`) (SEG-04) |
| `PERGUNTAS_DA_BANCA.mdx:128` | "Ligar e desligar o bloqueio também pede a digital" | Excluir qualquer conta desliga sem digital (`LimpezaDaConta.kt:48`) (SEG-01) |
| `CLAUDE.md` §5 regra 11 | "Todo `Flow` lido do banco tem `.catch` e um campo de erro no UiState" | 4 coletores sem `.catch`; `ContaUiState` sem campo de erro (FLW-01) |
| `CLAUDE.md` §5 regra 13 | "Toda tela tem `@Preview` claro, escuro e com fonte 2×" | 13 de 17 telas sem prévia escura e 8 sem fonte 2× (UI-01) |
| `CLAUDE.md` §5 regra 18 e commit `a092fde` | Nome, e-mail etc. "cortado[s] no ViewModel ao digitar ou colar" | Cadastro e login não cortam; só descrição, nome e cultura da propriedade e busca são cortados (ENT-01) |
| `CLAUDE.md:209` (§10) e regra 20 | "nenhum valor padrão de relógio… em produção"; nunca `now()` solto | 5 UiState com `LocalDate.now()`/`YearMonth.now()` como padrão, mais 2 pontos de UI (REL-01) |
| `CLAUDE.md:42` (§3, Testes) | A stack de testes inclui `work-testing` | Não é dependência; o Worker é testado pela função `verificarChuva`, extraída para isso |
| `CLAUDE.md:178` (§8) | Sem o bit de execução do `gradlew`, "o CI falha com exit 126" | O CI roda `chmod +x gradlew` (`android.yml:37`), e o bit já é 100755 |
| `CLAUDE.md:57` (§4) | `core/navigation/` tem AppNavegacao, Navegador, GrafosDeNavegacao, TransicoesDeTela, BarraInferior e Rotas | Também tem `RegrasDeNavegacao.kt` |
| `CLAUDE.md` §6 | Dados pessoais reais não entram em código e testes | **A confirmar** (SEG-02) |
| `app/build.gradle.kts:62-63` (comentário) | "O baseline guarda os achados antigos, para o CI cobrar só código novo" | Não há baseline (BLD-01) |
| `data/SelecaoAtual.kt:62` (KDoc) | `acompanharMesCorrente` é "Chamado… (ON_RESUME)" | Também é chamado no `onStart` da `MainActivity` |
| `feature/clima/AjustesAvisoViewModel.kt:67` (comentário) | Preserva ids de "propriedades de outra conta" | Os ajustes já são por conta (`AjustesAvisoPorConta`) |
| `PERGUNTAS_DA_BANCA.mdx:255` | A sincronização online é "o próximo passo natural" | O `CLAUDE.md` §10 deixa a API própria fora do escopo (o ROADMAP a põe como "Futuro") |
| `CLAUDE.md` §7 (RN06) | O sinal mora em `Lancamento.valorComSinal` | A regra vale, mas `valorComSinal` não é usado em produção (`ResumoFinanceiro` usa `tipo`) |

Conferidos e certos: as contagens da doc técnica §1 (118 + 24 arquivos Kotlin, 15 telas, 15 ViewModels, 4 tabelas, 499 testes de JVM, 15 instrumentados, 62 data classes), todas as versões da stack, o Gradle 9.5.0, `minSdk`/`targetSdk`, o Robolectric com `sdk=36`, o PBKDF2 com 600.000 iterações e a atribuição da Open-Meteo.

---

## 6. Plano de correção sugerido

Cada linha é uma branch a partir da `prod` e um PR. A prioridade usa a fórmula da `tech-debt`: **(Impacto + Risco) × (6 − Esforço)**, com notas de 1 a 5.

| Ordem | Branch | Achados | Por quê | Prioridade |
| --- | --- | --- | --- | --- | --- |
| 0 | — (conversa) | SEG-02, SEG-03, BUG-02 | Precisam de decisão do João antes de mexer. Rodar a consulta de BUG-01 no celular do produtor | — |
| 1 | `fix/leitura-resiliente` | BUG-01, FLW-01, FLW-02 e os testes de TST-01 | Evita crash e "dados sumidos". Normalizar no mapeador não mexe em entidade, então não precisa de migração | (5+5)×(6−2) = **40** |
| 2 | `fix/bloqueio-do-aparelho` | SEG-01, SEG-04, SEG-03 (depois da decisão) e a atualização do `CLAUDE.md` §6, da doc §22 e da banca 2.2/2.3 | São promessas de segurança da documentação | (4+4)×(6−2) = **32** |
| 3 | `fix/limites-e-relogio` | ENT-01, REL-01 | Regras 18 e 20; mudança mecânica | (2+3)×(6−1) = **25** |
| 4 | `test/dao-no-jvm` | TST-01 (DAOs no Robolectric), FAKE-01 | Faz o CI pegar erro de SQL | (3+4)×(6−3) = **21** |
| 5 | `refactor/resumos-no-dominio` | ARQ-01 | Regra 8, com testes no `:domain` | (2+2)×(6−2) = **16** |
| 6 | `chore/previas-e-espacos` | UI-01, UI-02 | Regras 13 e 14 | (2+1)×(6−1) = **15** |
| 7 | `chore/limpeza-de-build` | BLD-01, CI-01, LINT-01, MORTO-01, DOC-01, DAD-01 | Higiene. O DAD-01 muda o que o produtor vê: confirmar com o João | (1+2)×(6−1) = **15** |
| 8 | `chore/dependencias` | DEP-01 | Atualizar o BOM e o Kotlin pede teste no celular | (1+2)×(6−3) = **9** |

Observações:
- O PR 1 resolve o crash sem migração. Se o João preferir corrigir os dados com uma migração 3→4, a versão do banco sobe e os testes de DAO precisam rodar no Android Studio (`CLAUDE.md` §8).
- Os PRs 1 e 2 mudam comportamento de segurança e de erro: testar no celular do produtor antes do merge (§9 item 7).

---

## 7. O que está bom (preservar)

- **Senha:** PBKDF2 bem feito (600k iterações, sal aleatório, NFKC, comparação em tempo constante, rehash transparente, hash fictício contra enumeração por tempo). Não mexer.
- **Posse dos dados:** toda consulta filtra pelo dono; a posse é conferida antes do upsert (`existeSemSerDoUsuario`); `@Upsert` no lugar de `REPLACE`, com o motivo explicado; excluir e devolver o que saiu acontece na mesma transação.
- **Dinheiro:** sempre `Long` em centavos, `BigDecimal` só na formatação, teto de 9 dígitos que recusa em vez de cortar.
- **Domínio:** `:domain` realmente puro; regras de chuva e de saldo como funções puras com teste.
- **Rede:** HTTPS, timeout por operação e total, desconexão no cancelamento (`chamadaBloqueante`); resposta inválida vira `Falha` sem crash e sem estragar o cache.
- **Privacidade:** backup só criptografado; sessão e bloqueio fora do backup; notificação com versão pública para a tela de bloqueio; coordenadas arredondadas antes de sair do aparelho.
- **Segundo plano:** o Worker tem a lógica no domínio, é testável no JVM e só registra o aviso depois de notificar.
- **Qualidade:** detekt sem baseline e com zero achados; CI que anota os erros e publica as capturas; 499 testes rápidos e determinísticos.

---

## 8. Correções (28/10/2026)

Branch `fix/auditoria-2026-10-28`, a partir da `prod` (`a735a1b`). Um commit por grupo, todos com autor Morettys2 e sem linha de atribuição. Em cada grupo, os testes novos foram escritos antes e falharam antes da correção.

| Commit | Grupo | Achados |
| --- | --- | --- |
| `7ec7d46` | Dados reais do produtor fora do projeto | SEG-02 |
| `f8ddf9f` | Leitura resiliente | BUG-01, FLW-01, FLW-02 |
| `ab65b1c` | Bloqueio do aparelho | SEG-01, SEG-04, SEG-03 |
| `90c9e91` | Limites e relógio | ENT-01, REL-01 |
| `2960243` | Testes de DAO no JVM | TST-01, FAKE-01 |
| `7a2744b` | Regras no domínio | ARQ-01 |
| `8a1b40e` | Prévias e espaços | UI-01, UI-02 |
| `2dd3e3b` | Produto | BUG-02, DAD-01 |
| `6721802` | Limpeza | BLD-01, CI-01, LINT-01, MORTO-01, DEP-01 |
| `6da53c4` | Correção de build achada no fim | TST-01 |
| `d1f5f25` | Documentação | DOC-01 e a tabela da seção 5 |
| `bea83a1` | Status e correções neste relatório | — |
| `6c484c2` | Padrão da busca de dados reais fora do relatório; contagens de data classes na doc | SEG-02, DOC-01 |
| `03acbc8` | Teste do agendamento do WorkManager | TST-01 |
| `38b0d0f` | Fakes com sessão e posse, com teste de contrato | FAKE-01 |
| `f3e9914` | Ids de exemplo sem os nomes antigos | SEG-02 |
| `eb6c1c1` | Runner dos testes no aparelho declarado | MORTO-01 (regressão) |
| este commit | Status e documentação do fechamento | — |

**O que apareceu durante a correção:**
- O `precisaNovoAceite` do `SessaoViewModel` também lia o banco sem tratamento de erro (mesma família do FLW-01) e foi corrigido no mesmo commit.
- Ordenar o fake como o Room (FAKE-01) deixou instável um teste do formulário de propriedade que dependia da ordem dos ids. O teste foi corrigido no commit do ARQ-01.
- Com os testes de DAO em `src/sharedTest`, a pasta `src/androidTest/java` deixou de existir num checkout limpo e o detekt quebraria no CI. Corrigido em `6da53c4`.
- A limpeza do MORTO-01 tirou o Espresso, que trazia por tabela o `androidx.test:runner`. Os testes no aparelho deixaram de começar, e só se viu ao rodá-los no emulador em 02/11. Corrigido em `eb6c1c1`.
- Dois ids de teste ainda tinham nomes antigos de propriedade com hífen, que a busca com espaço não pegava. Corrigido em `f3e9914`.
- Este relatório (que ainda citava os dados reais) entrou por engano no commit do grupo 3. Como nada tinha sido publicado, o commit local foi refeito sem ele antes do primeiro push.

**Ferramentas no fim (02/11/2026):** ktlint, detekt (zero achados, sem baseline), `testDebugUnitTest` (492 testes, incluindo os 16 de DAO no JVM e os 18 do contrato), `:domain:test` (89 testes), `assembleDebug` e `lintDebug` (0 erros, 10 avisos, todos de versão de dependência) passaram. O `assembleRelease` com R8 compila (conferido em 28/10). O `connectedAndroidTest` rodou no emulador: 16 de 16 testes de DAO.

**Busca de dados reais:** uma busca no repositório inteiro, sem diferenciar maiúsculas, pelo nome do produtor, os e-mails de exemplo antigos, a cidade e as coordenadas da região voltou **vazia**. O padrão da busca não fica escrito aqui, porque ele mesmo deixaria ler esses dados. Uma segunda busca, pelos nomes de propriedade das prévias antigas e pelas casas decimais das coordenadas antigas, também voltou vazia. O histórico do Git, por decisão, não foi reescrito: os commits antigos da `prod` ainda têm esses dados. O repositório é privado; antes de torná-lo público, é preciso limpar o histórico (está no roadmap).

