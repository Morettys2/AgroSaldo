# Auditoria de código — AgroSaldo

Revisão do código na versão `3fe4b31` (antes da limpeza de comentários). As linhas citadas já são as
do código atual, depois da limpeza: só os comentários mudaram, então o comportamento é o mesmo.

**Escopo:** segurança e contas; camada de dados (Room, migrações); clima, aviso de chuva, localização
e WorkManager; funcionalidades financeiras e UI; qualidade dos testes.

**Método:** leitura completa dos arquivos de cada área, seguindo as chamadas entre camadas. Cada
achado foi confirmado traçando um caminho concreto no código. As suspeitas que não se sustentaram
estão no fim, em "O que foi verificado e está correto".

**Resumo:** nenhum problema crítico. São 3 de severidade alta, 12 médios e vários baixos. A base é
boa (veja "Pontos fortes"). Os itens altos e médios são os que convém resolver antes de publicar na
Play Store.

Caminhos relativos a `app/src/main/java/br/joao/agrosaldo/`, salvo indicação.

## Status das correções

| Item | Status | Testes |
|---|---|---|
| A1 Bloqueio de login × relógio do sistema | ✅ Corrigido | `ContaTest` (4 novos) |
| A2 Valores arredondados | ✅ Corrigido | `FormatacaoTest` (3 novos) |
| A3 Crash ao excluir propriedade durante o download | ✅ Corrigido | `ClimaTest` (2 novos) e `AgroSaldoDatabaseTest` (1 novo, instrumentado) |
| M1 Contador de tentativas por e-mail | ✅ Corrigido | `ContaTest` |
| M2 Capa de bloqueio × diálogos e foco | ✅ Corrigido (conteúdo sai da composição enquanto bloqueado) | — (Compose) |
| M3 Novo aceite LGPD pulável | ✅ Corrigido | `RegrasDeNavegacaoTest`, `ContaViewModelsTest` |
| M4 FLAG_SECURE | ✅ Corrigido (com o bloqueio por digital ligado) | — (Activity) |
| M5 Backup exposto | ✅ Corrigido (`xml-v28/backup_rules.xml`, nuvem só com criptografia) | — (XML) |
| M6 Aviso registrado antes de notificar | ✅ Corrigido | `ClimaTest` |
| M7 Notificação abre a propriedade errada | ✅ Corrigido | `ContaViewModelsTest` |
| M8 Tela de Clima congelada | ✅ Corrigido | `ClimaViewModelsTest` |
| M9 Botão de permissão sem efeito | ✅ Corrigido | `PermissaoNotificacaoTest` |
| M10 Sinal de menos nas coordenadas | ✅ Corrigido (botão ± e aviso fora do Brasil) | `PropriedadeFormViewModelTest` |
| M11 Área "1.000" | ✅ Corrigido | `PropriedadeFormViewModelTest` |
| M12 Formulários × morte do processo | ✅ Corrigido (SavedStateHandle) | `LancamentoFormViewModelTest`, `PropriedadeFormViewModelTest` |
| B1–B27 | ✅ Corrigidos (B14 e B27: código removido) | Testes nas suítes de cada área |
| Testes fracos listados em "Testes" | ✅ Corrigidos | — |
| Teste desatualizado em `ContaViewModelsTest` (não compilava) | ✅ Corrigido | — |
| Editar propriedade excluída recriava o registro (análogo ao B20) | ✅ Corrigido | `PropriedadeFormViewModelTest` |

Decisões tomadas nas correções (revisar se discordar):

- **Política de privacidade:** o texto passou a mencionar o backup do Android e `TermoDePrivacidade.VERSAO_ATUAL`
  subiu para 3, então todo usuário vê o novo aceite uma vez.
- **M1:** não há contador global que um login certo não zere, porque permitiria a qualquer pessoa bloquear todas
  as contas do aparelho errando e-mails inventados.
- **M5:** no Android 8.x (API 26–27) nada entra no backup, porque essas versões não criptografam de ponta a ponta.
- **M10:** coordenada fora do Brasil é um aviso, não um bloqueio, porque há quem tenha terra do outro lado da
  fronteira.
- **Valor com mais de 9 dígitos:** é recusado, em vez de cortado.
- **A 5ª senha errada:** já responde "bloqueado", em vez de "senha incorreta".

Sem Android SDK no ambiente em que as correções foram feitas, o código de Activity, Compose, XML e Room
foi revisado manualmente, mas não compilado. Antes de publicar, rode `./gradlew assembleDebug test
connectedAndroidTest lint`.


---

## ALTO

### A1. Bloqueio de login depende do relógio do sistema: é burlável e pode travar a conta por anos
`domain/conta/ContaService.kt:239` (`segundosDeBloqueio`) e `:66` (relógio padrão `System::currentTimeMillis`)

`bloqueadoAte` é gravado em hora de parede. Isso causa dois problemas:

- **Burla:** depois de 5 erros, basta adiantar a hora do celular para o bloqueio acabar.
- **Trava quase permanente:** o `coerceAtMost(BLOQUEIO_MAXIMO_MS)` limita só o número mostrado na
  tela, não o bloqueio gravado. Quem adiantar a data para 2036, errar 5 vezes e voltar a data
  bloqueia login, troca de senha e exclusão de conta até 2036. A tela mostra para sempre "15 min".
  Um celular com o relógio errado depois de descarregar produz o mesmo efeito, sem ataque.

**Correção:** em `segundosDeBloqueio`, se `restante > BLOQUEIO_MAXIMO_MS`, regravar
`bloqueadoAte = relogio() + BLOQUEIO_MAXIMO_MS`. Para impedir a burla, guardar também
`SystemClock.elapsedRealtime()` + `Settings.Global.BOOT_COUNT` e usar o relógio monotônico enquanto
o aparelho não reiniciar.

### A2. Valores sem centavos e com arredondamento bancário nas listas e resumos
`core/util/Moeda.kt:11` (`MOEDA_CURTA`, `maximumFractionDigits = 0`) e `core/ui/LinhaLancamento.kt:81`.
`emRealCurto` é usado em 22 pontos (lista, Início, Histórico, Sítios).

O `NumberFormat` arredonda com HALF_EVEN:

| Valor real | Valor exibido |
|---|---|
| R$ 0,50 | R$ 0 |
| R$ 1,50 | R$ 2 |
| R$ 12,50 | R$ 12 |
| R$ 13,50 | R$ 14 |

Três despesas de R$ 0,50 aparecem como "− R$ 0" enquanto o total do filtro mostra "R$ 1,50". O saldo
da aba Lançamentos (curto) diverge do saldo do Início (completo). Num app financeiro, o valor do
lançamento precisa aparecer exato.

**Correção:** usar `emReal()` nas linhas de lançamento e nos saldos. Se quiser manter o formato
curto em cartões, aplicá-lo só quando `centavos % 100 == 0L`, ou usar `RoundingMode.DOWN` com uma
indicação visual.

### A3. Crash ao excluir uma propriedade enquanto a previsão está sendo baixada
`data/repository/ClimaRepositoryRoom.kt:60` (`buscarEGuardar` → `dao.salvar(...)`)

`previsoes.propriedade_id` tem chave estrangeira para `propriedades`. O download pode levar até
cerca de 25 s (timeouts de 10 s + 15 s). Se a propriedade for excluída nesse meio tempo, o `@Upsert`
lança `SQLiteConstraintException`. Só `IOException` e `RespostaInvalidaException` são capturadas,
então a exceção sobe.

- **No app:** o `viewModelScope.launch { clima.atualizarSeNecessario(...) }` do Início é um job
  solto, e a exceção derruba o app.
- **No Worker:** a verificação de chuva aborta e as demais propriedades ficam sem aviso.

Dois revisores chegaram a este achado de forma independente.

**Correção:** capturar `SQLiteConstraintException` (e `SQLiteException`) em `buscarEGuardar` e
devolver `ResultadoAtualizacao.Falha`.

---

## MÉDIO

### Segurança e privacidade

**M1. Contador de tentativas único para o aparelho e zerado por login em qualquer conta**
`domain/conta/ContaService.kt:151`. O cadastro é livre. Quem pegar o celular cria a própria conta,
chuta 4 senhas no e-mail do dono, entra na própria conta (o contador volta a 0), sai e repete. O
backoff nunca chega a agir.
**Correção:** manter um contador por e-mail normalizado (ex.: chave `falhas_<sha256(email)>`) e um
contador global que um login certo não zera.

**M2. A capa de bloqueio não cobre diálogos nem o foco de teclado**
`core/navigation/AppNavegacao.kt:515` e `feature/propriedade/PropriedadeFormScreen.kt:109`.
`AlertDialog`, `DatePickerDialog` e `DropdownMenu` abrem em janelas próprias, acima da capa.
Cenário: o diálogo "Excluir esta propriedade?" fica aberto e o app vai para o fundo. Quem pegar o
celular cancela a digital e toca em "Excluir". O `rememberSaveable` mantém o diálogo aberto mesmo
depois que o processo morre. Com teclado físico, Tab/Enter alcançam os botões debaixo da capa.
**Correção:** expor um `CompositionLocal` de "travado" e não compor diálogos e menus enquanto
`precisaDesbloquear`. No conteúdo, usar `focusProperties { canFocus = false }`.

**M3. O novo aceite da LGPD pode ser pulado pela notificação ou por um intent externo**
`core/navigation/AppNavegacao.kt:178` e `core/navigation/SessaoViewModel.kt:43`.
O efeito `pedidosDoClima` navega para o Clima sem checar `precisaNovoAceite`. Como a `MainActivity`
é exportada, qualquer app pode mandar `destino=clima`. Além disso, `precisaNovoAceite` começa como
`false`, então o Início aparece por alguns frames antes do desvio.
**Correção:** exigir `!precisaNovoAceite` nesse efeito, incluir a rota atual nas chaves do
`LaunchedEffect` de regras e usar um estado inicial "desconhecido".

**M4. Sem `FLAG_SECURE`: valores financeiros na miniatura de Recentes e em capturas de tela**
`MainActivity.kt:37`. A miniatura de Recentes guarda o saldo mesmo com o bloqueio por digital
ligado.
**Correção:** com o bloqueio ativo (ou sempre), aplicar
`window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)`. No Android 13+, usar também
`setRecentsScreenshotEnabled(false)`.

**M5. O backup expõe o banco inteiro via `adb backup` no Android 8 a 11**
`AndroidManifest.xml:21` (`allowBackup="true"`) e `res/xml/backup_rules.xml`.
Com depuração USB ligada, `adb backup br.joao.agrosaldo` extrai o `agrosaldo.db` com e-mail, hash
(que pode ser atacado offline), lançamentos e coordenadas.
**Correção:** em `backup_rules.xml` (vale até a API 30), incluir o banco com
`requireFlags="clientSideEncryption"` (API 28+). Atenção: com `<include>`, o que não for listado
deixa de ir para o backup. Em `data_extraction_rules.xml`, usar
`<cloud-backup disableIfNoEncryptionCapabilities="true">`. Se o backup não for essencial, a
alternativa mais simples é `allowBackup="false"`.

### Clima e aviso de chuva

**M6. O aviso é registrado como enviado antes de a notificação sair**
`domain/clima/VerificadorDeChuva.kt:78` e `aviso/VerificacaoChuvaWorker.kt:35`.
Com duas propriedades, o aviso de A é gravado e então o sinal cai durante o download de B. O
WorkManager cancela o Worker e a notificação de A nunca aparece. Na nova tentativa, `podeAvisar`
devolve `false`, e o produtor fica cerca de 11 h sem aviso.
**Correção:** registrar o aviso só depois de `NotificadorDeChuva.mostrar`, item a item.

**M7. Tocar na notificação abre o Clima da propriedade errada**
`aviso/NotificadorDeChuva.kt:63`. O intent não leva o `propriedadeId`. A tela abre na propriedade
selecionada e mostra "Sem chuva relevante", contradizendo a notificação.
**Correção:** usar `putExtra("propriedade_id", ...)` e chamar `SelecaoAtual.selecionar(id)` antes de
navegar, depois de validar que a propriedade pertence ao usuário logado.

**M8. A tela de Clima fica "congelada" ao voltar para a aba**
`feature/clima/ClimaViewModel.kt:119`. O estado só é recalculado quando a propriedade muda.
Cenário: abrir às 14h e voltar às 19h. A tela ainda mostra "chuva a partir das 16h" e não baixa
previsão nova. Depois da meia-noite, "Hoje" e "Amanhã" ficam errados. O Início e a lista de
lançamentos já resolvem isso com `LifecycleEventEffect(ON_RESUME)`.
**Correção:** criar `aoVoltarParaATela()` no `ClimaViewModel`, que recalcula o estado e chama
`buscar(forcar = false)`, e ligá-lo no `ON_RESUME`.

**M9. O botão "Permitir notificações" não faz nada em alguns casos**
`core/ui/PermissaoNotificacao.kt:47`. Isso acontece quando a permissão foi negada duas vezes ou
quando o canal "Aviso de chuva" está desligado. Nesses casos o diálogo do sistema não aparece, e o
cartão continua na tela sem efeito.
**Correção:** nesses casos, abrir `Settings.ACTION_APP_NOTIFICATION_SETTINGS` (ou
`ACTION_CHANNEL_NOTIFICATION_SETTINGS`).

### Dados digitados

**M10. Latitude e longitude usam um teclado que pode não ter o sinal de menos**
`feature/propriedade/PropriedadeFormScreen.kt:149` e `:157` (`KeyboardType.Decimal`, sem
`FLAG_SIGNED`). Em teclados como o da Samsung, o "−" não aparece. Quase toda coordenada brasileira
é negativa. "21,73 / 46,93" passa na validação e a previsão é buscada no hemisfério oposto, sem
aviso.
**Correção:** usar `KeyboardType.Text` (ou um botão ±) e avisar quando a coordenada cair fora do
Brasil.

**M11. A área "1.000" é gravada como 1 hectare**
`feature/propriedade/PropriedadeFormViewModel.kt:105`. `replace(',', '.')` faz "1.000" virar 1.0, e
"1.000,5" é recusado.
**Correção (só no campo de área; latitude e longitude usam ponto decimal):** tratar o ponto como
separador de milhar quando o texto casar com `^\d{1,3}(\.\d{3})+(,\d+)?$`, ou mostrar a
interpretação ("= 1.000 ha") antes de salvar.

**M12. Os formulários perdem o que foi digitado quando o Android mata o processo**
`feature/lancamento/LancamentoFormViewModel.kt:96` (o mesmo vale para `PropriedadeFormViewModel`).
O estado fica só em `MutableStateFlow`, sem `SavedStateHandle`. Quem preenche um lançamento e vai
conferir a nota no WhatsApp pode voltar com o formulário vazio.
**Correção:** usar `createSavedStateHandle()` na factory e guardar no handle os campos editáveis.

---

## BAIXO

| # | Onde | Problema | Correção |
|---|---|---|---|
| B1 | `domain/conta/ContaService.kt:204` | A exclusão de conta deixa resíduos: `aviso_chuva.xml`, `privacidade.xml`, `seguranca.xml` e páginas livres do SQLite. A `PoliticaScreen` promete que "os dados são apagados" e que "fica tudo no seu celular", mas há o backup do Google. | Limpar essas preferências, rodar `VACUUM` e mencionar o backup na política. |
| B2 | `data/sessao/BloqueioPreferencias.kt:16` | A sessão nunca expira e o bloqueio por digital vem desligado por padrão. | Oferecer o bloqueio logo após o cadastro. |
| B3 | `domain/conta/ContaService.kt:95` | O cadastro diz na hora "Já existe uma conta com este e-mail", o que contorna a proteção de tempo do login (enumeração local). O `hashFicticio` é `lazy`, então a 1ª tentativa custa 2× PBKDF2. | Aceitar e documentar, ou contar essas consultas no limitador. Inicializar o hash fictício de forma antecipada. |
| B4 | `domain/clima/VerificadorDeChuva.kt:71` | Uma resposta 5xx/429 da Open-Meteo descarta o aviso sem nova tentativa (`Falha -> null`). | Tratar como `SemConexao`: usar o cache e pedir `retry()`. |
| B5 | `aviso/VerificacaoChuvaWorker.kt:35` | Um Worker em andamento durante o logout pode notificar a conta anterior. | Conferir `sessao.usuarioId` antes de cada `mostrar`. |
| B6 | `data/clima/FontePrevisao.kt:26` | O `HttpURLConnection` não é interrompido ao cancelar, o `Mutex` fica preso e não há timeout total. | Usar `runInterruptible` + `withTimeout`. |
| B7 | `feature/clima/ClimaViewModel.kt:227` e `:162` | "Atualizada às 14:05" não diz o dia (o cache vale até 2 dias). "Mostrando a última previsão salva" aparece quando não há nenhuma salva. | Incluir a data quando não for hoje e ajustar a mensagem. |
| B8 | `data/repository/ClimaRepositoryRoom.kt:37` | O parse do JSON roda na main thread a cada emissão do Room. | Usar `.flowOn(Dispatchers.Default)`. |
| B9 | `data/preferencias/AjustesAvisoPreferencias.kt:20` | Os ajustes do aviso valem para o aparelho, não para a conta, e as chaves `ultimo_aviso_<id>` nunca são apagadas. | Prefixar as chaves com `usuarioId` e limpá-las ao excluir. |
| B10 | `domain/clima/RegraDeChuva.kt:79` | "A partir das X" pode apontar para um traço de garoa (0,02 mm). | Usar um limiar mínimo por hora (ex.: 0,1 mm). |
| B11 | `aviso/NotificadorDeChuva.kt:67` e `:94` | `propriedadeId.hashCode()` é usado como id da notificação, e uma colisão faz uma notificação substituir outra. | Usar o id como `tag` em `notify(tag, id, …)`. |
| B12 | `data/repository/UsuarioRepositoryRoom.kt:28` | Criar a conta e adotar as propriedades sem dono não é atômico. | Usar `withTransaction`. |
| B13 | `data/repository/LancamentoRepositoryRoom.kt:46` e `PropriedadeRepositoryRoom.kt:48` | O upsert de lançamento não confere o dono da linha existente. Em propriedade, `donoDe == null` permite adotar uma propriedade sem dono. Na prática exige UUID repetido, então é defesa em profundidade. | Conferir o dono da linha existente na mesma transação. |
| B14 | `data/local/dao/LancamentoDao.kt:92` | `saldoDoPeriodo` não filtra por usuário. Hoje não é usado. | Fazer JOIN com `propriedades.usuario_id` ou remover. |
| B15 | `data/local/dao/PropriedadeDao.kt:17` | `COLLATE NOCASE` não trata acentos, então "Água Limpa" vai para o fim da lista e muda a propriedade padrão. | Ordenar com `Collator` pt-BR. |
| B16 | `data/Desfazer.kt:56` | O `catch (e: Exception)` engole `CancellationException`. A UI pode dizer "não foi possível desfazer" com os dados já restaurados. | Relançar `CancellationException`. |
| B17 | `data/SelecaoAtual.kt:20` | Com o processo vivo na virada do mês, o app abre no mês anterior. O `MainActivity.onStart` cobre parte disso. | Revalidar no `ON_START`. |
| B18 | `data/clima/OpenMeteo.kt:107` | `RespostaInvalidaException` perde a causa original. | Adicionar o parâmetro `cause`. |
| B19 | `feature/lancamento/LancamentoFormViewModel.kt:226` | Salvar uma edição de outro mês muda o mês selecionado, e a lista fecha a busca em que a pessoa estava. | Só mudar o mês em lançamento novo ou quando o mês do lançamento mudar. |
| B20 | `feature/lancamento/LancamentoFormViewModel.kt:112` | Ao editar um id que não existe mais, a tela abre vazia em modo edição e salvar recria o registro com o id antigo. | Mostrar "lançamento não encontrado" e fechar a tela. |
| B21 | `feature/lancamento/LancamentoListaScreen.kt:500` e as barras de Início, Histórico e Sítios | Com o "olho" fechado, o sinal do total e as barras proporcionais ainda revelam informação. | Omitir o sinal e as barras quando `oculto`. |
| B22 | `core/navigation/AppNavegacao.kt:401` e `:419` | Um toque duplo em "Cadastrar propriedade", "Histórico" ou "Conta" empilha duas telas. | Usar `launchSingleTop = true` ou o mesmo guard de `abrirFormulario`. |
| B23 | `feature/propriedade/PropriedadeListaViewModel.kt:81` | O `.catch` encerra o Flow, então a tela para de atualizar e mostra `e.message` técnico. | Usar `mensagemParaUsuario` e reiniciar a coleta. |
| B24 | `feature/lancamento/LancamentoListaViewModel.kt:196` | A busca normaliza todos os lançamentos a cada tecla, na main thread. | Pré-normalizar por emissão e usar `flowOn(Dispatchers.Default)`. |
| B25 | `feature/historico/HistoricoViewModel.kt:123` | Um mês futuro (lançamento agendado) pode virar "melhor mês" ou "mês mais fraco". | Usar `filter { it.temMovimento && !it.futuro }`. |
| B26 | `core/ui/SeletorPropriedade.kt:41` | A área de toque tem menos de 48dp e não tem `role` nem `onClickLabel`. | Usar `heightIn(min = 48.dp)` e `Role.DropdownList`. |
| B27 | `ui/dev/TelaTokens.kt` | Código morto: não é referenciado pela navegação e está desatualizado em relação a `FormasAgro`. | Apagar. |

---

## Testes

A suíte é boa e cobre regras de negócio, ViewModels, migrações e isolamento entre contas. Pontos a
corrigir:

- **`ClimaViewModelsTest` › "cartao de chuva nao some quando um lancamento novo chega"**: só chama
  `aoVoltarMes()`, nenhum lançamento é inserido.
- **`PropriedadeFormViewModelTest` › "dois toques em salvar criam uma propriedade so"**: com o
  dispatcher Unconfined, o primeiro save termina antes do segundo, então o teste não testa o toque
  duplo. O de `LancamentoFormViewModelTest` faz certo, com `CompletableDeferred`.
- **`LancamentoListaBuscaTest` › "busca fica so na propriedade escolhida"**: usa só `all { }`, que
  passa com a lista vazia.
- **`ContaViewModelsTest` › "quinta tentativa errada avisa do bloqueio"**: erra 6 vezes, então não
  prova que o aviso aparece na 5ª.
- **Relógio real:** `LancamentoListaBuscaTest` e `ResumosViewModelTest` usam `LocalDate.now()` e
  podem falhar na virada do mês.
- **Testes de caminho que a UI não usa:** os testes de valor e data usam `aoMudarValor` e
  `aoMudarData`, mas a tela usa `aoTeclar` e o calendário.
- **`FormatacaoTest` › "entrada gigante e cortada em 9 digitos"**: oficializa um corte silencioso
  (valor 10× menor). O certo seria recusar.
- **Falta cobertura** para: `emRealCurto` com centavos (achado A2), área "1.000" (M11), morte de
  processo (M12) e exceção de FK no clima (A3).
- **Fakes em memória × Room:** os repositórios em memória não validam dono, FK nem cascata. Rode
  `connectedAndroidTest` (emulador) antes de cada release. Considere `MigrationTestHelper` para as
  próximas migrações.

---

## Checklist para a Play Store

- [ ] Resolver A1–A3 e, de preferência, M1–M12.
- [ ] **Política de privacidade em URL pública** (exigência da Play). Incluir o contato do
      controlador e mencionar o backup do Android e o envio de coordenadas à Open-Meteo.
- [ ] **Data Safety:** declarar localização (aproximada/precisa), dados financeiros armazenados
      localmente e coordenadas enviadas a terceiro (Open-Meteo), inclusive em segundo plano pelo
      Worker.
- [ ] **Open-Meteo:** a API gratuita é **não comercial**. Se o app for monetizado, é preciso um
      plano pago.
- [ ] **LGPD:** falta exportação dos dados (portabilidade, art. 18, V).
- [ ] Avaliar se `ACCESS_FINE_LOCATION` é necessária, já que a grade da Open-Meteo é de quilômetros e
      `COARSE` basta.
- [ ] Assinatura: usar Play App Signing. Os `.jks` já estão no `.gitignore`, e nenhuma chave aparece
      no histórico.
- [ ] Mover os textos para `strings.xml`. Não bloqueia a publicação, mas permite traduzir depois.
- [ ] Rodar `./gradlew test connectedAndroidTest lint` antes de gerar o AAB.

---

## O que foi verificado e está correto

- **Migrações:** v1 → v3 produzem exatamente o schema de `3.json` (colunas, NOT NULL, defaults,
  FKs com CASCADE, índices). Nenhum `fallbackToDestructiveMigration`.
- **Senha:** o PBKDF2 roda fora da main thread. A troca de senha e a exclusão de conta exigem a
  senha atual.
- **Bloqueio por digital:** não é burlado girando a tela, matando o processo nem pelo botão voltar.
  O `Surface` da capa consome os toques.
- **Isolamento entre contas:** todas as consultas de propriedade e lançamento filtram por
  `usuario_id`.
- **Dinheiro e datas:** valores em `Long` centavos, sem overflow. Datas em `epochDay`, parse STRICT
  (31/02 é recusado). A conversão UTC do DatePicker está correta.
- **Rede:** timeouts, `Dispatchers.IO`, `disconnect()`, `Locale.ROOT` na URL (sem vírgula decimal) e
  coordenadas arredondadas antes de sair do aparelho.
- **WorkManager e notificações:** `enqueueUniquePeriodicWork(KEEP)`, `FLAG_IMMUTABLE`, checagem de
  `POST_NOTIFICATIONS` e `VISIBILITY_PRIVATE` com versão pública.
- **Outros:** nenhum `Log`/`println` com dados sensíveis. O semeador de exemplo só roda em debug. Só
  a Activity de entrada é exportada, e `usesCleartextTraffic="false"`. O contraste de todos os pares
  de cor é ≥ 4,5:1 nos dois temas.

## Pontos fortes

- Hash de senha correto: PBKDF2-HMAC-SHA256 com 600 mil iterações, sal aleatório, comparação em
  tempo constante, NFKC e rehash transparente.
- Mensagem única de erro no login, com hash fictício contra enumeração por tempo.
- Disciplina de migração e testes instrumentados nos pontos críticos (CASCADE, upsert, isolamento).
- Arquitetura MVVM limpa, com repositórios por interface e regras de domínio puras e testáveis
  (relógio injetado, fuso da propriedade).
- `tentar {}` não engole cancelamento, `Desfazer` com `NonCancellable` e proteção contra toque
  duplo nos formulários de lançamento.
- Acessibilidade acima da média: alvos de 48dp, `liveRegion`, leitor de tela respeitando o "olho".
