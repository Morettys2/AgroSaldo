# Changelog

Todas as mudanças importantes do AgroSaldo ficam aqui.

O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), e as versões seguem o `versionName` do app. Cada rodada de trabalho tem um guia detalhado em `docs/rodadas/`.

## [Não lançado]

### Adicionado
- Testes de tela no JVM (Robolectric): teclado numérico, botão de salvar desligado enquanto grava, estados vazios e erro no Início (R8).
- Capturas de tela automáticas das telas principais (Roborazzi), publicadas pelo CI na branch `ci/capturas` (R8).
- Testes do Worker do aviso de chuva e da persistência das preferências (R8).
- ktlint (com as regras de Compose) e detekt, com baseline; Android Lint com `abortOnError` (R8).
- GitHub Actions: compila, testa e roda o lint em todo PR (R6).
- `README`, este `CHANGELOG`, `CLAUDE.md` e `docs/ROADMAP.md` (R6–R8).
- Componentes compartilhados de botão (`BotaoPrincipal`, `BotaoDestaque`, `BotaoTonal`, `BotaoSecundario`) e de estado (`Carregando`, `EstadoVazio`, `FaixaDeAviso`, `EstadoErro`) (R7).
- Escala de espaçamentos `Espacos` (R7).
- Previews com fonte 2x em todas as telas principais (R7).

### Mudado
- Regras de saldo e ranking de gastos no domínio (`ResumoFinanceiro`), testadas sem Android (R6).
- Injeção de dependência manual com o container `Dependencias`, guardado pelo `AgroSaldoApp`; `SelecaoAtual` deixou de ser global (R6).
- Fakes e dados de exemplo saíram do APK para `src/testFixtures` (R6).
- **O app começa vazio**, sem dados de exemplo, também no build debug (R6).
- Um seletor de propriedade só, com 48 dp e role de lista em todas as telas (R7).
- O nome da aba nunca fica menor que 11 sp; as linhas clicáveis da Conta e do Histórico são anunciadas como botão (R7).
- Telas grandes divididas em arquivos menores; a navegação foi dividida por área (`Navegador`, `GrafosDeNavegacao`, `BarraInferior`) e as ações do formulário de lançamento foram agrupadas em `LancamentoFormAcoes` (R8).

### Corrigido
- Erro ao ler o banco não congela mais o Início, Lançamentos, Histórico e Clima: a tela mostra o aviso e tenta de novo sozinha (R6–R7).
- Botões não cortam mais o texto com a fonte do sistema grande (R7).

## [1.5] — 2026-10-08

Rodada R5 (bugs) e correções da auditoria de código (`docs/AUDITORIA.md`: A1–A3, M1–M12, B1–B27).

### Corrigido
- Crash ao ler lançamento futuro com o relógio do celular atrasado.
- Crash ao excluir uma propriedade durante o download da previsão.
- Bloqueio de login imune a mudanças no relógio do sistema, com contador de tentativas por e-mail.
- Valores em reais nunca arredondam centavos.
- Exclusão e "Desfazer" de propriedade numa transação só.
- Notificação de chuva abre o Clima da propriedade do aviso, e o aviso só é registrado depois de notificado.
- Formulários sobrevivem à morte do processo (`SavedStateHandle`).
- Backup do Android só com criptografia de ponta a ponta; telas protegidas de captura com o bloqueio por digital ligado.
- Novo aceite da política de privacidade não pode ser pulado pela notificação.

## R4 — 2026-10-04

### Adicionado
- Visual "Safra", com modo escuro.
- Olho para esconder os valores.
- Busca de lançamentos.
- Teclado numérico próprio para digitar valores.
- Tela de abertura.

## R3 — 2026-10-01

### Adicionado
- Previsão do tempo (Open-Meteo) e aviso de chuva em segundo plano (WorkManager).
- Localização pelo GPS no cadastro da propriedade.
- Bloqueio do app com digital.
- Novo aceite da política quando ela muda.
- Ícone do app.

## R2 — 2026-09-30

### Adicionado
- Login e cadastro de conta, com senha protegida por PBKDF2.
- Consentimento LGPD.

## R1 — 2026-09-30

### Adicionado
- Primeira versão: propriedades e lançamentos (receitas e despesas) com MVVM, Room e testes automatizados.
