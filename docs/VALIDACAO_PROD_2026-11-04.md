# Validação da `prod` para produção — AgroSaldo (04/11/2026)

> **Commit validado:** `34258b7cf02a825cfd758490ada2bd4903a917e1` (`prod`, "Merge branch 'demo' into prod").
> **Correções:** branch `release/validacao-prod`, a partir desse commit. Nada foi commitado na `prod`.
> **Referência do que está no celular do produtor:** o build `3fe4b31` ("correcoes", 08/10/2026), `versionCode 6` / `1.5`, banco versão 3. **Confirme no celular** (Ajustes > Apps > AgroSaldo > versão 1.5): se for outra versão, a seção 3 muda.

---

## 1. Veredito: **GO com ressalvas**

O código da `prod` passa em todas as ferramentas, a migração do banco preserva os dados (provado no JVM com SQLite real, campo a campo) e a senha atual do produtor continua valendo. Os dois bloqueantes encontrados foram corrigidos.

**Só instale no celular do seu pai depois de:**

1. Rodar o **roteiro 7a** (instalar por cima da 1.5 com dados) num aparelho ou emulador. O APK de release com R8 **nunca foi aberto num aparelho**: o emulador desta máquina pede autorização do adb na tela e não deu para usar sem você (seção 4).
2. **Assinar com a chave de sempre** (`C:\chaves\agrosaldo.jks`) e conferir o certificado (seção 3.4). O build não tem `signingConfig`: a assinatura é manual, pelo Android Studio.
3. Rodar o `connectedAndroidTest` (os 21 testes de banco no aparelho).
4. Decidir sobre o **novo aceite do termo LGPD** (seção 6).

---

## 2. A auditoria foi resolvida? (Fase 1)

Conferido no código, com os testes lidos um a um. Caminhos curtos: `feature/…`, `data/…`, `core/…` em `app/src/main/java/br/joao/agrosaldo/`; `domain/…` em `domain/src/main/java/br/joao/agrosaldo/domain/`.

| ID | Sev. | Correção | Teste que falharia sem ela | Status |
| --- | --- | --- | --- | --- |
| BUG-01 | 🔴 | `data/local/Mapeadores.kt:27-35, 60-68, 89-97` | `CamadaDeDadosTest.kt:550, 564, 576` | ✅ |
| FLW-01 | 🟠 | `InicioViewModel.kt:135-138, 154`; `ClimaViewModel.kt:134, 159`; `ContaViewModel.kt:91-93` (+ `erro` em `:39`); `SessaoViewModel.kt:58` | `ResumosViewModelTest.kt:447, 474`; `ClimaViewModelsTest.kt:508`; `ContaComBancoTravadoTest.kt:109, 206` | ✅ (ver nota 1) |
| SEG-01 | 🟠 | `data/LimpezaDaConta.kt:49-53`; `ContaService.kt:248` | `LimpezaDaContaTest.kt:48`; `ContaTest.kt:388`; `AgroSaldoDatabaseTest.kt:199` | ✅ |
| SEG-02 | 🟠 | Nome completo, coordenadas e nomes de sítio trocados em `7ec7d46` | — | ❌ parcial → **corrigido em `40ec733`** (nota 2) |
| SEG-03 | 🟠 | `MainActivity.kt:102-111` (`FLAG_SECURE` só com o bloqueio ligado, decisão de produto) | nenhum | ⚠️ sem teste |
| FLW-02 | 🟡 | 11 pontos + extras da sessão, todos dentro de `tentar { }` | `ContaComBancoTravadoTest.kt:128-218`; `LancamentoFormViewModelTest.kt:728`; `PropriedadeFormViewModelTest.kt:612`; `ClimaViewModelsTest.kt:534, 555` | ✅ |
| SEG-04 | 🟡 | `ContaService.kt:322-345` | `ContaTest.kt:333, 350` | ✅ |
| ENT-01 | 🟡 | `CadastroViewModel.kt:123-128`; `LoginViewModel.kt:32-33`; `AlterarSenhaViewModel.kt:219-223`; `PropriedadeFormViewModel.kt:217-233`; `Usuario.kt:25-26` | `ContaLimitesDeTextoTest.kt:54, 70, 81`; `PropriedadeFormViewModelTest.kt:638`; `ValidacaoDeEntradasTest.kt` | ✅ (o diálogo de excluir conta escapou: corrigido em `b59cc28`) |
| REL-01 | 🟡 | UiState sem data padrão; `CamposDoLancamento.kt:278, 286` | `ResumosViewModelTest.kt:508, 524, 530` | ✅ |
| ARQ-01 | 🟡 | `HistoricoViewModel.kt:107-108`; `PropriedadeListaViewModel.kt:54` | `ResumoFinanceiroTest.kt` (bloco da linha 198) | ✅ |
| TST-01 | 🟡 | `app/src/sharedTest` no `test` (`app/build.gradle.kts:59`) | `AgroSaldoDatabaseTest.kt:122` e mais 15; `AgendaWorkManagerTest.kt:32, 41, 49` | ✅ |
| UI-01 | 🟡 | `@PreviasDaTela` nas 17 telas | (prévia não leva teste) | ⚠️ |
| BLD-01 | 🟡 | Sem `baseline.set` nem `detektBaseline` | (build) | ⚠️ |
| DAD-01 | 🔵 | `Mapeadores.kt:71`; `PropriedadeFormViewModel.kt:362-366` | `CamadaDeDadosTest.kt:613, 625`; `PropriedadeFormViewModelTest.kt` | ✅ |
| BUG-02 | 🔵 | `PropriedadeFormViewModel.kt:193, 333` | `PropriedadeFormViewModelTest.kt:664` | ✅ |
| FAKE-01 | 🔵 | `PropriedadeRepositoryMemoria.kt:6, 29-42` | `CamadaDeDadosTest.kt:600`; `ContratoDosRepositoriosTest.kt:63, 86, 98` | ✅ |
| CI-01 | 🔵 | `android.yml:38-39, 89, 97` | (CI) | ⚠️ |
| UI-02 | 🔵 | Margens com `Espacos` | (visual) | ⚠️ (sobras corrigidas em `ca11672`) |
| LINT-01 | 🔵 | Sem `SavedStateHandle()` padrão; um `android:label` | Lint: "No issues found" | ✅ |
| DEP-01 | 🔵 | `gradle/libs.versions.toml` | Lint sem aviso de versão | ✅ |
| MORTO-01 | 🔵 | Funções, recursos e Espresso removidos | (limpeza) | ⚠️ |
| DOC-01 | 🔵 | `CLAUDE.md` e docs | — | ❌ parcial → **corrigido em `f8ddf9f`** (nota 3) |

Nenhum item regrediu (🔁): não há `now()` fora do `RelogioDoAparelho`, todo Flow do banco nos ViewModels passa por `repetirAposErro`, toda chamada suspensa passa por `tentar` e não há `!!` nem `GlobalScope`.

**Notas:**
1. **FLW-01:** o app não fecha, mas, com erro na primeira leitura, o Clima mostrava "Cadastre uma propriedade" a quem já tem (o produtor poderia achar que perdeu os dados e reinstalar). Corrigido em `ab7f92e`, com teste que falhava antes.
2. **SEG-02:** a troca de 28/10 pegou o nome completo, mas deixou **o primeiro nome do produtor, com acento**, em 25 linhas de teste (`ContaViewModelsTest.kt`, `ContaTest.kt`) e numa prévia de produção (`AlterarSenhaScreen.kt:112`). A busca da auditoria, provavelmente sem o acento, voltou vazia por isso. Severidade alta e parcial: bloqueante.
3. **DOC-01:** o KDoc de `SelecaoAtual.kt:62` (ON_RESUME) e o comentário de `AjustesAvisoViewModel.kt:89-90` ("outra conta") ficaram como antes.

---

## 3. Atualização sem perder dados (Fase 3)

### 3.1 Banco

- **Entidades × `3.json`:** entre `a34c1f1` (banco v3) e `c04eb4f` (migração 4), as entidades só mudaram em comentários. O `3.json` foi escrito uma vez e nunca alterado. A única diferença entre `3.json` e `4.json` é `propriedades.usuario_id` passar a `NOT NULL`, e a `MIGRACAO_3_4` (`data/local/Migracoes.kt`) recria a tabela com o `createSql` exato do `4.json`.
- **Versão nova + migração + schema + teste:** existem (versão 4, `MIGRACAO_3_4`, `app/schemas/.../4.json`, `MigracaoDoBancoTest`).
- **Testes novos (`4fa087a`):** todos os campos de todas as linhas das quatro tabelas saem iguais (com acento, aspas, texto vazio, área no teto e valor de 9 dígitos); depois da migração, `PRAGMA foreign_key_check` vazio, e excluir a propriedade e a conta ainda apaga em cascata (RN02). Isso responde à dúvida de a tabela recriada deixar os lançamentos presos à tabela velha.
- **`fallbackToDestructiveMigration`:** não existe em lugar nenhum (`git grep` só acha comentários e a doc que proíbem).

### 3.2 Versão

`versionCode 6` / `1.5` na `prod`, **igual ao instalado**: bloqueante. Corrigido para `versionCode 7` / `versionName 1.6` em `2aaf9e5`.

### 3.3 Senha

O `HashDeSenha` mudou de módulo, mas o formato é o mesmo (`pbkdf2_sha256$<iter>$<sal>$<hash>`, Base64 sem padding, NFKC, 600 mil iterações). Teste dourado novo (`bf47d4f`): hashes calculados fora do Kotlin (Python `hashlib`) continuam conferindo, inclusive com acento e com acento decomposto.

### 3.4 Assinatura

- `app/build.gradle.kts` **não tem `signingConfig`**: o `assembleRelease` gera `app-release-unsigned.apk`, e a assinatura é feita à mão (Android Studio > Build > Generate Signed App Bundle or APK), com `C:\chaves\agrosaldo.jks`. A chave existe nesse caminho, fora do repositório.
- `git ls-files` não tem `.jks`, `.keystore`, `.apk`, `.aab` nem `local.properties`; o `.gitignore` tem todos. Não existe `~/.gradle/gradle.properties` com senhas.
- **Risco:** escolher outra chave no assistente. Antes de mandar o APK, compare o certificado do APK novo com o instalado:
  ```
  apksigner verify --print-certs AgroSaldo-1.6.apk   (Sdk\build-tools\36.0.0)
  ```
  O SHA-256 tem que ser igual ao do APK 1.5 que você mandou para ele. Se for diferente, **não instale**: o Android recusa, e a saída "desinstalar e instalar" apaga os dados.

### 3.5 Preferências (comparado `3fe4b31` × `prod`)

| Arquivo | O que acontece na atualização |
| --- | --- |
| `sessao.xml` | Mesma chave: o produtor continua conectado |
| `seguranca.xml`, bloqueio por digital | Mesma chave: o bloqueio continua como estava |
| `seguranca.xml`, contador de senhas erradas | Era global e virou por e-mail. As chaves antigas são apagadas uma vez (`TentativasPreferencias`, `init`): um bloqueio em andamento zera. Inofensivo |
| `privacidade.xml` (olho dos valores) | Mesma chave |
| `selecao.xml` (sítio escolhido) | Mesma chave |
| `aviso_chuva.xml` | Os ajustes valiam para o aparelho e passaram a valer por conta. As chaves antigas são adotadas pela primeira conta que entra (`AjustesAvisoPorConta.adotarChavesSemConta`): os limites que ele configurou continuam |
| WorkManager | Mesma classe (`aviso.VerificacaoChuvaWorker`), mesmo nome único e política `KEEP`; o pedido antigo não tem dados de entrada |
| Canal de notificação | Mesmo id (`aviso_chuva`) |
| **Termo LGPD** | **Versão 2 → 3** (`c1c2dd0`, texto do backup). O produtor **vai ver o novo aceite** ao abrir: decisão na seção 6 |

---

## 4. Build de release (Fase 4)

- **R8 ligado** (`optimization { enable = true }`), sem regras próprias: depende das regras que as bibliotecas trazem (Room, Hilt, WorkManager, Biometric, SplashScreen). As rotas são texto (sem kotlinx.serialization), o `org.json` é da plataforma, e não há reflexão no código. **Não foi provado num aparelho.** O emulador `Medium_Phone` (imagem com Play Store) subiu, mas o adb ficou "unauthorized", e autorizar exige tocar na tela. A outra saída seria desinstalar o AgroSaldo do seu AVD e apagar os dados de teste que estão nele, e isso eu não fiz sem você pedir.
- **`debuggable`:** o release não é depurável (padrão do AGP; nada no build liga).
- **Logs:** `git grep "Log\.|println|printStackTrace"` em `app/src/main` e `domain/src/main`: nenhum.
- **App começa vazio:** dados de exemplo e fakes só em `src/testFixtures`.
- **Manifesto:** `INTERNET`, `POST_NOTIFICATIONS`, `ACCESS_COARSE_LOCATION` e `ACCESS_FINE_LOCATION` (a troca por `COARSE` está no R9). Só a `MainActivity` é exportada (o `propriedade_id` de fora é conferido contra o dono). `usesCleartextTraffic="false"`.
- **Backup:**
  - API 26-27: nada vai para o backup.
  - API 28-30: só com criptografia de ponta a ponta ou na transferência entre aparelhos.
  - API 31 em diante: `disableIfNoEncryptionCapabilities`.
  - `sessao.xml`, `seguranca.xml` e `permissoes.xml` nunca saem do aparelho.
- **`FLAG_SECURE`:** só com o bloqueio por digital ligado, como decidido (`MainActivity.kt:102-111`).

---

## 5. Dados reais e privacidade (Fase 5)

- **Árvore atual:** depois de `40ec733` e `0ee1fac`, a busca pelo nome do produtor (com e sem acento), sobrenome, cidade, coordenadas da região, CPF, CNPJ e telefone só acha o `CLAUDE.md` e as docs que **proíbem** esses dados (e um acaso de bytes no `ic_launcher.webp`). Os e-mails do repositório são todos `@exemplo.com`, mais o do autor dos commits e o do bot do CI. As coordenadas `-21.0` dos testes do domínio eram números redondos (não o ponto real) e foram trocadas por `-23.5/-48.0`.
- **Imagens:** as 8 capturas de `docs/assets` só mostram dados fictícios ("Fazenda Modelo", "Sítio Exemplo", `produtor@exemplo.com`).
- **Histórico do Git:** continua com os dados antigos (não reescrito, como combinado). O repositório não pode ficar público sem limpar o histórico.
- **LGPD:**
  - O aceite vem antes de gravar a conta, e o termo tem versão (`TermoDePrivacidade.VERSAO_ATUAL = 3`).
  - Excluir a conta apaga em cascata e limpa os ajustes do aviso, as notificações, a seleção e as páginas livres do SQLite (`VACUUM`). Os ajustes do aparelho só voltam ao padrão quando não sobra nenhuma conta.
  - As coordenadas saem arredondadas para 2 casas (`OpenMeteo.arredondar`), só por HTTPS.
- **Senha:**
  - PBKDF2-HMAC-SHA256, 600 mil iterações, sal de 16 bytes, NFKC e `MessageDigest.isEqual`.
  - O bloqueio por tentativas é por e-mail e usa o relógio monotônico.

---

## 6. Decisões que dependem de você

| Assunto | O que acontece | Recomendação |
| --- | --- | --- |
| Novo aceite LGPD | O termo passou da versão 2 para a 3, por causa do texto sobre o backup. O produtor vai aceitar de novo ao abrir a 1.6 | **Manter.** O texto mudou de verdade, e a LGPD pede que ele saiba |
| `versionName` | Escolhi `1.6` | Troque se preferir outro número |
| Assinatura | Manual, sem `signingConfig` | Manter manual até o R9 e conferir o certificado com `apksigner` sempre |
| R8 | Ligado e sem teste em aparelho | Manter ligado, mas só distribuir depois do roteiro 7a |
| Distribuição | APK direto ou teste fechado na Play | **APK direto** para o seu pai agora. O teste fechado exige 12 testadores por 14 dias e a conta da Play (R9) |
| Itens 🟡 abertos | Seção 8 | Decidir quais entram |

---

## 7. Roteiro de teste manual no celular

**Antes:** gere o APK assinado com `C:\chaves\agrosaldo.jks` e confira o SHA-256 (seção 3.4).

**a) Atualização por cima da 1.5 com dados (o mais importante)**

Se for testar primeiro num emulador, o APK debug da 1.5 de `3fe4b31` foi compilado nesta validação; é só gerar de novo a partir desse commit.

1. Num aparelho de teste com a **1.5** instalada (mesma chave da 1.6): crie uma conta, dois sítios (um com cultura em branco), uns dez lançamentos (receita e despesa, em meses diferentes, um com descrição longa), ligue o aviso de chuva com limites diferentes do padrão, ligue o bloqueio por digital e esconda os valores com o olho.
2. Anote o saldo do mês e do ano de cada sítio (tire print).
3. Instale a **1.6 por cima** (sem desinstalar).
4. Abra: o app deve pedir a digital e depois o **novo aceite do termo**. Aceite.
5. Confira: mesma conta conectada, os dois sítios, todos os lançamentos, os mesmos saldos dos prints, o olho ainda fechado, os limites do aviso iguais aos que você configurou e o bloqueio ainda ligado.
6. Saia e entre de novo com a **mesma senha**.
7. **Só depois disso, repita no celular do seu pai.** Antes, confira em Ajustes > Apps > AgroSaldo que ele está na 1.5.

**b) Uso normal**

Crie uma conta nova (outro e-mail), um sítio com "Usar minha localização", lançamentos de receita e despesa, edite um, exclua e toque em **Desfazer** (ele volta no mesmo lugar). Exclua um sítio e desfaça (voltam os lançamentos e a seleção).

**c) Modo avião**

Com o modo avião ligado: Início, Lançamentos, Histórico e Sítios funcionam. O Clima mostra a previsão guardada ou o aviso de falta de conexão, sem fechar. Desligue o modo avião e toque em atualizar.

**d) Aviso de chuva com o app fechado**

Permita as notificações, configure limites baixos (ex.: 10% e 0,5 mm), feche o app pelos Recentes e espere a próxima verificação (até 12 h; com Wi-Fi). A notificação deve abrir o Clima do sítio certo. Teste também com a notificação negada: nada de aviso e nenhum erro.

**e) Bloqueio por digital**

Ligue em Conta (pede a digital). Feche e abra: pede a digital. Com ele ligado, print de tela e miniatura em Recentes ficam pretos. Desligue (pede a digital de novo).

**f) Fonte grande e modo escuro**

Fonte máxima nos ajustes do celular, depois o modo escuro. Passe por todas as telas: nada cortado nem sobreposto, todos os botões alcançáveis. (Atenção à barra inferior: com fonte 2×, o rótulo "Lançamentos" pode ficar cortado.)

**g) Excluir a conta**

Com duas contas no aparelho e o bloqueio ligado, exclua a conta de teste: o bloqueio continua ligado. Exclua a última: tudo volta ao padrão e o app abre na tela de entrada, sem dados.

**Testes de banco no aparelho** (Android Studio, emulador ligado):
```
./gradlew connectedAndroidTest
```
São 21 testes: `AgroSaldoDatabaseTest` (16, DAOs) e `MigracaoDoBancoTest` (5, migração 3 → 4).

---

## 8. Achados médios e baixos (não corrigidos; para você decidir)

| Sev. | Onde | O que é | Sugestão |
| --- | --- | --- | --- |
| 🟡 | `AgroSaldoApp.kt:43-47` + `AjustesAvisoPorConta.ler(null)` | Sem sessão, os ajustes padrão (`ativo = true`) religam o trabalho de 12 h, que acorda o app à toa e termina sem fazer nada | Sem sessão, chamar `desligar()`; o login religa |
| 🟡 | `aviso/VerificacaoChuvaWorker.kt:124` | "Verificar chuva agora" não exige rede. Sem internet, tenta 3 vezes com espera, e a notificação pode chegar minutos depois | `NetworkType.CONNECTED` também nesse pedido |
| 🟡 | `LancamentoFormViewModel.kt:186` | Lançamento antigo com mais de 9 dígitos abre como R$ 0,00 na edição (o salvar recusa, nada se perde). O teto existe desde a R4 | Avisar "valor acima do limite" |
| 🟡 | `Mapeadores.kt:27-35` | Editar ou desfazer uma propriedade antiga com nome acima de 80 caracteres regrava o nome cortado | Aceito pela regra 18; se quiser, o desfazer pode regravar a entidade original |
| 🟡 | `MainActivity.kt:102-111` | `FLAG_SECURE` sem teste automático | Teste Robolectric com o bloqueio ligado e desligado |
| 🔵 | `VerificadorDeChuva.kt:79-84` | O periódico e o "agora" juntos podem notificar duas vezes (a segunda substitui a primeira, mas toca de novo) | Registrar o aviso antes de notificar, ou um `Mutex` |
| 🔵 | `core/navigation/BarraInferior.kt:106-112` | Fonte 2×: "Lançamentos" pode ficar cortado na barra | Conferir no passo 7f |
| 🔵 | `LoginScreen.kt`, `TelaBloqueio.kt` | Títulos sem `semantics { heading() }` (TalkBack) | Acrescentar |
| 🔵 | `CartoesDoClima.kt:94, 110, 150`, `ContaScreen.kt:192` e outros | `dp` de preenchimento fora de `Espacos` | Limpeza visual |
| 🔵 | `Previsao.kt:77` | `fusoSegundos` da API sem validação (lança acima de ±18 h; a Open-Meteo não manda isso) | `runCatching` com UTC de reserva |

**Correção sem teste automático:** o corte da senha no diálogo de excluir a conta (`b59cc28`). O teste de interface do diálogo não estabiliza no Robolectric (o cursor piscando no campo focado não deixa o Compose ficar ocioso). A regra é a mesma das outras telas de senha. Confira no passo 7g colando um texto grande.

---

## 9. Correções feitas (branch `release/validacao-prod`)

Autor de todos os commits: Morettys2, sem trailer de atribuição.

| Commit | O quê | Teste |
| --- | --- | --- |
| `40ec733` | Primeiro nome real do produtor fora dos testes e da prévia (SEG-02, bloqueante) | busca sem ocorrências |
| `2aaf9e5` | `versionCode 7` / `1.6` (bloqueante) | — |
| `bf47d4f` | Teste dourado do hash de senha | novo |
| `4fa087a`, `ab48883` | Migração preserva todos os campos, as chaves estrangeiras e o CASCADE | novo |
| `ab7f92e` | Clima mostra o erro em vez de "Cadastre uma propriedade" | `TelasTest`, falhava antes |
| `b59cc28` | Senha do diálogo de excluir a conta cortada em 128 | sem teste (ver seção 8) |
| `ca11672` | `Espacos`, `heightIn`, `BotaoSecundario` e `rememberUpdatedState` onde faltavam | testes de tela e capturas |
| `f8ddf9f` | Comentários desatualizados (DOC-01) e nota do rascunho no `CLAUDE.md` | — |
| `0ee1fac` | Coordenadas dos testes do domínio longe da região | testes do domínio |
| (este commit) | Este relatório, versão e contagens de testes nas docs | — |

---

## 10. Portão de qualidade (Fase 2)

Cada comando rodou sozinho, em sequência, depois de um `clean`.

| Comando | `prod` (`34258b7`) | Depois das correções |
| --- | --- | --- |
| `clean` | ✅ | ✅ |
| `ktlintCheck` | ✅ | ✅ |
| `detekt` | ✅ zero achados | ✅ zero achados |
| `:domain:test` | ✅ 89 testes, 0 falhas | ✅ 90 testes, 0 falhas |
| `testDebugUnitTest` | ✅ 492 testes, 0 falhas (30 classes) | ✅ 495 testes, 0 falhas (30 classes) |
| `assembleDebug` | ✅ | ✅ |
| `lintDebug` | ✅ "No issues found" (0 erros, 0 avisos) | ✅ "No issues found" |
| `assembleRelease` | ✅ R8 ligado, APK não assinado | ✅ `versionCode 7` / `1.6`, sem `debuggable` (conferido com `aapt`), 2,8 MB |
| Avisos `w:` do Kotlin | 0 | 0 |

- **Números da doc:** a seção de testes da doc técnica dizia 581 (89 + 492), o que bate. Três textos estavam velhos (80 testes do domínio na doc técnica; 514 na banca e no ROADMAP) e foram atualizados.
- **CI:** o último run na `prod` processou `34258b7` e publicou as capturas na `ci/capturas` em 02/11 às 22:26 UTC. Esse passo roda com `if: always()`, então não prova o check verde. Sem o `gh` nesta máquina, confira o ✅ na aba Actions do GitHub.
