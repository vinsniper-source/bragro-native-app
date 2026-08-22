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
