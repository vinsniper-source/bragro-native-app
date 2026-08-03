# Ficha da Play Store -- rascunho (Fase 3)

Rascunho pra você revisar/editar antes de publicar. Nada aqui é definitivo
-- ajuste tom, nome comercial, capturas de tela etc. como preferir. Os
limites de caracteres abaixo são os do Google Play Console.

## Nome do app (máx. 30 caracteres)

```
BRAgro
```

## Descrição curta (máx. 80 caracteres)

```
Gestão agrícola completa: financeiro, estoque, safra, RH e mais -- offline.
```
(79 caracteres)

## Descrição completa (máx. 4000 caracteres)

```
BRAgro é o aplicativo de gestão para produtores rurais que reúne, num só
lugar, tudo que hoje fica espalhado em planilhas e papel: financeiro,
estoque, safra e colheita, frota, recursos humanos, notas fiscais e muito
mais.

PRINCIPAIS RECURSOS

• Funciona offline de verdade -- lance dados no talhão, na balança ou onde
  não tiver internet. Tudo é salvo no aparelho na hora e sincroniza
  sozinho assim que a conexão voltar.
• Painel com os principais indicadores da fazenda: financeiro em aberto,
  estoque, safras em andamento, colaboradores ativos e mais.
• DRE (Demonstrativo de Resultado) por fazenda, com custo por hectare e
  por saca, e detalhamento de custos por categoria.
• Clima, cotação do dólar/euro e preços de commodities (soja, milho,
  sorgo) direto na tela inicial.
• Análises cruzadas entre módulos: planejado x realizado, custo x
  produtividade, clima x safra e outras.
• Importação de nota fiscal eletrônica (XML de NF-e) com lançamento
  automático em Estoque e Financeiro.
• Romaneio rápido de balança com leitura automática do ticket por foto
  (OCR no próprio aparelho, sem precisar de internet).
• Impressão/exportação em PDF de qualquer lista direto do celular.
• 16 módulos completos: Financeiro, Estoque, Safra, Colheita, Frota, RH,
  NF-e, Romaneios, Cobranças, Pedidos e mais.

Feito para quem trabalha no campo: interface simples, sem depender de
sinal de internet o tempo todo, e com os cálculos automáticos (rateio de
custos, vencimento de parcelas, numeração de documentos) sempre corretos
porque vêm do mesmo sistema usado no escritório.
```

## Categoria sugerida

Negócios (Business) -- categoria secundária possível: Produtividade.

## Público-alvo / classificação de conteúdo

Sem conteúdo sensível (sem violência, sem conteúdo adulto). Classificação
indicativa esperada: Livre. Preencha o questionário de classificação de
conteúdo do Play Console normalmente -- nenhuma resposta deveria gerar
restrição de idade.

## Política de privacidade -- rascunho

O Play Console exige uma URL pública com a política de privacidade
(pode ser uma página simples hospedada em qualquer lugar -- inclusive
GitHub Pages a partir deste mesmo repositório, se preferir). Rascunho:

```
Política de Privacidade -- BRAgro

Última atualização: [preencher data]

O aplicativo BRAgro ("nós", "aplicativo") é operado por [preencher razão
social/CNPJ]. Esta política descreve quais dados o aplicativo coleta e
como são usados.

1. DADOS DE CONTA
Para usar o aplicativo, você cria/usa uma conta com e-mail e senha. Esses
dados são armazenados de forma segura pelo nosso provedor de
autenticação (Supabase) e usados exclusivamente para identificar sua
organização e controlar o acesso aos seus próprios dados.

2. DADOS OPERACIONAIS DA FAZENDA
Os lançamentos que você faz no aplicativo (financeiro, estoque, safra,
recursos humanos, notas fiscais, romaneios etc.) ficam armazenados nos
nossos servidores (Supabase, hospedado em [preencher região/provedor]) e
também localmente no seu aparelho, para uso offline. Esses dados
pertencem à sua organização e não são compartilhados com terceiros nem
usados para publicidade.

3. FOTOS
O aplicativo permite fotografar tickets de balança (romaneios). Essas
fotos são enviadas para armazenamento seguro (Supabase Storage),
vinculadas à sua organização, e usadas apenas para conferência do
lançamento. A leitura automática de texto da foto (OCR) acontece
inteiramente no seu aparelho -- nenhuma imagem é enviada a serviços de
terceiros para esse fim.

4. RELATÓRIO DE ERROS
Se você habilitar o recurso de relato de erros (Firebase Crashlytics),
informações técnicas sobre travamentos (modelo do aparelho, versão do
Android, pilha de execução do erro) podem ser enviadas ao Google Firebase
para nos ajudar a corrigir problemas. Esses dados não incluem o conteúdo
dos seus lançamentos.

5. LOCALIZAÇÃO
O aplicativo não coleta nem armazena sua localização geográfica.

6. COMPARTILHAMENTO COM TERCEIROS
Não vendemos nem compartilhamos seus dados com terceiros para fins de
publicidade. Os dados são processados pelos seguintes prestadores de
serviço, na função de operadores: Supabase (banco de dados, autenticação
e armazenamento de arquivos) e, opcionalmente, Google Firebase (relato de
erros).

7. EXCLUSÃO DE DADOS
Para solicitar a exclusão da sua conta e dos dados associados, entre em
contato pelo e-mail [preencher e-mail de contato].

8. CONTATO
Dúvidas sobre esta política: [preencher e-mail de contato].
```

**Antes de publicar**: preencha os campos entre colchetes (razão
social/CNPJ, região do servidor, e-mail de contato, data) e hospede este
texto numa URL pública -- é ela que vai no campo "Política de privacidade"
do Play Console.

## Formulário de Segurança de Dados (Data Safety) do Play Console

Guia rápido de como responder o formulário, com base no que o app
realmente coleta hoje:

- **Coleta dados pessoais?** Sim -- e-mail (conta) e dados operacionais
  da organização (financeiro, RH etc., que podem incluir nomes de
  colaboradores).
- **Localização**: Não coleta.
- **Fotos/vídeos**: Sim (fotos de ticket de romaneio), enviadas para
  armazenamento próprio (Supabase), não compartilhadas com terceiros.
- **Dados financeiros**: Sim (lançamentos financeiros da organização),
  não compartilhados com terceiros, usados só para o funcionamento do
  app.
- **Identificadores de dispositivo/relatório de falhas**: Sim, se o
  Crashlytics estiver habilitado -- finalidade "Analytics"/"Diagnóstico".
- **Os dados são criptografados em trânsito?** Sim (HTTPS/TLS em todas as
  chamadas).
- **Você permite que o usuário peça exclusão dos dados?** Sim (ver seção
  7 da política acima -- defina o processo real antes de marcar "sim").

## Capturas de tela

Ainda não geradas. Sugestão de 4-6 telas pra tirar num emulador ou
aparelho real antes de publicar: Início/Dashboard, DRE, uma lista de
módulo (ex.: Financeiro), formulário de lançamento, Romaneio rápido (com
a pré-visualização do OCR) e Análises cruzadas.
