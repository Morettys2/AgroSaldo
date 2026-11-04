<div align="center">

# 🌱 AgroSaldo

**Controle financeiro e aviso de chuva para o pequeno produtor rural.**

App Android nativo para registrar receitas e despesas por propriedade, acompanhar o saldo do mês e do ano e receber um aviso quando vai chover (“dá para adiar o pivô”).

[![CI](https://github.com/Morettys2/AgroSaldo/actions/workflows/android.yml/badge.svg?branch=prod)](https://github.com/Morettys2/AgroSaldo/actions/workflows/android.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)
![Testes](https://img.shields.io/badge/testes-606-2E7D32)
![Versão](https://img.shields.io/badge/vers%C3%A3o-1.6-1F4D35)

<img src="docs/assets/tela-inicio.png" width="230" alt="Tela de início com o saldo do mês" />
<img src="docs/assets/tela-novo-lancamento.png" width="230" alt="Formulário de novo lançamento" />
<img src="docs/assets/tela-clima.png" width="230" alt="Tela de clima com a previsão de chuva" />

</div>

---

## Sobre o projeto

O AgroSaldo nasceu de um problema real: um produtor rural que precisava saber **quanto entrou e quanto saiu em cada sítio** e **não ligar a irrigação quando a chuva já vem**. O app é usado por ele no dia a dia.

Projeto acadêmico da disciplina de Engenharia de Software (UCE, UNIFAE), com dois requisitos centrais: arquitetura **MVVM** e persistência local com **Room**.

- **100% local:** os dados ficam no celular, sem servidor e sem conta na nuvem.
- **Funciona offline:** só a previsão do tempo usa a internet ([Open-Meteo](https://open-meteo.com/)).
- **Feito para o campo:** fonte grande, alvos de toque de 48 dp, alto contraste e modo escuro.

## Funcionalidades

| | |
| --- | --- |
| 💰 **Lançamentos** | Receitas e despesas por categoria, com teclado numérico de calculadora, busca e filtros |
| 🏡 **Propriedades** | Vários sítios por conta, com cultura, área e localização pelo GPS |
| 📊 **Resumo do mês** | Saldo, entradas e saídas, para onde foi o dinheiro e os últimos lançamentos |
| 📅 **Histórico** | O ano mês a mês, melhor e pior mês e maiores gastos |
| 🌧️ **Clima** | Previsão das próximas horas e dos 7 dias para cada propriedade |
| 🔔 **Aviso de chuva** | Notificação em segundo plano quando vai chover, com limites ajustáveis |
| ↩️ **Desfazer** | Excluiu sem querer? O lançamento ou o sítio volta exatamente como estava |
| 🔒 **Privacidade** | Bloqueio por digital, valores ocultos com um toque, senha com hash forte e consentimento LGPD |

<div align="center">
<img src="docs/assets/tela-lancamentos.png" width="200" alt="Lista de lançamentos" />
<img src="docs/assets/tela-historico.png" width="200" alt="Histórico do ano" />
<img src="docs/assets/tela-sitios.png" width="200" alt="Lista de propriedades" />
<img src="docs/assets/tela-inicio-escuro.png" width="200" alt="Início no modo escuro" />
</div>

## Arquitetura

**MVVM + Repository em três camadas**, como recomenda o guia oficial de arquitetura do Android. As regras de negócio ficam num **módulo Gradle separado, em Kotlin puro**: o compilador impede que o domínio dependa de Android, banco ou tela.

```text
  Tela (Compose)            ViewModel                Domínio (:domain)             Dados (:app)
┌───────────────┐ eventos ┌────────────┐  chama    ┌───────────────────┐ implementa ┌──────────────────┐
│ *Route/*Screen│────────▶│ *ViewModel │──────────▶│ regras puras +    │◀───────────│ Room, prefs,     │
│ (só desenha)  │◀────────│ (UiState)  │           │ interfaces de     │            │ Open-Meteo,      │
└───────────────┘ UiState └────────────┘           │ repositório       │            │ WorkManager      │
                                ▲                  └───────────────────┘            └──────────────────┘
                                └──────────── tudo ligado pelo Hilt (di/) ─────────────────┘
```

- **Fluxo de dados unidirecional:** a tela recebe o estado (`StateFlow<UiState>`) e devolve eventos; ela não guarda regra.
- **Reativo de ponta a ponta:** o Room devolve `Flow`, então uma gravação atualiza todas as telas abertas sozinha.
- **Inversão de dependência:** o domínio define as interfaces dos repositórios e a camada de dados as implementa.
- **Injeção de dependência com Hilt:** ViewModels, Worker e Activity recebem tudo pelo construtor.

## Tecnologias

| Área | Tecnologia |
| --- | --- |
| Linguagem e UI | Kotlin 2.4, Jetpack Compose (Material 3), Navigation Compose |
| Arquitetura | MVVM, Repository, módulo `:domain` em Kotlin puro, Coroutines e Flow |
| Banco local | Room 2.8 (SQLite), com migrações versionadas e testadas |
| Injeção de dependência | Hilt (Dagger), via KSP |
| Segundo plano | WorkManager (aviso de chuva a cada 12 h) |
| Rede | `HttpURLConnection` + `org.json`, só HTTPS, API Open-Meteo |
| Segurança | PBKDF2-HMAC-SHA256 (600 mil iterações), Biometric, `FLAG_SECURE`, backup só criptografado |
| Testes | JUnit 4, Coroutines Test, Robolectric, Roborazzi, fakes em memória |
| Qualidade | ktlint, detekt (zero achados, sem baseline), Android Lint, GitHub Actions |

## Estrutura

```text
AgroSaldo/
├── domain/                  Módulo Kotlin puro: modelos, regras de negócio e interfaces
│   └── src/main/.../domain/
│       ├── model/           Lancamento, Propriedade, Usuario (validam os próprios dados)
│       ├── financeiro/      ResumoFinanceiro: saldo, totais, ranking, histórico
│       ├── clima/           RegraDeChuva e VerificadorDeChuva
│       ├── conta/           ContaService, HashDeSenha, regras de senha e bloqueio
│       └── repository/      Contratos dos repositórios
└── app/                     Módulo Android
    └── src/main/.../agrosaldo/
        ├── feature/         Uma pasta por funcionalidade: *Screen + *ViewModel + *UiState
        ├── data/            Room (entidades, DAOs, migrações), preferências, Open-Meteo
        ├── di/              Módulos do Hilt
        ├── aviso/           Worker, agendamento e notificação de chuva
        ├── core/            Navegação, componentes de UI, utilitários, biometria
        └── ui/theme/        Design system "Safra": cores, tipografia, espaçamentos
```

## Como rodar

**Requisitos:** Android Studio recente e um aparelho ou emulador com Android 8.0+. O Gradle baixa sozinho o JDK que o build usa.

```bash
git clone https://github.com/Morettys2/AgroSaldo.git
cd AgroSaldo
./gradlew assembleDebug        # gera o APK de debug
./gradlew installDebug         # instala no aparelho conectado
```

Ou abra a pasta no Android Studio e clique em **Run**. O app começa vazio: crie uma conta, cadastre um sítio e faça os primeiros lançamentos.

## Testes e qualidade

**606 testes automáticos:** 585 rodam no computador, sem emulador, e 21 de banco rodam também no aparelho.

```bash
./gradlew :domain:test          # regras de negócio (JVM puro, segundos)
./gradlew testDebugUnitTest     # ViewModels, dados, Worker, telas (Robolectric) e capturas (Roborazzi)
./gradlew connectedAndroidTest  # DAOs e migração do banco num aparelho ou emulador
./gradlew ktlintCheck detekt lintDebug
```

- **Regras de negócio** testadas em funções puras, com relógio e threads injetados: testes determinísticos, sem `sleep`.
- **Banco de verdade:** os testes de DAO e de migração usam SQLite real, no computador e no aparelho.
- **Atualização sem perder dados:** a migração é testada campo a campo sobre o schema exato da versão anterior.
- **CI:** cada push na `prod` e cada pull request rodam ktlint, detekt, os testes, o build e o Lint. As capturas de tela dos testes ficam na branch [`ci/capturas`](https://github.com/Morettys2/AgroSaldo/tree/ci/capturas).

## Regras de negócio

| Regra | Descrição |
| --- | --- |
| RN01 | Todo lançamento pertence a exatamente uma propriedade |
| RN02 | Excluir uma propriedade apaga os lançamentos e a previsão dela, com “Desfazer” |
| RN03 | Saldo do período = receitas − despesas |
| RN04 | Aviso de chuva quando, nas próximas 12 h, a chance chega a 60% **ou** a chuva somada chega a 2 mm (limites ajustáveis) |
| RN06 | O valor é sempre positivo; o sinal vem do tipo (receita ou despesa) |
| RN07 | A data pode ser no máximo 12 meses no futuro |

Dinheiro é sempre `Long` em centavos (sem erro de ponto flutuante); datas usam `java.time`.

## Privacidade e segurança

- Os dados ficam **só no aparelho**; a previsão do tempo recebe coordenadas arredondadas, por HTTPS.
- A senha nunca é guardada: só o hash PBKDF2 com sal aleatório, conferido em tempo constante.
- Bloqueio por tentativas imune a mudar a hora do celular; bloqueio opcional por digital.
- Backup do Android só com criptografia de ponta a ponta; sessão e segurança nunca saem do aparelho.
- Consentimento LGPD versionado, e excluir a conta apaga todos os dados.

## Documentação

A documentação técnica completa (arquitetura, cada pasta e arquivo, banco, testes, build e decisões) está em [`docs/DOCUMENTACAO_TECNICA.mdx`](docs/DOCUMENTACAO_TECNICA.mdx).

## Créditos

- Desenvolvido por **João Pedro** ([@Morettys2](https://github.com/Morettys2)): Engenharia de Software, UNIFAE.
- Previsão do tempo: [Open-Meteo](https://open-meteo.com/), dados sob licença [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
- Fonte: [Plus Jakarta Sans](https://fonts.google.com/specimen/Plus+Jakarta+Sans), sob a SIL Open Font License.
