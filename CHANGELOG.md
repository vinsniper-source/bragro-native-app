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
