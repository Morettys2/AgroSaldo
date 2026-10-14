# Roadmap — refino do AgroSaldo rumo à Play Store

Saiu de uma auditoria completa do código, feita em outubro de 2026 depois da R4: três revisões separadas, uma de bugs, uma de qualidade de código e uma de requisitos da Play Store. Depois veio a auditoria de `docs/AUDITORIA.md` (A1–A3, M1–M12, B1–B27), toda corrigida. As rodadas seguem em ordem; cada uma é uma branch e um PR, com o guia em `docs/rodadas/`.

Os números de linha citados abaixo são da época da auditoria e podem ter mudado.

---

## ✅ R5 — Bugs (feita)

- Crash ao ler lançamento futuro com o relógio atrasado: o RN07 saiu do `init`.
- Formulário de propriedade:
  - trava contra toque duplo em Salvar;
  - recusa NaN e Infinity;
  - erros viram mensagem em vez de derrubar o app.
- Exclusão e "Desfazer" de propriedade em transação (`excluirComLancamentos` / `restaurar`).
- Notificação de chuva:
  - tocar nela com o app aberto não recria mais a Activity (`singleTop` + `onNewIntent`);
  - não registra o aviso quando não dava para notificar.
- Clima: uma busca por vez (geração).
- Textos da chuva unificados em `core/util/TextosDaChuva.kt`.
- Detalhes: o mês vira sozinho, diálogos com `rememberSaveable`, `numeroBr`, crédito da Open-Meteo com link.

---

## ✅ R6 — Regras de negócio no domínio + dependências limpas (feita, ver `docs/rodadas/R6.md`)

O objetivo é mostrar MVVM "de livro": a regra mora no domínio e é testada sem Android. Cada passo é um commit, sem mudar nada que o produtor veja.

### 0. Conferir a base

Rodar `./gradlew testDebugUnitTest assembleDebug lint` na `main` antes de mexer.

As telas Compose da R5 foram revisadas, mas nunca compiladas fora do Android Studio. Se algo falhar, corrigir primeiro, num commit separado: `fix(build): ...`.

### 1. `domain/financeiro/ResumoFinanceiro.kt` — funções puras, testadas

Hoje a mesma conta está escrita de 4 jeitos diferentes:

| Onde | O que calcula |
|---|---|
| `InicioViewModel` (~linhas 237-262) | totais do mês e top 3 de gastos |
| `HistoricoViewModel` (~117-146) | o mesmo ranking de gastos, de novo |
| `PropriedadeListaViewModel` (~66-70) | saldo, usando `valorComSinal` |
| `LancamentoListaViewModel` (~152-162) | saldo, comparando `tipo ==` |

E a lógica "propriedade selecionada, ou a primeira" se repete em `InicioViewModel`, `ClimaViewModel` e `HistoricoViewModel`.

Criar:
- `resumoDoMes(lancamentos, propriedadeId, mes)`, que devolve receitas, despesas e saldo;
- `rankingDeGastos(lancamentos, quantos)`, que devolve categoria, total e proporção;
- `propriedadeAtual(lista, idSelecionado)`.

Trocar os ViewModels para usar essas funções e portar ou duplicar os testes existentes para testar a função direto.

### 2. `SelecaoAtual` deixa de ser `object` global

Hoje os ViewModels mexem direto no singleton: `LancamentoFormViewModel` ~221 e `PropriedadeFormViewModel` ~158. Os testes também precisam resetá-lo à mão (`ResumosViewModelTest` ~52).

Transformar em `class SelecaoAtual`, criada uma vez no container e passada no construtor de cada ViewModel, como os repositórios.

### 3. Container de dependências

Renomear `Repositorios` para `Dependencias`: ele também guarda serviços (`conta`, `desfazer`, `verificadorDeChuva`). Avaliar se vira uma classe guardada pelo `AgroSaldoApp`, lida nas `Factory` via `this[APPLICATION_KEY]`, que é o padrão do guia oficial de DI manual.

Tirar o `Desfazer` do parâmetro padrão do composable `AppNavegacao` (~141).

### 4. Fakes fora do app

Os 9 `*Memoria` (repositórios e preferências em memória) estão em `src/main` e vão para o APK. Mover para `src/test`, ou para um source set de *test fixtures*.

Mover também `ui/dev/TelaTokens.kt` para `src/debug`.

### 5. Leitura do banco com erro

Os `combine(...).collect` de `InicioViewModel`, `LancamentoListaViewModel`, `HistoricoViewModel` e `ClimaViewModel` não têm `.catch`. Acrescentar o `.catch` e um campo `erro` no UiState.

A tela de erro em si fica para a R7.

### 6. Guia

Escrever `docs/rodadas/R6.md`, explicando por que regra de negócio mora no domínio e o que é DI manual. É o argumento para a professora.

---

## ✅ R7 — Componentes visuais e acessibilidade (feita, ver `docs/rodadas/R7.md`)

- **Componentes compartilhados em `core/ui`:**
  - `BotaoPrincipal`, `BotaoSecundario` e `BotaoDestaque` com `heightIn(min = 56.dp)`. Hoje há uns 12 botões refeitos à mão, com `.height(56.dp)` fixo que corta o texto com fonte grande e alturas 52/56 misturadas.
  - `EstadoVazio`, `Carregando` e `EstadoErro`. Hoje cada tela tem o seu, e o `Aviso` está duplicado em `PropriedadeListaScreen` e `LancamentoListaScreen`.
- **Espaçamentos:** `object Espacos` em `ui/theme`. Hoje são 422 `dp` literais; a margem lateral é 20 em 27 lugares, mas 16, 18, 24… em outros.
- **Seletor de propriedade:** ficar com um só. O `core/ui/SeletorPropriedade.kt`, usado no Histórico, não tem role e tem alvo menor que 48 dp. O `SeletorPropriedadeCabecalho` é o acessível.
- **Acessibilidade:**
  - nome da aba com no mínimo 11 sp (hoje desce a 9 sp);
  - linhas clicáveis com `role` (Conta, Histórico);
  - teclas do teclado numérico com `heightIn`;
  - previews com fonte 2×.
- ~~**Permissão de notificação negada duas vezes**~~: feito na auditoria (M9).
- **Textos:** unificar "Selecione" / "Escolha" / "Nenhuma propriedade".
- ~~**Formulários:** rascunho sobrevivendo ao processo morrer~~: feito na auditoria (M12).
- ~~**Política nova:** Início piscando antes do aceite~~: feito na auditoria (M3).

## ✅ R8 — Padrão profissional (feita, ver `docs/rodadas/R8.md`)

- **Quebrar os arquivos grandes:**
  - `LancamentoFormScreen` (956 linhas) → teclado, campos, topo;
  - `LancamentoListaScreen` (713) → cabeçalho, filtros;
  - `AppNavegacao` (677) → grafo por feature, barra de baixo;
  - `InicioScreen` (660) → cartões;
  - `ClimaScreen` (540).
- **Callbacks longos** viram uma classe `Acoes` (o formulário recebe 18 lambdas).
- **ktlint + detekt** com regras de Compose, partindo de um baseline. Bloco `lint { abortOnError = true }`.
- **GitHub Actions:** `lint detekt testDebugUnitTest assembleDebug` em todo PR.
- **README:**
  - prints;
  - diagrama das camadas;
  - como rodar;
  - convenção de nomes;
  - por que DI manual.
- **CHANGELOG** no formato Keep a Changelog.
- **Testes de UI Compose** das telas com estado falso:
  - teclado → valor;
  - salvar desligado enquanto grava;
  - botões dos estados vazios.
- **Testes do Worker** com `work-testing`, e das `*Preferencias`. A contagem de senhas erradas precisa sobreviver a reiniciar o app.

## R9 — Play Store (futuro, sem data)

- **Política de privacidade num link público** (GitHub Pages), com:
  - nome do responsável e e-mail de contato (LGPD);
  - o IP que a Open-Meteo recebe;
  - como apagar os dados;
  - texto corrigido sobre o backup.
- ~~**Backup** criptografado~~: feito na auditoria (M5).
- **Localização aproximada:** tirar `ACCESS_FINE_LOCATION`. `COARSE` basta para previsão do tempo, e arredondar as coordenadas enviadas para 0,02°.
- ~~**Recentes** sem miniatura com o bloqueio ligado~~: feito na auditoria (M4).
- ~~**Bloqueio por senhas erradas** imune ao relógio~~: feito na auditoria (A1).
- **Release:**
  - AAB (`bundleRelease`);
  - Play App Signing: a chave atual vira só a *upload key*;
  - senhas da chave via `~/.gradle/gradle.properties`;
  - teste do build de release (R8);
  - `localeFilters += "pt-rBR"`.
- **Play Console:**
  - formulário Data safety: localização, criptografada em trânsito;
  - declaração de recursos financeiros ("não oferece");
  - público adulto;
  - classificação indicativa.
- **Conta pessoal nova:** teste fechado com **12 testadores por 14 dias seguidos** antes de pedir produção. Planejar umas 3 semanas.

## Fora do escopo (decidido em out/2026)

- **API própria em NestJS:** o app continua 100% local (Room no celular). Sem sincronização, sem uso em vários celulares e sem "esqueci minha senha" por e-mail. Os ids já são UUID, então dá para retomar se um dia fizer sentido.
- **Controle de irrigação (pivô):** não será implementado. O app usa a previsão só para recomendar adiar a irrigação ("Dá para adiar o pivô").
