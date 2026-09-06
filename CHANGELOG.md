# Changelog -- BRAgro (app nativo Android)

Formato livre (não segue Keep a Changelog à risca), só pra ter um
histórico legível de cada versão publicada. Datas no formato AAAA-MM-DD.

## Como versionar um novo release

Em `app/build.gradle.kts`, dentro de `defaultConfig`:

- `versionCode`: número inteiro que só sobe (nunca reaproveite nem pule
  pra trás -- é o que a Play Store usa pra saber se um APK/AAB é mais novo
  que o publicado). Incremente em 1 a cada envio pra loja, mesmo que seja
  só uma correção pequena.
- `versionName`: o texto que o usuário vê ("1.1.0" etc.). Sugestão:
  `MAIOR.MENOR.PATCH` -- suba MAIOR pra mudanças grandes de fluxo, MENOR
  pra novas telas/recursos, PATCH pra correções.

Depois de mudar a versão, adicione uma seção nova aqui em cima descrevendo
o que mudou (o CI não faz isso sozinho).

## [1.2.48] -- 2026-09-06

- **Blocos individuais de campo (Cotações/Pedidos/Itens da nota) invisíveis
  demais na prática**: usuário desinstalou o app, instalou o 1.2.47 do zero
  e mesmo assim reportou "continua a mesma coisa" em Comparar fornecedores.
  Código já estava certo (só um `CotacaoMultiItemScreen` registrado na
  navegação, sem tela duplicada) -- o problema era o valor escolhido pro
  scrim: `onSurface.copy(alpha = 0.05f)` é ~5% de preto sobre branco
  (RGB~242,242,242), praticamente idêntico ao branco do Card por trás num
  display real, mesmo tendo ficado visível no preview HTML usado pra
  confirmar o entendimento. Subido pra `alpha = 0.12f` nos 3 arquivos que
  usam esse padrão (`CotacaoMultiItemScreen.kt`, `PedidoMultiItemScreen.kt`,
  `FinanceiroItensInline.kt`) -- contraste bem mais perceptível, sem
  precisar de borda (regra do app de não ter bordas em lugar nenhum).

## [1.2.47] -- 2026-09-06

- **DRE: selo "Orçamento estourado"/"Dentro do orçamento" ausente na árvore
  de custos**: pedido do usuário ("analise se os módulos análises e dre da
  plataforma e do app estão alinhados, me parece que não são as mesmas
  informações"). Auditoria de paridade site x app encontrou um gap real:
  `getDreArvoresPorFazendas` (site) já manda o campo `status` ("ACIMA" ou
  não) em cada nó da árvore de custos por talhão, o modelo Kotlin
  `DreRamoItemData.status` já existia pra receber esse dado, mas
  `DreTreeNode` (DreScreen.kt) nunca lia esse campo -- o selo colorido que
  aparece no site (RamoNode, dre-client.tsx) simplesmente não existia no
  app. Corrigido: `DreTreeNode` agora mostra o mesmo selo (vermelho
  "Orçamento estourado" / verde "Dentro do orçamento") abaixo do nome do
  item, só quando `status` vem preenchido -- mesmo critério do site. Análises
  foi auditado no mesmo pedido e está OK: `/api/mobile/analises` é
  passthrough puro de `getAnalisesCruzadas` (mesma função do site, sem
  recálculo em Kotlin) e o renderizador genérico (`AnalisesScreen.kt`)
  percorre as 15 seções e todos os campos sem pular nenhum -- a única
  diferença é de apresentação (gráficos custom no site x cards genéricos no
  app), não de dado faltando.

## [1.2.46] -- 2026-09-06

- **Análises: 3ª varredura de cedilha/acento (persistia)**: screenshot do
  usuário ("corrigir Ç") mostrou "Orcado" em Planejado x Realizado. Auditoria
  campo a campo de novo contra `getAnalisesCruzadas` (site) achou mais 3
  chaves com o mesmo problema (camelCase nunca teve o acento, a quebra
  automática sozinha não tinha como acertar): `orcado`->"Orçado",
  `descricao`->"Descrição", `ocorrenciasPraga`->"Ocorrências Praga" e a
  seção `conciliacaoCaixaVsFinanceiro`->"Conciliação Caixa x Financeiro".

## [1.2.45] -- 2026-09-06

- **Base de Dados: editar o nome da fazenda**: pedido do usuário ("coloque
  para editar também o nome da fazenda") -- antes só área/cultura/
  safrinha/localização podiam ser corrigidas depois do cadastro; um erro de
  digitação no nome exigia excluir e recadastrar a fazenda do zero,
  perdendo o histórico. Agora o nome é editável (site: campo próprio no
  modo de edição da fazenda; app: campo de texto no bloco da fazenda, igual
  aos demais). Nova ação `updateFarmNameAction` (site) e suporte a `name`
  no `update_farm` (app, via `/api/mobile/base-de-dados`) validam o nome
  (mesma regra do cadastro: maiúsculas, 2-80 caracteres, não pode ser
  "TOTAL FAZENDAS"), impedem duplicar o nome de outra fazenda da mesma
  organização, e renomeiam a entrada equivalente na lista suspensa
  "Local" (usada em Safra/Financeiro/Frota etc.) para não deixar duas
  entradas (nome antigo + novo) nem quebrar o dropdown.
  **Limitação conhecida**: lançamentos JÁ SALVOS em outros módulos guardam
  o nome da fazenda como texto, não uma referência -- renomear não
  atualiza esses lançamentos antigos (o rateio deles deixa de bater com a
  fazenda renomeada). Renomear logo após cadastrar, antes de lançar em
  outros módulos, evita esse problema.

## [1.2.44] -- 2026-09-06

- **Base de Dados (app) > Fazendas: layout quebrado (bug real)**: screenshot
  anotado do usuário mostrou o label "Cultura 1"/"Cultura" quebrando letra
  por letra na vertical (quando o campo estava vazio) e valores cortados
  sem reticências (ex.: "SOJA" aparecendo como "SOJ", "SORGO" como "S(").
  Causa raiz: `FarmsCard` colocava 4 campos (2 dropdowns de cultura + 2
  inputs de área) na MESMA linha (`Row` com `weight(1f)` cada), sobrando
  menos de 1/4 da largura da tela por campo -- estreito demais até pra um
  valor de 4 letras, e o Material3 renderiza o label no tamanho GRANDE
  (não a versão pequena flutuante) quando o campo está vazio e sem foco,
  o que sem `maxLines`/`overflow` definidos quebrava caractere a
  caractere. Corrigido reestruturando de "2 linhas de 4 campos" pra "4
  linhas de 2 campos" (bloco de edição por fazenda E formulário "Nova
  fazenda"), dobrando a largura disponível por campo -- mesmo critério já
  usado no SITE (`grid-cols-2` no mobile). Também adicionado
  `maxLines = 1, overflow = Ellipsis` em todos os labels como rede de
  segurança contra esse efeito de quebra letra-a-letra em qualquer tela
  estreita futura.

## [1.2.43] -- 2026-09-06

- **Blocos individuais ainda invisíveis em 3 telas (app)**: mesmo depois do
  fix da 1.2.42, "Propostas dos fornecedores" (Cotações), "Itens da nota"
  (Financeiro, tela real é `FinanceiroItensInlineSection` -- a
  `NotaMultiItemScreen.kt` antiga é código morto, sem rota) e "Itens do
  pedido" (Pedidos, `PedidoLinhaCard`) continuavam mostrando os campos
  soltos, sem bloco visual. Causa raiz: o bloco usava
  `colorScheme.surfaceVariant` como fundo, cor que no tema verde deste app
  calha de ficar praticamente igual ao fundo do Card/seção pai (branco),
  então o bloco existia na estrutura mas ficava invisível na tela --
  "Itens cotados" só parecia certo por coincidência de contraste, não por
  diferença real de implementação. Trocado em TODOS os blocos individuais
  (Cotações, Financeiro, Pedidos) por um scrim `onSurface` com alpha 0.05,
  que sempre contrasta com qualquer fundo por trás. `PedidoLinhaCard`
  (Categoria, Item, Unidade, Qtd. pedida/entregue) e a seção inline de
  Financeiro (Item, Unidade, Quantidade) nunca tinham tido blocos
  individuais antes -- adicionados agora pela primeira vez.

## [1.2.42] -- 2026-09-06

- **Cotações (app) > Comparar fornecedores**: campos de "Propostas dos
  fornecedores" (Fornecedor, Preço unit., Prazo, Condição de pagamento,
  Validade) agora em blocos individuais (fundo próprio), não mais soltos
  dentro de um único card sem separação visual -- mesmo ajuste aplicado
  também em "Vários itens" (CotacaoLinhaCard).

## [1.2.41] -- 2026-09-05

Correções em cima do lote anterior (itens que persistiam ou tinham detalhe
faltando):

- **ProviderIntegrationCard** (Bomba/Drone/Balança/FieldView): removido o
  botão "Fechar" duplicado dentro do diálogo de Bomba/Balança (agora só o
  do diálogo aparece); frase do topo e resumo de informações não usam mais
  letreiro/corte em 1 linha -- quebram em quantas linhas precisarem pra
  mostrar o texto completo.
- **Financeiro**: removido o bloco "Lançamentos" que ficava duplicado
  abaixo do título (o título do topo já virou "Lançamentos"); a lista
  suspensa "Gestão Financeira" agora ocupa a linha sozinha.
- **Análises**: 2ª varredura de ortografia -- "Farm"/"Name" (inglês),
  "talhao"/"funcao"/"lancamentos"/"ultimo"/"atras"/"eficiencia"/"maquina"
  (sem acento/cedilha), "Mm"/"Ton" (unidade maiúscula) e abreviações soltas
  tipo "P"/"R" corrigidas; 5 campos de custo por hectare que ainda
  mostravam número cru agora formatam em R$.
- **Pedidos/Cotações (Novo Lançamento, site)**: campos dos itens agora em
  blocos individuais (fundo próprio), não só grade recortada.
- **Base de Dados (site)**: cadastro de fazenda (nova e edição)
  redesenhado -- 2 linhas de grade exatas (Cultura/Total ha/Cultura 1/ha 1
  e Cultura 2/ha 2/Lat,Lon), campos com largura flexível em vez de pixel
  fixo, margens reduzidas -- nada corta mais.

## [1.2.40] -- 2026-09-05

Segundo lote do mesmo pedido (itens finais do mega-lote de plataforma):

- **ProviderIntegrationCard** (Bomba/Drone/Balança/FieldView): layout
  reordenado -- frase "Acesso automático via prestadora de serviço" no
  topo; abaixo, status ("Não conectado" ou provedor conectado + ✔) com
  seta de recolher; ao expandir, resumo das informações, campo Provedor
  (bloco cheio) com seta, campo API Key/Token (bloco cheio), botão
  Salvar e, mais abaixo, botão Fechar.
- **Financeiro**: nova entrada "Gestão Financeira" na lista suspensa do
  botão Financeiro (barra inferior), separada de "Lançamentos". Abre a
  mesma tela já com o dropdown Gestão Financeira ativo (Contas a
  Pagar/Receber, Conciliado, Fluxo de Caixa, Rateio Direto/Indireto) e
  sem o botão de Lançamentos; barra Dados/Operações/Arquivos continua
  igual nos dois modos. Ícone próprio (carteira) na lista pra não
  confundir com "Lançamentos".
- **Novo Lançamento > Lançar itens NF, Pedidos > Itens do pedido,
  Cotações > Itens cotados/Proposta fornecedores**: grades de campos
  recortadas pra bater exatamente com a quantidade de campos reais por
  linha (sem coluna fantasma nem campo órfão sozinho numa linha quase
  vazia).
- **Análises**: nova varredura -- palavras com "Vs" (comparações tipo
  "planejadoVsRealizado") agora viram "x" ("Planejado x Realizado");
  outras abreviações camelCase corrigidas (Ha→ha, Área, RH, NF, O.S.,
  %, km, CNPJ, CPF, CIF, FOB, kg, pH). Campos de margem por saca e
  conciliação financeira agora mostram moeda (R$).
- **Cobranças > bloco Faturamento**: ícones agora em blocos de largura
  igual com borda vertical dividindo cada um, centralizados (mesmo
  padrão já usado em Dados/Operações/Arquivos).
- **Base de Dados > cadastro de fazenda**: formulário de nova fazenda
  reorganizado em 2 linhas (Cultura/Total ha/Cultura 2/ha na primeira,
  Cultura 3/ha/Lat,Lon na segunda), margens reduzidas pra caber tudo
  sem cortar palavra nem usar letreiro nesses campos.

## [1.2.39] -- 2026-09-05

Mega-lote de correções (mensagem com pedidos de plataforma + Romaneio
Rápido + Análises + O.S. por fazenda):

- **Drone**: card de integração de provedor com blocos individuais
  (Provedor/API Key), igual ao padrão já usado em Frota/Romaneios.
- **Base de Dados**: cada fazenda cadastrada em bloco próprio, campos
  reorganizados em 2 linhas com fonte reduzida (dense) pra caber sem
  cortar.
- **Site**: botão único Tabela/Bloco com ícones invertidos (LayoutGrid
  para "Bloco", Rows3 para "Tabela").
- **Romaneio Rápido (app)**: ícone "Copiar último lançamento" no canto
  superior direito da TopAppBar.
- **Romaneio Rápido (app)**: corrigido crash ao tirar foto do ticket --
  declarado `<queries>` no Manifest para `ACTION_IMAGE_CAPTURE` e o
  arquivo de destino agora é criado antes de gerar a URI (proteção
  contra apps de câmera de alguns fabricantes que falhavam nesse ponto).
- **Bomba/Balança/FieldView**: texto do aviso agora quebra em
  `basicMarquee()` (letreiro) em vez de quebrar linha no meio da
  palavra.
- **Análises**: valores agora mostram moeda (R$) e "%" conforme o campo,
  literal "label" some da lista genérica (vira título do card), demais
  campos usam nome amigável em vez da chave crua.
- **Varredura true/false**: literais "true"/"false" que vazavam pra UI
  em campos sem tipo `checkbox` (renderizador genérico de Análises)
  agora mostram "Sim"/"Não".
- **"Lançar talhão manualmente" → "Lançar talhão"** (botão, site e
  app; o diálogo mantém o texto completo).
- **O.S. por fazenda**: a numeração automática de Ordem de Serviço
  (Safra/Frota) agora tem uma sequência PRÓPRIA POR FAZENDA em vez de
  uma sequência única da organização inteira -- tanto ao pré-visualizar
  o próximo número (antes de salvar) quanto ao salvar de fato. No app,
  o número sugerido agora também se atualiza ao trocar a fazenda no
  formulário, igual o site.

## [1.2.38] -- 2026-09-05

Mega-lote de correções e ajustes (mensagem com 7 imagens, tarefa #424) --
últimos 3 itens implementados nesta versão (os demais itens do mega-lote já
tinham sido cobertos em versões anteriores):

- **Preview automático do próximo número de O.S.** -- pedido do usuário
  ("aplique também no app o que foi aplicado na plataforma do preenchimento
  automático da O.S. posterior"). `DomainFormScreen.kt` agora chama a mesma
  ação `preview-next-os` (endpoint `/api/mobile/module-actions`, já usado
  por outras 9+ ações leves) ao abrir um lançamento NOVO num domínio que tem
  coluna "os" (Safra/Frota/Controle Interno), pré-preenchendo o campo com o
  próximo número em vez de deixá-lo em branco -- mesmo comportamento que já
  existia no site (`previewNextOsAction`).
- **Lentidão percebida nos filtros do dashboard (site)** -- corrigida com
  `useTransition`/`isPending`: o dashboard é `force-dynamic` (não pode ser
  cacheado) e os filtros de Safra/Cultura/fazenda disparavam
  `router.push`/`router.refresh` sem nenhum feedback visual durante a
  espera, dando a sensação de "travado". Agora os selects ficam
  desabilitados e semitransparentes com um spinner até a navegação
  terminar.
- **Área Safrinha por cultura: de campos fixos (Milho/Sorgo) para 2 slots
  livres com dropdown** -- reversão de uma decisão anterior, reconfirmada
  com o usuário nesta sessão (contradição encontrada entre a memória
  persistida e o pedido do mega-lote; usuário escolheu explicitamente
  "Trocar p/ dropdown flexível"). Os campos `areaSafrinhaMilhoHa`/
  `areaSafrinhaSorgoHa` viraram 4 campos (`areaSafrinhaCultura1`/`1Ha`/
  `2`/`2Ha`), cada slot com cultura escolhida via dropdown (mesma lista
  fixa de culturas do site) + área em hectares. Migração de banco preserva
  os dados antigos (Milho -> slot 1, Sorgo -> slot 2) antes de derrubar as
  colunas velhas. Espelhado no site (Base de Dados, Canvas,
  `resolveAreaTotal`) e no app nativo (tela Base de Dados/Fazendas, com 2
  dropdowns `ExposedDropdownMenuBox` substituindo os campos fixos "milho"/
  "sorgo").

## [1.2.37] -- 2026-09-04

- **Forte candidato ao crash "app fecha ao preencher qualquer campo" (confirmado pelo usuário mesmo após instalar a 1.2.36)** -- usuário relatou que, do formulário genérico (Novo/Editar lançamento), SÓ o campo Data conseguia ser preenchido sem o app fechar; qualquer outro campo (texto, select, número, moeda) derrubava o app. O denominador comum: Data é o único campo que pode ser preenchido inteiramente pelo seletor de calendário, SEM abrir o teclado -- todos os outros exigem teclado. `DomainFormScreen.kt` usava `Modifier.basicMarquee()` (API experimental) no título da TopAppBar ("Novo lançamento"/"Editar lançamento"); abrir/fechar o teclado redimensiona a janela do Scaffold, o que pode recalcular a largura do marquee em condição de corrida -- já existe um bug real e confirmado de marquee travando a UI neste mesmo app (ver entrada de tarefa "Reverter marquee dentro de DropdownMenuItem", causava a barra inferior travar). Removido: o texto do título é sempre um destes dois literais curtos e fixos, nunca precisou de letreiro (Ellipsis nunca chega a aparecer). Sem acesso a logcat pra confirmar 100% a causa raiz -- se o crash persistir mesmo na 1.2.37, precisamos de mais detalhe (qual módulo, stack trace se possível).

## [1.2.36] -- 2026-09-04

- **Bloco do Canvas (fazenda única) sem paridade com o site + campo "Safra"
  ausente** -- usuário reportou ("o primeiro bloco do native não tem a mesma
  configuração da plataforma, insira também nas informações a safra"). Duas
  correções:
  1. Quando há só 1 fazenda visível, `CanvasCirclesRow` desenhava só o
     círculo (nome/área/filtro dentro dele) -- faltava o painel de texto ao
     lado (nome maior, área, safra·cultura do filtro, cultura atual e o
     rótulo de status por extenso) que o site já tinha (`canvas-view.tsx`,
     layout "fazenda única", texto à esquerda + círculo à direita). Agora o
     native espelha essa mesma disposição: só a cor da borda do círculo
     indicava o status antes, agora também aparece como texto (badge com a
     mesma cor da borda).
  2. Nenhuma das duas plataformas expunha a Safra atual (ex.: "24/25",
     "SAFRINHA 26") no card do Canvas -- só a Cultura. Adicionado
     `safraAtual` em `lib/services/canvas.ts` (mesmo cálculo que já existia
     pra `culturaAtual`, só que lendo o campo "safra" em vez de "cultura"),
     propagado pela rota `/api/mobile/home`, `CanvasFazendaCardData.kt`
     (native) e exibido nos dois lados: painel de fazenda única (site e
     native) e card de detalhe da fazenda selecionada (site e native).
  3. Ajuste fino após revisão visual do usuário: as linhas de "cultura atual"
     e "Safra X" do item 2 duplicavam a mesma informação já condensada em
     "SAFRINHA 26 · SORGO" (filtroLabel) logo acima -- removidas do painel de
     fazenda única nos dois lados (site + native), mantendo só nome/área/
     filtro/status. O card de detalhe (abaixo do bloco) continua mostrando
     cultura+safra normalmente.
  4. Correção de build: faltava uma chave de fechamento em
     `CanvasCirclesRow` (Kotlin) após separar o layout de fazenda única do
     layout multi-fazenda -- causava `Expecting '}'` no `assembleRelease`.
  5. Ajuste do item 3: sem filtro de safra/cultura selecionado ("Todas as
     safras/culturas"), filtroLabel fica vazio -- removendo culturaAtual/
     safraAtual incondicionalmente, o bloco ficava SEM nenhuma info de
     safra/cultura nesse caso (usuário reportou de volta: "insira a
     informação da safra e cultura"). Agora mostra um OU outro: filtroLabel
     quando há filtro ativo, culturaAtual/safraAtual quando não há.

## [1.2.35] -- 2026-09-04

- **Bug real corrigido: campos monetários travavam em 1 caractere** --
  usuário reportou ("o campo Bruto não consigo digitar mais de um número" no
  site; "não consigo preencher nenhum campo... quando clico em outro campo"
  no app). Causa: a máscara de moeda completava ",00" a CADA tecla digitada;
  como o campo é recomposto a partir desse texto formatado, o cursor sempre
  ia parar no fim de uma string com 2 dígitos fantasmas (",00") que o
  usuário nunca digitou, e a tecla seguinte caía dentro dessa parte decimal
  fantasma (sempre truncada em 2 dígitos) em vez de continuar a parte
  inteira. Corrigido nos dois lados: ",00" só é completado quando o campo
  perde o foco (blur), nunca durante a digitação. No app, o campo monetário
  também ganhou estado local de exibição (antes recalculava a exibição a
  partir do valor cru a cada tecla, o que sozinho já reintroduzia o mesmo
  problema mesmo sem a máscara).

## [1.2.34] -- 2026-09-04

- **Cotações Fornecedores: campo Fornecedor virou lista suspensa (Base de
  Dados)** -- pedido do usuário ("em cotações campo fornecedores crie lista
  suspensa, tem que cadastrar primeiro para acessar o campo"): nos dois
  modos de "Nova Cotação" (Vários itens/Comparar fornecedores), o campo
  Fornecedor era texto livre; agora é dropdown da mesma categoria já usada
  em Pedidos (`entidades_financeiro`) -- precisa estar cadastrado em Base
  de Dados pra aparecer na lista. O formulário de edição de 1 registro já
  usava esse dropdown (sem mudança ali). Espelhado no site
  (`cotacao-multi-item-button.tsx`).

## [1.2.33] -- 2026-09-04

- **"Lançar nota com itens" (Financeiro nativo): aviso mais curto e direto**
  -- pedido do usuário ("seja mais direto, TEXTO LONGO"): o parágrafo de
  aviso em `FinanceiroItensInline.kt` estava mais longo que a versão já
  encurtada no site; agora usa o mesmo texto conciso do site
  (nota-multi-item-button.tsx). A frase corrida "Usa Doc/NF X, Data Y,
  Local Z, Entidade W" também virou uma tira curta "Doc/NF: X · Data: Y ·
  Local: Z · Entidade: W", igual ao formato já usado no site.

## [1.2.32] -- 2026-09-04

- **Cotações Fornecedores: novo modo "Comparar fornecedores" (1 item, N
  propostas)** -- pedido do usuário ("Cotações Fornecedores: múltiplos
  fornecedores por operação", task #404): a tela de nova cotação
  (`CotacaoMultiItemScreen.kt`) ganhou um alternador de modo. "Vários itens"
  é o comportamento original (1 fornecedor, N itens numa submissão);
  "Comparar fornecedores" é o novo -- descreve o item cotado uma vez
  (categoria/item/data/quantidade/unidade) e lança lado a lado o preço de
  cada fornecedor que cotou ele, sem repetir o formulário inteiro trocando
  só o fornecedor. Sem mudança de schema: cada proposta continua virando
  sua própria linha de `CotacaoFornecedor` (mesma tabela flat), e todas
  entram no mesmo grupo de comparação (Categoria+Item) já existente --
  Índice de Vantagem/Avaliação recalculados normalmente. Espelhado no site
  (`cotacao-multi-item-button.tsx`, novo modo) e chamando o novo endpoint
  `/api/mobile/cotacao-comparacao` (nova Server Action
  `createCotacaoComparacaoAction`).

## [1.2.31] -- 2026-09-04

- **Livro Caixa: certificado digital agora é estruturado (Tipo/Emissor/
  Validade), não só uma referência em texto** -- pedido do usuário ("o
  certificado digital não só apenas colocar a referência, substitua este
  campo"): antes um único campo de texto livre (`certificadoDigitalRef`),
  agora 3 campos (tipo A1/A3, emissor, validade real) com badge de status
  (Válido/Vence em Xd/Vencido) calculado a partir da validade, mesmo padrão
  do vencimento de seguro em Inventário/Frota. O arquivo .pfx/.p12 em si
  continua nunca sendo enviado -- só metadado de controle. Espelhado no
  site (`produtor-rural-card.tsx`) e no app (`LivroCaixaScreen.kt`).

## [1.2.30] -- 2026-09-04

- **Início: primeiro bloco (Canvas) com visual próprio, destacado do resto
  da página** -- pedido do usuário, a partir de 4 opções mostradas em
  mockup ("gostei da forma do bloco 3, mas com estilo da 4, agora a cor tem
  que seguir o modo escuro/claro"): o card que envolve os círculos das
  fazendas trocou o retângulo branco de sempre por cantos assimétricos
  (36/8/36/8, quebra a grade reta do resto da Início de propósito) e fundo
  verde cheio (`--primary`/`colorScheme.primary`, já tinha par claro/escuro
  definido -- nenhuma cor nova, só reaproveitado onde antes só aparecia em
  botões). Textos e o painel de import de KML dentro do bloco foram
  ajustados pra continuar legíveis em cima do verde cheio (site e app).
- **Fix de contraste: círculo "ok" quase invisível em cima do bloco verde**
  -- efeito colateral direto da mudança acima: o círculo usava um
  preenchimento verde translúcido pra status "ok", que sumia de vista
  sobre o novo fundo verde cheio do bloco. Trocado por um fundo neutro
  opaco fixo (mesma cor em qualquer status); a cor do status continua só
  na borda e no texto, leitura igual a de sempre.

## [1.2.29] -- 2026-08-29

- **Frota/Romaneios: acesso "Bomba"/"Balança" virou FAB dedicado** -- pedido
  do usuário ("coloque o botão balança acima do botão +, o mesmo faça em
  frota, botão bomba acima do botão +, e exclua os botoes bomba e balança e
  reposicione os outros botoes de dados"). O ícone que ficava dentro do
  bloco "Dados" saiu de lá (os ícones restantes se redistribuem sozinhos --
  `EqualWidthBlockRow` já divide a largura pelos filhos que sobraram, sem
  código extra) e virou um novo `FloatingActionButton` posicionado acima do
  "+", que abre o mesmo card de integração dentro de um `AlertDialog`.
- **Início: círculo único da fazenda agora mostra foto de satélite real e
  abre o Google Earth ao tocar** -- pedido do usuário, a partir de um
  mockup anexado ("retire da lado inferior direito a palavra importar kml,
  sendo que ao clicar no círculo será direcionado para o google earth para
  kml, o círculo será preenchido com o mapa"). O texto/ícone "Importar KML"
  que ficava no rodapé do bloco de fazendas foi removido; agora, quando só
  uma fazenda está em exibição e ela tem latitude/longitude cadastrada
  (Base de Dados), o próprio círculo mostra a imagem aérea real de fundo
  (Esri World Imagery, endpoint público sem chave de API -- mesmo critério
  do osmdroid usado no mapa do FieldView, pra não depender de Google
  Maps/Mapbox pagos) e o toque no círculo abre o Google Earth centrado
  naquele ponto. Fazenda sem localização cadastrada mantém o preenchimento
  de cor simples de sempre (comportamento anterior, sem quebra).
- **Site: `/api/mobile/home` passou a enviar `latitude`/`longitude` de cada
  fazenda** (já existentes no schema desde a 6ª exceção) pro app nativo
  poder montar a foto de satélite e o link do Google Earth acima.

## [1.2.28] -- 2026-08-29

- **Novo: card "Acesso automático via prestadora de serviço" em Frota
  (bomba de combustível) e Romaneios (balança)** -- pedido do usuário ("api
  para bomba de combustivel implemente e api implementado tambem para
  balanca"), aprovado como scaffolding igual ao de FieldView/Drone (não é
  integração real de fabricante: só persiste a credencial escolhida pelo
  usuário por organização, a leitura automática em si ainda depende de
  aprovação de parceiro do fabricante, mesmo aviso permanente do card).
  Ícone "Bomba"/"Balança" no bloco Dados de cada módulo revela o card. Como
  Frota e Romaneios são módulos genéricos (sem tela própria, diferente de
  FieldView/Drone), a integração usa uma rota só no backend
  (`/api/mobile/module-integration`, parametrizada por "modulo") em vez de
  duplicar rota por módulo.
- **Schema: 2 novos valores no enum `IntegrationModule`**
  (`FROTA_COMBUSTIVEL`, `ROMANEIO_BALANCA`) -- migration aditiva (`ALTER
  TYPE ... ADD VALUE`), autorizada explicitamente pelo usuário (7ª exceção
  à regra de não mexer em schema, ver memória do projeto). Reaproveita
  100% a mesma tabela `provider_integrations` já usada por FieldView/Drone/
  SEFAZ -- nenhuma tabela nova.

## [1.2.27] -- 2026-08-29

- **Fix real: build da 1.2.26 quebrada (`Unresolved reference:
  ExposedDropdownMenu`)** -- `FinanceiroItensInline.kt` importava
  `androidx.compose.material3.ExposedDropdownMenu` como símbolo de pacote,
  mas nesta versão do Material3 esse composable só existe como membro do
  escopo de `ExposedDropdownMenuBox` (chamado sem import, igual o resto do
  app já fazia). Removido o import indevido.
- **Mais ícones não centralizados encontrados e corrigidos** -- pedido do
  usuário ("ainda há ícones que não estão centralizados"). Causa raiz
  diferente da 1.2.26: os botões "Colunas" (ColumnsPickerButton), "Período"
  (PeriodoDropdown/GenericPeriodoDropdown) e "Filtros" (BancoDropdown)
  embrulham o LabeledIconButton num `Box` extra só pra ancorar o
  DropdownMenu -- esse `Box`, sem `contentAlignment` explícito, posiciona o
  filho no canto superior-esquerdo (padrão do Box) em vez de centralizado,
  então mesmo com o fix de EqualWidthBlockRow da 1.2.26 esses 3 botões
  específicos continuavam deslocados. Corrigido com
  `contentAlignment = Alignment.Center` nos 4 pontos (ColumnsAndExport.kt,
  FinanceiroScreen.kt x2, DomainListScreen.kt).
- **Vista Tabela: bordas verticais removidas, altura das linhas
  uniformizada** -- pedido do usuário ("retire as bordas verticais das
  tabelas e alinha a altura das linhas uniforme"). Cada célula usava
  `.border(1.dp, cor)` (retângulo completo, 4 lados) -- trocado por uma
  linha só embaixo de cada célula (`drawBehind`), que empilhada forma um
  separador horizontal contínuo sem nenhuma linha vertical cortando as
  colunas. Altura: `Row` com `Modifier.height(IntrinsicSize.Max)` +
  `fillMaxHeight()` em cada célula, pra todas as células de uma mesma linha
  esticarem até a mais alta, em vez de cada uma parar na altura do próprio
  conteúdo.

## [1.2.26] -- 2026-08-29

- **"Lançar nota com itens" embutido na sequência de campos de Novo
  Lançamento (Financeiro)** -- pedido do usuário (achado de auditoria: "não
  foi inserido no native como está na plataforma o módulo lançamentos, está
  faltando adicionar itens na sequência dos campos, como está em
  plataforma"). Réplica do que o site já fazia (nota-multi-item-button.tsx):
  a seção "Itens da nota" aparece INLINE, logo depois do campo Doc/NF,
  lendo Doc/NF/Data/Local/Entidade/Safra/Cultura/Setor/Banco/Forma Pgto./
  Período/Bruto (R$) AO VIVO do mesmo formulário -- sem campo duplicado.
  Substituiu a tela separada antiga (NotaMultiItemScreen.kt, cujo único
  gatilho de navegação tinha sido removido na 1.2.24) que já estava
  desatualizada em relação ao site havia várias rodadas (campos duplicados,
  "Valor unitário" por item que o site já tinha tirado -- agora usa 1 total
  só, o campo Bruto (R$), distribuído pelos itens no servidor). Endpoint
  /api/mobile/nota-multi-item atualizado pra aceitar o novo payload
  (bruto/periodo/safra/cultura/setor/banco/formaPgto diretos), mantendo
  compatibilidade com o payload antigo pra quem ainda não atualizou o app.
- **Blocos de ícone (Gráficos/Filtros/Colunas/Recolher/Coluna etc.):
  centralização real + borda vertical visível** -- pedido do usuário
  (achado de auditoria: "os icones não estão centralizados e sem aborda
  vertical para separar, coloque a borda um pouco mais escura do que a cor
  do retangulo"). Dois bugs reais no `EqualWidthBlockRow`
  (ModuleIconRow.kt): (1) cada célula só centralizava o conteúdo
  verticalmente -- horizontalmente ficava sempre encostado na borda
  esquerda da célula quando o filho media mais estreito que a célula
  inteira (caso de blocos como o dropdown de Banco); corrigido calculando
  o X centralizado igual já acontecia com o Y. (2) a borda usava
  `outlineVariant` do tema, que no visual escuro do app fica quase idêntica
  ao fundo do bloco -- ficava lá, mas invisível. Trocada por uma cor
  calculada (18% de preto misturado na própria cor do bloco), que sempre
  contrasta com o fundo dela mesma.
- **Vista Tabela: linhas "desconfiguradas" (células em branco, sem
  grade visível)** -- mesma causa raiz da borda invisível acima
  (RecordTable.kt também usava `outlineVariant`), MAIS um segundo bug: o
  texto de valor normal de cada célula não tinha `color` explícita, então
  herdava a cor de texto ambiente da tela (não a da célula) -- num fundo
  escuro isso deixava a maioria das colunas ilegível (só os badges de
  status e o "—" de campo vazio, que já tinham cor própria, apareciam).
  Corrigido com `color = onSurface` explícito + mesma borda mais escura do
  item acima.
- **FieldView: mais espaço entre "Acesso automático" e as abas, e entre
  "Importar KML/KMZ" e "Lançar talhão manualmente"** -- pedido do usuário.
  20.dp de respiro em cada um dos dois pontos (antes: 0 e 8.dp).

## [1.2.25] -- 2026-08-29

- **Vista Tabela estendida a Operações, Análises, DRE e Livro Caixa** --
  pedido do usuário ("estenda para todos"), continuação da 1.2.24 que
  tinha deixado esses 4 módulos de fora por não terem uma lista "achatada"
  de registros individuais editáveis. Reaproveitadas as colunas/funções de
  exportação que cada módulo já tinha pra Excel/PDF/Imprimir
  (`OPERACOES_EXPORT_COLUMNS`/`operacoesExportRecords`,
  `ANALISES_EXPORT_COLUMNS`/`analisesExportRecords`,
  `DRE_EXPORT_COLUMNS`/`dreExportRecords`,
  `LIVRO_CAIXA_EXPORT_COLUMNS`/`livroCaixaExportRecords`) como fonte de
  dados da tabela, sem escrever mapeamento novo. Como os 4 mostram dados
  agrupados/calculados (não um registro único editável por linha), a
  tabela aparece sem a coluna de Ações (sem Ver/Editar/Excluir). Em
  Operações o botão Tabela/Coluna é só ícone (o cabeçalho dessa tela usa
  ícones sem rótulo em todos os botões); nos outros 3 é o mesmo botão
  ícone+rótulo dos demais módulos.
- **Ainda fora da vista Tabela**: FieldView (Talhões/Máquinas/Fazendas --
  dados em JSON genérico, sem um "molde" fixo de colunas hoje), Drone
  (não existe nenhuma infraestrutura de exportação/colunas pra
  reaproveitar -- precisaria ser construída do zero) e as 3 telas de
  lançamento com múltiplos itens (Nota com Itens, Pedidos, Cotações --
  cada linha ali é um item sendo editado ao vivo pra compor um lançamento
  novo, não um registro já salvo; a vista de tabela é só leitura, então
  não serve pra esse caso sem redesenhar a tela). Preciso de decisão do
  usuário se vale a pena construir isso também.

## [1.2.24] -- 2026-08-29

- **Blocos de ícone: retângulo reto, sem "chip" duplo dentro do bloco** --
  achado de auditoria do usuário ("os blocos ícones e rótulos continuam
  desconfigurados... não foi aplicado a forma de retângulo"). Causa real:
  cada ícone (`ModuleIconButton`/`LabeledIconButton`) tinha seu PRÓPRIO
  `Card` (cantos arredondados + fundo) dentro do retângulo já
  arredondado/bordado do bloco (`EqualWidthBlockRow`) -- um Card dentro de
  outro, então cada célula ainda parecia um chip flutuante em vez de uma
  fatia de um retângulo único. Removido o `Card` individual (vira só
  `Column` + `clickable`, sem fundo/forma próprios); `EqualWidthBlockRow`
  ganhou cantos retos (sem `RoundedCornerShape`), fundo preenchido e
  centralização vertical do conteúdo. Vale pra todos os blocos
  Dados/Operações/Arquivos/Filtros/Período/Gráficos/Calculadoras/etc. em
  todos os módulos (componente único e compartilhado).
- **Removido o ícone "Nota com itens" do topo de Novo Lançamento
  (Financeiro)** -- pedido do usuário ("exclua o ícone de adicionar itens
  do app native"), pra ficar no mesmo modelo enxuto do módulo
  Lançamentos da plataforma (site não tem esse atalho no cabeçalho do
  formulário). Mantido só o ícone Copiar.
- **Gestão Financeira: seletor de visão virou retângulo reto, com cor nova
  no estado não selecionado** -- pedido do usuário ("altere o formato para
  retangulo e a cor do seletor"). Antes usava
  `SegmentedButtonDefaults.itemShape` (pontas arredondadas nas
  extremidades da fileira) com verde translúcido no estado inativo; agora
  `RectangleShape` em todos os segmentos e o estado inativo vira neutro
  (surface/onSurface, mesmo tom dos blocos de ícone comuns) -- só o
  segmento selecionado continua em verde sólido.
- **Nova vista Tabela (grade real) em todos os módulos com lista de
  lançamentos** -- pedido do usuário ("insira também o ícone e rótulo
  tabela intercalando com coluna, no mesmo modelo de expandir e recolher
  no mesmo botão, coloque em todos os módulos"). Um botão único
  (`RecordTable.kt`) alterna ícone+rótulo Tabela/Coluna, igual ao padrão
  já usado em Expandir/Recolher -- colunas fixas (mesmo conjunto de
  `visibleKeys` da vista em cards) com rolagem horizontal compartilhada
  entre cabeçalho e linhas, e as mesmas ações Ver/Editar/Excluir em ícones
  compactos no final de cada linha. Aplicado no módulo genérico
  (`DomainListScreen.kt`, cobre a maioria dos módulos: Safra, Clima,
  Planejamento, Colheita, Frota, Estoque, RH, Cobranças, Pragas,
  Receituários, Controle Interno, Cotações Grãos, Câmbio, NFS-e etc.) e no
  Financeiro (`FinanceiroScreen.kt`). Módulos sem uma lista "achatada" de
  lançamentos (Operações agrupadas, Análises, DRE, Livro Caixa, FieldView,
  Drone, Pedidos/Cotações multi-item) não têm um equivalente direto pra
  essa alternância e ficaram de fora desta rodada.

## [1.2.23] -- 2026-08-28

- **Fix real (bug reportado pelo usuário): botões da barra inferior com
  lista suspensa (Safra, Financeiro, RH, Módulos) paravam de responder ao
  toque** -- causado pelo letreiro (marquee) da v1.2.22: `DropdownMenu`/
  `DropdownMenuItem`/`ExposedDropdownMenu` do Material3 1.2.1 mede o
  conteúdo de cada item com largura não-limitada numa passada interna, e
  `Modifier.basicMarquee()` exige largura máxima finita -- a combinação
  travava a abertura do menu silenciosamente (sem crash visível, "nada
  acontece"). Confirmado reproduzindo em build debug E release (não era
  R8/minify) e confirmado que os botões de acesso direto sem dropdown
  (Frota, Estoque) continuavam funcionando -- isolou o problema pro
  `DropdownMenuItem` especificamente. Revertido só esses casos pra
  "..." (`TextOverflow.Ellipsis`, sem `basicMarquee`) em todos os módulos
  que usam dropdown/combobox (barra inferior, seletor de fazenda, filtro
  global, filtros de coluna/período, comboboxes de Entidade/Fazenda/Banco
  em Pedidos/Cotações/NF-e). O letreiro continua ativo em tudo que NÃO é
  dropdown (abas, blocos individuais, KPIs, cards) -- não foi tocado.

## [1.2.22] -- 2026-08-28

- Letreiro (marquee) em vez de "..." em todo texto de uma linha só que
  ultrapassa o espaço disponível -- pedido do usuário ("tem como aparecer
  como um letreiro se movendo? aplique em todo app que tiver fontes
  cortadas"). `Modifier.basicMarquee()` só anima quando o texto realmente
  não cabe; se cabe, fica parado normal. Aplicado nos ~61 pontos que antes
  usavam `TextOverflow.Ellipsis` com uma linha só (blocos individuais,
  abas, dropdowns, KPIs, cards etc.), em 21 arquivos. Textos de parágrafo
  com 2+ linhas (ex.: descrição de alerta) continuam com "..." normal --
  letreiro é só pra uma linha.

## [1.2.21] -- 2026-08-28

- Blocos individuais dentro de cada categoria (Dados/Operações/Arquivos e
  equivalentes) agora têm largura igual entre si, borda vertical separando
  cada um e gerador de caracteres (ellipsis) automático quando o rótulo não
  couber -- novo `EqualWidthBlockRow` (`ui/domain/ModuleIconRow.kt`),
  aplicado em todos os módulos: DomainListScreen (genéricos + Cobranças),
  FinanceiroScreen, DreScreen, LivroCaixaScreen, AnalisesScreen e
  ControleInsumosScreen.
- Gerador de caracteres app-wide: rótulos de `SegmentedButton`
  (Dados/Operações/Arquivos) e itens de dropdown (seletor de setor/módulos
  na barra inferior, seletor de fazenda, filtros de período/coluna
  genéricos) que ultrapassavam o bloco ou a tela agora truncam com "...".

## [1.2.20] -- 2026-08-26

- Financeiro e Colheita: "Área Total (ha)" agora também respeita
  `areaSafrinhaHa` (mesmo fix já aplicado a Safra/Frota na 1.2.19) --
  faltava nos dois módulos, usuário reportou que continuavam mostrando a
  área cadastral cheia em vez da área safrinha.
- Canvas: círculo não troca mais de área sozinho sem nenhum filtro de
  Safra/Cultura selecionado (bug real: mesmo com "Todas as safras/Todas as
  culturas" escolhido, o círculo às vezes já mostrava a área safrinha).
  Também passou a detectar safrinha pelo campo Safra de cada lançamento da
  fazenda (não só pelo dropdown de filtro), então filtrar só por Cultura
  (ex.: Sorgo) já é suficiente quando todos os lançamentos daquela cultura
  forem de uma safra "SAFRINHA ...".
- Base de Dados: novos campos opcionais "Área safrinha - Milho (ha)" e
  "Área safrinha - Sorgo (ha)" por fazenda -- exceção de schema autorizada
  (`Farm.areaSafrinhaMilhoHa`/`areaSafrinhaSorgoHa`, ver MEMORY.md), pra
  quando a fazenda planta os dois na mesma safrinha, cada um ocupando uma
  parte diferente do total. O Canvas e o "Área Total (ha)" de
  Safra/Colheita/Financeiro usam o campo certo conforme a cultura filtrada
  (ou a cultura dos lançamentos, se só houver uma).
- Base de Dados: badge da área safrinha mostrava "100 ha safrinha" (o
  rótulo colado no valor); agora mostra só o número, com "safrinha"/"milho"/
  "sorgo" como legenda pequena abaixo.
- Corrigido erro genérico do sistema ("An error occurred in the Server
  Components render... omitted in production builds") ao tentar recadastrar
  uma fazenda com nome "TOTAL FAZENDAS" -- agora mostra a mensagem real de
  validação.
- Início (Canvas): o rótulo abaixo da área do círculo ("da safra
  selecionada") agora reflete de fato qual filtro está ativo -- "da cultura
  selecionada" quando só Cultura está escolhida, "da safra/cultura
  selecionada" quando os dois, em vez de sempre dizer "safra" mesmo sem
  nenhuma safra escolhida.
- Início: "X fazendas" no resumo acima do Canvas agora usa a mesma lista já
  filtrada pelo seletor de fazenda do Canvas, em vez de uma contagem geral
  da organização que ficava dessincronizada do filtro.
- Base de Dados: campo genérico "Área safrinha (ha)" removido da tela
  (badge e inputs) -- pedido do usuário, agora só aparecem "Área TOTAL
  (ha)", "milho" e "sorgo". O valor antigo continua no banco e no fallback
  do Canvas/"Área Total" pra fazendas que já tinham esse campo preenchido.
- Corrigido bug real de layout em Base de Dados > Fazendas: a linha de cada
  fazenda (nome + TOTAL/milho/sorgo + salvar + excluir) estourava a largura
  da tela -- a soma das larguras fixas dos campos numéricos já passava de
  280dp, sem sobrar espaço nenhum pro nome (que ficava espremido a quase
  0dp de largura, quebrando caractere por caractere numa coluna gigante e
  quase invisível) e cortando o ícone de excluir da tela. Agora o nome
  fica numa linha própria (sempre visível, com "..." se for muito longo) e
  os campos numéricos ficam numa segunda linha com rolagem horizontal --
  nunca mais estoura, em qualquer tamanho de tela.
- Início: tour de orientação de 3 telas (Canvas/filtros, barra inferior de
  módulos, atalhos "Copiar último lançamento"/Romaneio/Abastecimento),
  mostrado uma única vez na primeira abertura da Início depois do login
  (Task #296/#344, auditoria 2026-08-28 item 1 -- "onboarding leve no app
  nativo"). Diferente do wizard de 3 passos do site (que cria organização),
  este é só um tour: quem usa o app já entra com conta pronta.
- Base de Dados: novo campo "lat, lon" por fazenda (`Farm.latitude`/
  `longitude`, 6ª exceção de schema autorizada, ver MEMORY.md) -- localização
  real usada pelo clima (Dashboard, /clima, "Preencher com o clima de hoje"
  no site) em vez do fallback fixo (Tupaciguara/MG), assim que cadastrada
  aqui ou no site. O clima do próprio app nativo (rota pública
  `/api/mobile/weather`) continua no fallback fixo por enquanto -- essa
  rota não sabe de qual organização é a chamada (auditoria 2026-08-28).
- Removido `DashboardScreen.kt` -- tela órfã sem rota nenhuma no NavHost,
  sobra de uma versão anterior da Início (achado da varredura profunda de
  código morto, auditoria 2026-08-28 item 2).

## [1.2.19] -- 2026-08-25

- Operações: ícone de editar em cada lançamento (Financeiro e Safra) agora
  navega direto pra tela de edição do registro (`domain/{id}/edit/{recordId}`),
  em vez do único link genérico "Ver em Safra" que existia antes.
- Início: removido o bloco de Estágio da safra + Sugestão adaptativa
  (pedido do usuário -- não é mais necessário na Início).
- Financeiro (Gestão Financeira): removido o fundo do bloco externo único;
  os blocos de ícone individuais (visualização) agora têm fundo verde
  escuro (`BrGreen`) com fonte branca, sem alterar o `ModuleIconButton`
  compartilhado usado no resto do app.
- Frota: barra de progresso da próxima revisão agora mostra também quantos
  dias faltam (ou quantos dias está atrasada), espelhando o mesmo texto do
  site.
- Início (Custo médio/ha): legendas do detalhamento por safra/cultura
  viram `FlowRow` (quebra horizontal até o fim do bloco, só desce de linha
  se necessário) em vez de empilhadas verticalmente.
- Início (Canvas): o círculo de cada fazenda agora reflete a área de fato
  coberta pelos lançamentos de Safra da safra/cultura filtrada (ex.:
  safrinha ocupa um círculo menor que a safra verão na mesma fazenda), em
  vez de sempre mostrar a área cadastral total. Sem filtro de safra/cultura
  ativo, continua mostrando a área total, igual antes.
- Base de Dados: novo campo opcional "Área safrinha (ha)" por fazenda, ao
  lado da área total -- exceção de schema explicitamente autorizada pelo
  usuário (`Farm.areaSafrinhaHa`, ver MEMORY.md). Quando preenchido, o
  círculo do Canvas passa a usar esse valor (em vez da soma de hectare dos
  lançamentos) sempre que a safra selecionada no filtro bater "SAFRINHA
  ...". Sem preenchimento, cai no fallback anterior.

## [1.2.18] -- 2026-08-25

- Barras de progresso nos 5 módulos que ganharam a mesma feature no site
  (pedido do usuário: "a barra de progresso deve ser aplicada em módulos
  que envolvem metas, limites, etapas contínuas ou consumo de recursos" +
  confirmação "sim" pra replicar no app): Colheita (% colhido, cor pelo
  status do lançamento), Pedidos (% entregue, cor pelo status),
  Planejamento de Safra (área realizada vs. planejada, e custo realizado
  vs. orçado), Frota (dias desde a Entrada até a próxima revisão, janela
  de 180 dias) e Contratos (Valor Pago vs. Valor do contrato -- novo campo
  manual "Valor Pago", exceção de schema explicitamente autorizada pelo
  usuário). Tudo calculado no cliente (Kotlin) em cima de colunas que já
  chegavam do servidor -- nenhum campo novo, nenhum cálculo duplicado além
  do já existente no site (`domainProgressCellInfo` em StatusStyle.kt é o
  espelho exato de `progressCellInfo` em data-table.tsx). Mesmos limiares
  de cor já usados no app (verde/amarelo/vermelho) e no site.

## [1.2.17] -- 2026-08-24

- Nova barra de progresso "Área utilizada" no card "Operação" (Safra
  agrupada, site e app) -- pedido do usuário: "em safra modulo operações
  preciso que crie uma barra de progresso da area total com a areas
  parcial. e aplique o mesmo padrao da plataforma em native". Compara o
  hectare lançado na própria operação (área parcial) com a Área Total
  cadastrada da fazenda (Farm.areaHa, mesmo lookup por nome usado em
  computeSafraFields/Safra). Calculado 1x no servidor (getOperacoes,
  services/operacoes.ts) e só consumido no app -- nenhuma lógica duplicada
  em Kotlin. Cores seguem o padrão de threshold já usado em Estoque
  (verde/amarelo/vermelho): acima de 100% (área lançada maior que a
  cadastrada) fica vermelho como sinal de possível divergência de
  cadastro. Só aparece quando o "Local" da operação bate com uma fazenda
  cadastrada com área preenchida.

## [1.2.16] -- 2026-08-24

- Corrigido bug real no card "Operação" (Safra agrupada por Safra+Cultura+
  Local, site e app): a barra "Progresso da janela" podia mostrar números
  como 650% -- pedido do usuário, com diagnóstico correto: o cálculo
  comparava tempo decorrido (Date.now() - início) com uma "janela"
  (dataFim - início), mas "dataFim" ali NÃO é um prazo planejado -- é a
  data em que cada O.S. foi de fato concluída (SafraRegistro.dataFim, ver
  computeSafraFields em services/safra.ts). Como o cálculo continuava
  comparando com a data de HOJE mesmo depois da operação já ter terminado
  há muitos dias, o "progresso" crescia sem limite pra sempre (13 dias
  decorridos ÷ 2 dias de janela = 650%). Trocado por progresso OPERACIONAL
  de verdade: % de O.S. concluídas sobre o total do grupo (osConcluidas/
  osTotal), que nunca ultrapassa 100% por construção (é uma razão de
  contagens, não de tempo) -- quando todas concluídas, badge verde
  "Concluído" com a barra travada em 100%; quando nenhuma concluída e
  alguma está com mais de 5 dias em andamento (mesmo critério de "ATRASADO"
  já usado no Status de Safra), badge vermelho "Atrasado" em vez de deixar
  crescer. Calculado 1x no servidor (getOperacoes) e reaproveitado igual
  pelo site e pelo app -- nenhuma lógica duplicada em Kotlin.

## [1.2.15] -- 2026-08-24

Varredura de auditoria completa (site + app) e implementação dos itens
levantados (pedido do usuário: "faça uma varredura completa profunda...
para sugestões de implementação e exclusão" + "implemente tudo").

- "Copiar último lançamento" nas 3 telas "vários itens" (Pedidos, Cotações,
  Nota com itens): Pedidos e Nota nunca tinham essa conveniência no app;
  Cotações tinha um comentário dizendo que precisava de endpoint mobile
  novo -- não precisava (RecordRepository.mostRecent(domainId) já lê do
  cache local pra qualquer domínio, mesmo mecanismo genérico usado em todo
  o resto do app). Site também ganhou o mesmo botão em Pedidos
  (pedido-multi-item-button.tsx), que só Cotações tinha.
- Pedido Rápido (Controle de Insumos): corrigido pra abrir a LISTA de
  Pedidos filtrada pelo item, igual o site faz de verdade -- antes pulava
  direto pro formulário em branco sem usar o item recebido.
- Credenciais de integração de provedor (FieldView/John Deere/DJI/SEFAZ)
  agora são criptografadas em repouso (AES-256-GCM) antes de salvar no
  banco -- sem mudar a coluna do banco (String? continua igual), só o
  formato do conteúdo salvo. Compatível com credenciais antigas já salvas
  em texto simples.
- Card de integração de provedor: mensagem sobre a sincronização depender
  de aprovação de parceiro do fabricante agora fica sempre visível quando
  conectado, não só depois de clicar em "Testar sincronização".
- Emissão de NFS-e (Focus NFe) agora funciona pelo app nativo: botão
  "Emitir NFS-e" na lista do módulo NFS-e (só quando ainda não emitida),
  réplica do mesmo botão do site -- reaproveita o endpoint genérico
  /api/mobile/module-actions (mesma Server Action do site, nenhuma lógica
  de emissão duplicada em Kotlin).
- QuickCaptureBar.kt/quick-capture-bar.tsx e ModulosScreen.kt confirmados
  órfãos (zero referências reais fora do próprio arquivo) -- remoção via
  comando manual, ver instruções da conversa.

## [1.2.14] -- 2026-08-22

- Acessos e Segurança (app): adicionada a categoria "Início (blocos do
  painel)" com um switch PRÓPRIO por bloco (Filtros, Canvas, Estágio,
  Sugestão, Mural, Alertas, Monitor, Insights, KPIs, Clima, Câmbio,
  Cotações, Destaques) -- bug de paridade real encontrado (usuário: "há
  ainda campos que não estão selecionados, estão em bloco único"): essa
  categoria já existia no site (seguranca-client.tsx/INICIO_WIDGETS) desde
  a Task #214, mas nunca tinha sido replicada no app -- os blocos da Início
  ficavam de fora da tela de Acessos por completo, sem forma de restringir
  bloco a bloco pra um papel customizado.

## [1.2.13] -- 2026-08-22

- Início: quando o filtro global reduz o Canvas a 1 fazenda só (org com uma
  única fazenda ou fazenda selecionada no pill de filtro), o círculo grande
  agora fica CENTRALIZADO no bloco -- pedido do usuário ("em início quando
  selecionar uma fazenda centralize-a dentro do bloco"). Causa: o círculo
  único ficava no mesmo Row com horizontalScroll usado pra lista de várias
  fazendas -- sob scroll horizontal o Row mede os filhos com largura
  infinita (é assim que o conteúdo consegue ficar maior que a tela pra
  rolar), então Arrangement.Center não tinha nenhum espaço sobrando pra
  distribuir e o círculo ficava colado na borda esquerda mesmo tentando
  centralizar. Com 1 fazenda só o scroll nem é necessário -- removido nesse
  caso, e Arrangement.Center passa a centralizar de verdade.
- Avisos de Safra, Planejamento de Safra, Frota, Estoque, RH e Controle
  Interno (bloco "Novo Lançamento") reescritos mais diretos e objetivos --
  pedido do usuário. Vem do backend (registry.ts), então atualiza os dois
  (site e app) sem precisar mudar nada em Kotlin.

## [1.2.12] -- 2026-08-22

- Calculadoras (Semeadura/Pulverização/Adubação/Colheita/Financeiro):
  CalcCard agora usa fundo verde translúcido (colorScheme.primary a 15%),
  mesmo padrão já usado no bloco externo de Gestão Financeira/Cobranças --
  pedido do usuário ("em calculadoras deixe o fundo verde e os campos
  brancos como está no restante do app"). Campos continuam brancos por
  dentro (appFieldColors/colorScheme.surface), então o contraste fica igual
  ao resto do app.
- Corrigido bug real (3ª vez que o usuário relatou o mesmo sintoma, mesmo
  após o fix de rememberSaveable em 1.2.10): "mesmo com o download
  concluído não aparece a mensagem concluído". Causa: o BroadcastReceiver
  só recebe o aviso do DownloadManager enquanto o processo do app está
  vivo -- se o Android mata o processo em segundo plano durante o download,
  ou atrasa a entrega do broadcast (Doze/economia de bateria), a conclusão
  passa em branco e o app fica preso em "Baixando..." pra sempre, mesmo com
  o APK já baixado de verdade. Adicionado um polling de segurança
  (LaunchedEffect a cada 1,5s consultando DownloadManager.query() direto
  pelo downloadId) que não depende de broadcast nenhum -- cobre inclusive
  reabrir o app depois do download ter terminado com o app fechado.
- Acessos e Segurança: contraste do switch do próprio OWNER (sempre
  travado/desabilitado, pra não se autodesativar) corrigido -- pedido do
  usuário ("este botão de selecionar está ativo? parece que ele tá
  apagado"). O Material3 usava cores padrão de "desabilitado" iguais pra
  ligado e desligado, deixando um switch realmente ativo com a MESMA
  aparência apagada de um inativo. Agora tem cores explícitas pro estado
  desabilitado+ligado (verde da marca, um pouco mais claro), então o OWNER
  continua vendo claramente que está ativo.

## [1.2.11] -- 2026-08-22

- KPI Cotações Grãos reescrito pra ficar IDÊNTICO ao card do site (dashboard
  do site, grid-cols-3 divide-x) -- pedido do usuário ("coloque o kpi
  cotações grãos como está na plataforma sem abreviar nada"). Antes: 3
  linhas horizontais (Soja/Milho/Sorgo) com "R$ valor / 60kg / sacas"
  (unidade abreviada) e sem a praça de referência. Agora: 3 colunas lado a
  lado, cada uma com ícone+rótulo, preço "R$ valor" sem nenhuma unidade do
  lado, variação% com seta, e a praça de referência (Grão Direto) por
  extenso -- exatamente como o site renderiza cada commodity.

## [1.2.10] -- 2026-08-22

- KPI Cotações Grãos: cada linha (Soja/Milho/Sorgo) agora se distribui em 3
  colunas de peso igual (rótulo | preço centralizado | variação), ocupando
  o bloco de ponta a ponta -- antes tudo ficava colado à esquerda, sobrando
  um vão vazio à direita.
- Corrigido bug real: em Configurações, o download do APK às vezes ficava
  preso em "Baixando..." sem nunca mostrar "concluído". Causa: o card
  "Aplicativo mobile (Android)" é um item de LazyColumn -- se ele saísse da
  janela composta da lista (rolar a tela) e voltasse, o Compose recriava a
  composição e o estado do download (downloading/downloadId), que só usava
  `remember` simples, era perdido -- a instância nova não sabia mais que um
  download estava rolando, então quando ele terminava de verdade, nada
  disparava. Trocado por `rememberSaveable`, que sobrevive a essa recriação
  (mesmo mecanismo usado pra rotação de tela).

## [1.2.9] -- 2026-08-22

- Removidas as últimas bordas remanescentes do app inteiro -- pedido do
  usuário ("faltou alguns módulos que não retiraram as bordas... tire
  todas as bordas de todo app"):
  - `AppCard.kt`: borda padrão de TODO Card do app zerada (era verde fina
    por padrão, sobrescrita manualmente só no Início/calculadoras). Como
    todo Card do app passa por aqui, isso já resolve de uma vez os blocos
    de ícone+rótulo (Dados/Operações/Arquivos, Filtros, Período, Gráficos,
    Imprimir, Nuvem, Copiar etc. em ModuleIconRow.kt) e a tela "Módulos"
    (ModulosScreen.kt), que ainda estavam com a borda antiga.
  - Pills dos seletores globais de fazenda/safra/cultura
    (FarmSelectorButton.kt/GlobalFieldSelectorButton.kt): borda trocada
    por preenchimento (mesmo tom dos Cards).
  - Campo calculado do formulário genérico (DomainFormScreen.kt): borda
    trocada por preenchimento, igual já feito no CalcResultField das
    calculadoras.
  - Badges de ícone de seção (Mural de Avisos, KPIs -- HomeScreen.kt):
    voltaram a ter fundo translúcido na cor do ícone em vez de só contorno.
  - Selo de tendência no círculo do Canvas (CanvasSection.kt): borda
    removida (já tinha fundo).
  - Mantidos de propósito: o contorno colorido dos círculos de fazenda no
    Canvas (indica status de custo -- dado real, mesmo critério do site) e
    o contorno do indicador "Conciliado" no Financeiro (espelha
    ConciliarDot do site, também dado real).

## [1.2.8] -- 2026-08-22

- Estendido o preenchimento sem borda (appFieldColors -- ver AppCard.kt) pra
  todos os campos de formulário do app que ainda estavam no padrão antigo
  (bordado, sem preenchimento): Configurações, Segurança, Romaneio Rápido,
  Login, Livro Caixa, FieldView, Drone, Base de Dados, DRE, Análises,
  Estoque (transferir/ajustar/devolver), Financeiro (filtro de datas),
  lista genérica de domínio (filtro de datas), calculadoras, importação
  bancária, integração de provedor, importação de NF-e e abastecimento
  rápido. Cobertura completa dos campos ativos do app (o único
  OutlinedTextField restante sem o tratamento é o de QuickCaptureBar.kt,
  arquivo órfão desde a remoção do bloco de captura por voz, task #268).
- Resultado de calculadora (CalcResultField) trocou borda por preenchimento
  pra continuar igual ao campo manual ao lado, agora que os campos não têm
  mais borda.

## [1.2.7] -- 2026-08-22

- Campos de formulário (Pedido/Cotação/Nota multi-item) sem borda,
  preenchidos com o mesmo tom dos Cards -- pedido do usuário ("preeencha
  os campos da mesma cor dos blocos e rretire as bordas odss campos").
- Listas suspensas da barra inferior (setor e "Módulos") agora mostram o
  mesmo verde forte do fundo/barra em vez do tom mais claro dos Cards.
- Tela "Módulos" (grade de domínios): fundo dos cards no mesmo verde forte
  do app -- pedido do usuário ("coloque nas listas suspensas e módulos o
  mesmo verde do app"). Escopo confirmado com o usuário: só esta tela, os
  blocos individuais dentro de cada módulo continuam neutros (decisão
  anterior preservada, ver ModuleIconRow.kt).

## [1.2.6] -- 2026-08-21

- Corrigido: os blocos (Cards) tinham ficado com a MESMA cor do fundo da
  tela (sem borda + sem contraste = bloco invisível). Cards agora usam um
  tom próprio, mais claro que o fundo verde (mais escuro no modo escuro),
  se destacando sem precisar trazer a borda de volta. A barra inferior
  continua no verde forte de antes (agora presa a "background", não mais a
  "surface", pra não herdar a nova cor mais clara dos Cards).

## [1.2.5] -- 2026-08-21

- Bloco de Estágio da safra e a sugestão adaptativa (dica de pragas etc.)
  viraram um card só, sem repetir o nome da fase -- antes eram dois blocos
  separados e a sugestão começava repetindo a mesma fase que já aparecia
  no rótulo de estágio.

## [1.2.4] -- 2026-08-21

- Fundo do app bem mais verde/perceptível (22% de mistura em vez de 8%) --
  a versão anterior ficou sutil demais pra notar no aparelho.
- Barra inferior agora usa exatamente o mesmo tom verde do fundo (sem
  elevação extra por cima), ficando visivelmente verde igual pedido.
- Removido o bloco de captura rápida por voz/texto (microfone) da Início.

## [1.2.3] -- 2026-08-21

- Fundo do app (claro e escuro) trocado pra mesma tonalidade da barra
  inferior (verde da marca bem diluído sobre o branco/grafite, mesma
  mistura que o Material3 já usa na própria barra e nos menus suspensos)
  -- antes o fundo geral era neutro, sem nenhum tom, enquanto a barra
  inferior aparecia com um véu esverdeado sutil.

## [1.2.2] -- 2026-08-21

- Fundo do app (claro e escuro) ajustado pra bater com a cor real de
  --background do site (quase branco no claro, grafite quente no escuro,
  sem nenhum terracota) -- antes era um bege/cinza neutro levemente
  diferente.

## [1.2.1] -- 2026-08-21

- Início: removida a borda de todos os blocos (Canvas, Estágio/Janela,
  Sugestão adaptativa, Mural, Alertas, Monitor, KPIs, Clima, Câmbio,
  Cotações, Destaques), igual ao ajuste equivalente feito no site --
  mesmas cores de tema, só sem o contorno verde.

## [1.2.0] -- 2026-08-19

- Pedidos e Cotações de Fornecedores ganharam o "novo modelo" de vários
  itens no mesmo lançamento (cabeçalho preenchido uma vez + itens
  repetíveis), igual ao que o site já tinha -- antes, criar um Pedido ou
  uma Cotação no app usava o formulário genérico de 1 item só. Chama
  direto as mesmas Server Actions do site (`createPedidoMultiItemAction`/
  `createCotacaoMultiItemAction`), nenhuma lógica de negócio duplicada em
  Kotlin.

## [1.1.0] -- 2026-08-12

- Preenchimento automático de despesas fixas mensais (Financeiro).
- Base de Dados: importar padrões respeita valores excluídos manualmente
  (não repõe mais o que o usuário apagou de propósito).
- Correção do erro 400 ao sincronizar lançamentos offline (mensagem de
  erro real chegava vazia pro app) + registro no Sentry.
- Selects com busca (digitar filtra as opções) em Novo Lançamento, em
  todos os módulos.
- Lançamento manual (sem KML/KMZ) em FieldView -- Talhões, Máquinas e
  Fazendas.
- Confirmação (Sim/Não) antes de importar os valores padrão em Base de
  Dados.
- Reversão do agrupamento visual de lançamentos de Safra por bloco --
  volta à listagem simples de sempre.
- Seletor global de fazenda no cabeçalho (Início e cada módulo
  farm-linked): ao escolher uma fazenda, os módulos Safra, Frota,
  Financeiro, Colheita, Planejamento de Safra, Pragas e Clima já abrem
  filtrados só pelas operações dela.
- Ícone do filtro de fazenda trocado de trator para pino de local (não
  conflita mais visualmente com o ícone de Frota).
- Logo BRAgro colada na borda esquerda de Início, abrindo espaço pra
  logo do cliente no lado direito do cabeçalho.
- Monitor em tempo real: cada evento agora é clicável e leva direto pro
  módulo correspondente (mesmo comportamento que Central de Alertas já
  tinha).
- Base de Dados: botão "Recusar" ao lado de "Importar padrões" -- marca
  os itens que faltam como decisão definitiva de não importar, sem
  precisar ver o aviso de novo (reversível cadastrando na mão depois).

## [1.0.0] -- Fases 1, 2 e 3 (ainda não publicado na Play Store)

Primeira versão completa do app nativo -- ainda não publicada, esta é a
base a partir da qual os próximos releases vão evoluir.

### Fase 1 -- núcleo offline
- Login direto no Supabase Auth.
- Download e cache local (Room) da configuração dos 16 módulos, listas
  suspensas, fazendas e dados da organização.
- Motor genérico de lista/formulário (mesmas duas telas servem todos os
  módulos, guiadas pela configuração de cada domínio).
- Offline de verdade: lançamentos gravam local na hora e entram numa fila
  de sincronização (WorkManager), com nova tentativa automática quando a
  conexão volta.

### Fase 2 -- paridade de recursos com o site
- Dashboard, DRE (com árvore de custos e composição por categoria),
  Clima/Câmbio/Cotações, Análises cruzadas.
- Renovação de sessão em segundo plano (token deixou de expirar
  silenciosamente nas telas de leitura).
- Ícone adaptativo e splash screen personalizados.
- Importação de XML de NF-e (com correção de um bug real de duplicidade
  que também afetava o site).
- Impressão/exportação em PDF de qualquer lista via `PrintManager`.
- Romaneio rápido com leitura automática do ticket por foto (OCR 100% no
  aparelho via ML Kit -- recurso que nem o site tem hoje).

### Fase 3 -- prontidão pra lançamento
- CI (GitHub Actions): compila, testa e faz lint a cada push/PR.
- `signingConfig` de release parametrizado por `keystore.properties`
  (nunca commitado).
- Dependência do Firebase Crashlytics (opcional, inerte até existir
  `google-services.json`).
- Regras de backup excluindo os tokens de sessão do backup automático.
- Robustez de UX: atualização manual + aviso de offline na lista genérica,
  aviso quando a foto do ticket é cancelada.
- Primeiro teste automatizado do projeto (`RomaneioOcrParserTest`).
- Rascunho da ficha da Play Store (`docs/play-store-listing.md`).
