# BRAgro -- App Android nativo (Fase 1)

App Android 100% independente da plataforma web: Kotlin puro + Jetpack
Compose, banco local (Room), sem WebView, sem Capacitor, sem nenhum arquivo
compartilhado com o site (`sistema-agro-nextjs/`) ou com o wrapper antigo
(`mobile-app/`, que continua existindo e funcionando em paralelo -- nada
neste projeto novo depende dele nem foi removido dele).

## Por que ele fala com o mesmo backend

"Independente da plataforma" aqui quer dizer: nenhum código compartilhado,
nenhuma WebView, nenhuma ponte JS frágil como a que causou os bugs no
wrapper antigo. Isso é diferente de "sem backend nenhum" -- todo app
(inclusive os apps nativos de banco, e-commerce etc.) precisa falar com
algum servidor pra ler/gravar dados de verdade. Este app fala com:

1. **Supabase Auth (REST)** -- login direto, sem passar pelo site.
2. **Supabase Postgres (RLS)** -- mesma base de dados do site, protegida
   pelas mesmas políticas de segurança por organização já existentes.
3. **`/api/mobile/*` e `/api/offline-sync`** -- três rotas pequenas no site
   Next.js (`sistema-agro-nextjs/src/app/api/mobile/`), criadas pra este
   app. Elas só **reaproveitam** a validação, os cálculos de negócio
   (rateio, vencimento de parcelas, numeração de O.S. etc.) e as regras de
   organização que já existem no site -- nenhuma lógica de negócio foi
   duplicada em Kotlin. Isso evita o pior cenário possível: reescrever à
   mão, em Kotlin, todas as regras acumuladas em ~370 tarefas do site (e
   com o tempo os dois lados saírem batendo diferente).

## O que já funciona (Fase 1)

- Login (Supabase Auth).
- Download automático da configuração dos 16 módulos, listas suspensas,
  fazendas e dados da organização (`/api/mobile/bootstrap`), tudo cacheado
  no Room.
- Tela **Início**: grade com os módulos liberados pro usuário.
- Tela de **lista** (genérica -- serve os 16 módulos): mostra os
  lançamentos cacheados localmente, com botão de sincronizar.
- Tela de **formulário** (genérica -- serve os 16 módulos): campos
  gerados a partir da mesma configuração do site (texto, número, data,
  lista suspensa, caixa de seleção, área de texto).
- **Offline de verdade**: qualquer lançamento criado/editado grava
  IMEDIATAMENTE no banco local (Room) e entra numa fila de sincronização.
  Com internet, tenta sincronizar na hora; sem internet, fica na fila e o
  `SyncWorker` (WorkManager) tenta de novo sozinho assim que a conexão
  voltar (e também toda vez que o app volta pro primeiro plano).
- Os campos calculados pelo servidor (rateio, vencimento, numeração
  automática etc.) não aparecem no formulário -- eles só existem depois
  que o lançamento sincroniza e o app baixa a versão definitiva do
  servidor. Isso é proposital: evita duplicar essas contas em Kotlin.

## Fase 2 (em andamento)

- Tela **Início/Dashboard**: os mesmos KPIs do `/dashboard` do site (em
  aberto no financeiro, itens no estoque, safras em andamento,
  colaboradores ativos, cultura líder, pedidos em atraso), via
  `/api/mobile/dashboard` (nova rota, reaproveita a mesma
  `getDashboardStats()` do site -- sem duplicar nenhuma agregação em
  Kotlin). Cacheado no Room (tabela `dashboard`) para abrir offline com o
  último retrato conhecido. Acesso: ícone de dashboard na barra superior
  da tela Início.
- Tela **DRE**: resumo consolidado por fazenda (custo total, custo/ha,
  custo/sc, receita e margem), com filtros de Safra e Cultura, via
  `/api/mobile/dre` (nova rota, reaproveita `getDreConsolidado()` do site
  -- mesmo motor de rateio, nada recalculado em Kotlin). Cacheado no Room
  (tabela `dre`) para abrir offline com o último resultado. Não inclui
  ainda a árvore de custos por categoria/talhão nem os gráficos da página
  web (fica para uma próxima fase). Acesso: card "Ver DRE" na tela
  Início/Dashboard. Cada card de fazenda expande (ícone de seta) mostrando
  a árvore de custos (Financeiro por categoria, Frota por máquina, Safra
  por talhão/item) via o mesmo `getDreArvoresPorFazendas()` do site. Logo
  abaixo dos Totais, um card de "Composição de custo por categoria" (barra
  horizontal por categoria, sem depender de biblioteca de gráficos) via o
  mesmo `getDreComposicaoPorCategoria()` do site.
- Card **Clima e câmbio (agora)** na tela Início: temperatura/mín/máx do
  dia, Dólar, Euro e cotações de Soja/Milho/Sorgo, via `/api/mobile/weather`
  (rota nova, pública -- dado não é específico de organização, sem
  necessidade de login). Reaproveita `getWeather()`/`getFxRates()`/
  `getCommodityQuotes()` do site. De propósito SEM cache offline (mesmo
  critério do `CachedDashboard` no site): o card simplesmente não aparece
  quando não há conexão, em vez de mostrar um valor desatualizado.
- Tela **Análises**: as 15 análises cruzadas entre módulos (Planejado x
  Realizado x Pago, Custo/ha por fonte, Pedido x Recebimento, Consumo de
  Estoque, Clima x Produtividade, Pragas x Produtividade, Folha x Custo,
  Eficiência de máquina etc.), com filtro de Safra, via
  `/api/mobile/analises` (reaproveita `getAnalisesCruzadas()` do site).
  Renderização **genérica**: em vez de modelar 15 formatos de linha
  diferentes em Kotlin, cada seção do resultado vira uma lista de cards
  com os campos brutos -- mesmo princípio do motor genérico de
  lista/formulário já usado nos 16 módulos. Acesso: card "Ver Análises
  cruzadas" na tela Início/Dashboard.

- Tela **Importar NF-e (XML)**: o usuário escolhe o arquivo XML da nota pelo
  seletor nativo do Android (Storage Access Framework, sem pedir permissão
  de armazenamento), o app lê como texto e manda pra
  `/api/mobile/nfe-preview` (pré-visualização: número, série, chave de
  acesso, emitente, itens com categoria de Estoque sugerida) e, após
  confirmar a fazenda de destino, `/api/mobile/nfe-import` -- ambas
  reaproveitam DIRETO `previewXmlAction()`/`confirmXmlImportAction()`
  (`src/app/(app)/nfe/actions.ts`), o MESMO parser e motor de rateio que a
  tela web usa. Sem cache no Room (ação pontual, não leitura recorrente).
  De quebra, um bug real e pré-existente foi corrigido (beneficia o site
  também): `Invoice.chaveAcesso` não tinha checagem de duplicidade, então
  reimportar o mesmo XML (ex.: falha de rede no meio da confirmação)
  duplicava nota inteira + Estoque + Financeiro -- agora bloqueado quando a
  chave de acesso já existe na organização. Acesso: card "Importar NF-e
  (XML)" na tela Início/Dashboard.

## Confiabilidade

- **Renovação de sessão** (Task #37): antes, só a fila de sincronização
  tentava renovar o access token (via refresh_token) quando um envio dava
  401 -- as telas de leitura (Início/DRE/Análises/listas de módulo)
  simplesmente falhavam silenciosamente nesse caso, mesmo com o aparelho
  online e o refresh_token ainda válido. `TokenRefresher` centraliza essa
  renovação e agora é usada por todas elas. Além disso, `TokenRefreshWorker`
  (WorkManager periódico, a cada ~45min) renova o token em segundo plano
  antes mesmo dele expirar (~1h de vida útil), reduzindo a chance de
  qualquer tela precisar do caminho reativo.

## Romaneio rápido com OCR no aparelho (Task #42)

`romaneios` já era um dos ~18 módulos (`Romaneio` no schema, cálculos como
líquido/desconto por umidade-impureza/sacas/tonelada em
`getDreConsolidado`-style `computeRomaneioFields` no servidor). O site tem a
estrutura pronta pra OCR da foto do ticket
(`lib/services/romaneio-ocr.ts`) mas **nenhum provedor configurado** --
sempre retorna `"NAO_CONFIGURADO"`. Este app native vai além: roda OCR de
verdade, **100% no aparelho**, via Google ML Kit Text Recognition (offline,
sem custo de servidor) -- extrai peso bruto/tara/umidade/impureza/número por
palavras-chave no texto reconhecido (`ui/romaneio/RomaneioOcrParser.kt`) e
pré-preenche o formulário; o usuário sempre confere antes de lançar. A foto
sobe pro MESMO bucket do Supabase Storage que o site já usa
(`RomaneioUploadRepository.kt`, mesma convenção de caminho e política de
RLS -- nenhuma infraestrutura nova). O lançamento em si passa pelo mesmo
motor genérico de registros (`RecordRepository.createRecord`, offline-first)
-- os campos calculados continuam saindo só do servidor ao sincronizar,
igual a qualquer outro módulo. Acesso: card "Romaneio rápido (balança)" na
tela Início/Dashboard.

## Impressão (Task #41)

Investigação prévia: o site não tem nenhuma geração de PDF no servidor (sem
`pdfkit`/`puppeteer`/`jspdf`/`@react-pdf/renderer` no projeto) -- o único
mecanismo de "impressão"/"exportar PDF" que existe hoje
(`components/domain/data-table.tsx`, `exportPdf()`) é 100% client-side:
monta uma tabela HTML pura e chama `window.print()` do navegador. O campo
`Invoice.danfeUrl` (DANFE de NF-e) existe no schema mas não é usado em
nenhuma tela hoje, e a emissão SEFAZ está sem provedor configurado
(`emitirNotaFiscalSefaz` sempre retorna `"NAO_CONFIGURADO"`) -- ou seja, não
há nenhum PDF real de DANFE pra reaproveitar ainda.

O app nativo reproduz o mesmo princípio (tabela HTML local + diálogo de
impressão do sistema), agora com as APIs nativas do Android
(`PrintManager` + `WebView.createPrintDocumentAdapter`) em vez de
`window.print()`. Cobre o caso mais útil: imprimir ou salvar em PDF a lista
de qualquer um dos 16/18 módulos -- usando os registros que a tela de lista
já tem cacheados no Room, sem nenhuma rota nova em `/api/mobile` (ver
`ui/print/HtmlPrinter.kt`). Acesso: ícone de impressora na barra superior de
qualquer tela de módulo (aparece quando há pelo menos um registro
carregado).

## Ícone e splash screen (Task #38)

Ícone adaptativo (Android 8+) com fallback legado (API 24-25), gerado por
código via Pillow (`scripts/gen_icon.py`, sem depender do Android Studio
Image Asset nem de um arquivo de design externo) -- fundo verde da bandeira
(`#2F6F4F`, mesma cor de `ui/theme/Theme.kt`) com um glifo de folha/broto
amarelo (`#F2C037`). Tela de splash via `androidx.core:core-splashscreen`
(mesmas cores/glifo), com fallback automático de comportamento em versões
anteriores ao Android 12. Para redesenhar o ícone, edite
`scripts/gen_icon.py` e rode `python3 scripts/gen_icon.py` de dentro de
`native-app/` (precisa de `pip install pillow`).

## Fase 2: lista original concluída

Todos os itens da lista original da Fase 2 (Dashboard, DRE com árvore de
custos e composição por categoria, Clima/Câmbio/Cotações, Análises
cruzadas, renovação de sessão, ícone/splash, importação de NF-e,
impressão, e Romaneio rápido com OCR) estão implementados. Próximos
incrementos ficam a critério de uso real do app (feedback de quem usa no
campo) -- não há mais nenhum item pendente conhecido nesta lista.

## Itens removidos da lista (investigados, não fazem sentido para o app nativo)

- **Seletor de organização (multi-tenant)**: o banco já suporta (tabela
  `Membership`, N:N entre usuário e organização) e `getAuthContext()` no
  site já lê um cookie `sa_org_id` pra escolher a organização ativa -- mas
  esse cookie nunca é escrito em lugar nenhum, nem no site. Ou seja, não
  existe seletor de organização em NENHUM lugar hoje, nem na web. Construir
  isso primeiro no app nativo exigiria mexer nas rotas `/api/mobile/*`
  existentes, pra atender um caso raro (usuário com mais de uma
  organização) que a própria web ainda não trata -- decisão explícita de
  não construir agora.

- **Relatórios**: a aba foi removida do PRÓPRIO site a pedido do cliente
  (`src/app/(app)/relatorios/page.tsx` hoje só redireciona pro Dashboard) --
  os gráficos "por setor" que ela mostrava já aparecem dentro de cada
  módulo. Não existe nenhuma lógica de backend própria pra reaproveitar;
  não é uma tela "faltando", é uma tela que não existe mais em lugar nenhum.
- **Painel Admin**: existem dois "admin" diferentes no projeto -- o painel
  de configurações da organização (`/configuracoes`, OWNER/ADMIN da conta)
  já é coberto pelo motor genérico de módulos, e o "Painel Admin" citado
  aqui é na verdade `/admin`, a ferramenta interna do DONO DA PLATAFORMA
  (allowlist de e-mail, não papel de organização) para gerenciar TODOS os
  clientes -- lista de orgs, plano/mensalidade, faturas. É uma ferramenta
  de back-office de uso único (o próprio desenvolvedor), não uma feature
  voltada ao usuário final (produtor rural) -- fora de escopo do app
  nativo por natureza, não por falta de tempo.
- **RH completo**: já 100% coberto pelo motor genérico de lista/formulário
  (`DomainListScreen.kt`/`DomainFormScreen.kt`) -- o domínio "rh" no
  registry.ts usa só tipos de campo já suportados (texto/data/número/lista
  suspensa/caixa de seleção/área de texto) e os campos calculados pelo
  servidor (idade, remuneração total, líquido a pagar) já ficam de fora do
  formulário, mesmo padrão de qualquer outro módulo. Não havia nada
  específico de RH faltando.

## Como abrir e rodar

1. Abra a pasta `native-app/` (esta pasta, não `mobile-app/`) no Android
   Studio -- "Open" > selecione `native-app`.
2. Deixe o Gradle sincronizar sozinho (primeira vez demora um pouco,
   baixa as dependências).
3. Rode num emulador ou aparelho físico (Run > app).
4. Login: usa a mesma conta/senha que você já usa no site
   (`sistema-agro-bra.vercel.app`).

Não precisa de nenhuma configuração extra -- as URLs do backend
(`API_BASE_URL`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`) já estão em
`app/build.gradle.kts` (`buildConfigField`), apontando pro site publicado.

## Arquitetura (resumo)

```
data/local/      Room (SQLite) -- toda a persistencia offline
data/remote/     Retrofit -- Supabase Auth + rotas /api/mobile/* do site
data/repo/       Repositorios -- unica fonte de verdade pras telas
sync/            WorkManager -- fila de sincronizacao offline -> online
ui/              Telas Compose (login, inicio, lista generica, formulario generico)
```

O motor de lista/formulário é **genérico** (guiado por `DomainConfig`,
baixado de `/api/mobile/config`) -- os 16 módulos usam as MESMAS duas
telas, exatamente como o motor genérico do site
(`components/domain/data-table.tsx` e `record-form.tsx`). Isso evita
escrever 16 telas quase idênticas à mão, e qualquer campo novo
adicionado no site (em `registry.ts`) aparece aqui automaticamente, sem
precisar mexer no app.
