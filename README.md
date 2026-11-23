# Diagnóstico do Líder

Diagnóstico de carreira para líderes: o líder abre um link, responde um questionário curto
(pensado para o celular) e recebe o resultado na tela e em PDF por e-mail. O resultado
posiciona o líder na **curva da carreira** (subida, platô, queda).

## Stack

| Camada    | Tecnologia                                                                        |
|-----------|-----------------------------------------------------------------------------------|
| Backend   | Java 27 · Spring Boot 4.2 (Web MVC, Data JPA, Validation, Thymeleaf, Actuator)    |
| Banco     | PostgreSQL 18 · Flyway                                                            |
| Frontend  | Angular 22 (zoneless, signals, Signal Forms, `@Service`) · Tailwind CSS 4.3       |
| PDF       | HTML (Thymeleaf) → Chromium: Gotenberg 9 em produção, Chrome local em dev         |
| E-mail    | Resend (API HTTP) em produção, log em dev                                         |
| Arquivos  | Railway Bucket (S3, AWS SDK v2) para as logomarcas; disco local em dev            |
| Deploy    | Railway, via `Dockerfile` (uma imagem com o Angular embutido no Spring Boot)      |

## Estrutura

```
backend/   Spring Boot (pacote por funcionalidade)
  campanha/      links de diagnóstico (geral ou vinculado a uma empresa)
  questionario/  questionário versionado: dimensões, perguntas, opções e faixas de resultado
  diagnostico/   respostas, cálculo do índice (0–100) e API pública
  relatorio/     PDF (template Thymeleaf + Chromium) e a geometria da curva
  notificacao/   envio do PDF por e-mail após a conclusão (assíncrono)
  empresa/       empresa, paleta de cores, logomarca e cargos
  arquivos/      onde ficam os arquivos: bucket S3 ou disco local
  db/migration/  Flyway (V2 traz o questionário de exemplo)
frontend/  Angular
  diagnostico/   questionário: uma etapa por tela, progresso salvo no aparelho
  resultado/     resultado com a curva, dimensões, próximos passos e download do PDF
Dockerfile Build único para o Railway
```

### Decisões de modelagem

- **Questionário orientado a dados e versionado.** Mudar perguntas é criar uma nova versão.
  Cada diagnóstico aponta para a versão respondida, então resultados antigos continuam corretos.
- **Campanha = link.** Sem empresa é o link geral (`/`); com empresa (`/c/{codigo}`), os
  diagnósticos entram no relatório consolidado daquela empresa (`diagnostico.empresa_id`).
  O líder nunca digita a empresa: no link de empresa ela vem do próprio link; no geral, não há.
- **Empresa nasce com o link.** Cadastrar uma empresa no painel cria a campanha `/c/{codigo}`
  (o código sai do nome, sem acento; a regra está em `CodigoCampanha.java` e `codigo-campanha.ts`).
- **Resultado gravado na conclusão** (`pontuacao`, `faixa_id`, `diagnostico_dimensao`):
  o consolidado por empresa é um `AVG ... GROUP BY`.
- **Sem login.** O id do diagnóstico é um UUID aleatório e funciona como chave de acesso ao resultado.

## Rodando localmente

Pré-requisitos: JDK 27, Node 26, PostgreSQL com o banco `diagnostico_lider`
(padrão `localhost:5432`, usuário `postgres`, senha `root`, ajustável pelas variáveis `PG*`).

```powershell
cd backend; ./mvnw spring-boot:run
```

```powershell
cd frontend; npm install; npm start
```

Abra http://localhost:4200. O Angular faz proxy de `/api` para `localhost:8080`.
O PDF local usa o Chrome/Edge instalado (`CHROME_PATH` para outro caminho).
O e-mail em dev não sai: o log mostra o que seria enviado e o caminho do HTML gerado
(`%TEMP%\diagnostico-lider\ultimo-email.html`), para abrir no navegador.

As logomarcas vão para o disco (`backend/.arquivos/desenvolvimento`, fora do git) até você
colocar as credenciais do bucket em `backend/.env` (o arquivo já é lido na subida):

```properties
S3_ENDPOINT=https://...
S3_BUCKET=...
S3_ACCESS_KEY_ID=...
S3_SECRET_ACCESS_KEY=...
S3_REGION=auto
ARMAZENAMENTO_PREFIXO=desenvolvimento
```

Na subida, o log diz onde os arquivos estão ("Arquivos (logomarcas) em: ...").

Links de exemplo: `/` (link geral) e `/c/demo` (vinculado à "Empresa Demonstração").

## API pública

| Método | Caminho                                              | O que faz                                   |
|--------|------------------------------------------------------|---------------------------------------------|
| GET    | `/api/public/campanhas/{codigo}`                     | Campanha + questionário (sem pesos)         |
| POST   | `/api/public/campanhas/{codigo}/diagnosticos`        | Inicia com nome, e-mail e WhatsApp (e cargo e tempo no cargo, se o link pedir) |
| GET    | `/api/public/diagnosticos/{id}`                      | Estado para retomar                         |
| PUT    | `/api/public/diagnosticos/{id}/respondente`          | Corrige os dados do líder                   |
| PUT    | `/api/public/diagnosticos/{id}/respostas/{pergunta}` | Grava a resposta                            |
| POST   | `/api/public/diagnosticos/{id}/finalizar`            | Calcula o resultado (idempotente)           |
| GET    | `/api/public/diagnosticos/{id}/resultado`            | Resultado                                   |
| GET    | `/api/public/diagnosticos/{id}/relatorio.pdf`        | PDF (`?visualizar=true` abre no navegador)  |
| GET    | `/api/public/campanhas/{codigo}/previa-{versao}.jpg` | Imagem da prévia do link (1200×630)         |

Erros seguem `application/problem+json` (RFC 9457); validação traz o mapa `campos`.

## Painel administrativo

Tela em `/administrador`: indicadores, a curva com todos os líderes, distribuição por faixa,
busca (atalho `/`), filtros por link, situação e faixa (todos na URL) e o detalhe de cada
diagnóstico com respostas, pontos, linha do tempo e download do PDF.

Em `/administrador/empresas`: cadastro de empresas (cada uma recebe o seu link), mensagem de
convite pronta e o QR Code do link, em dois formatos: só o código (PNG ou SVG para gráfica) e
um cartão de divulgação 1080 × 1350. O QR Code é gerado no navegador (`lean-qr`, correção H,
com o símbolo da marca no centro): copiar, baixar ou compartilhar a imagem não passa pelo servidor.

### Login e usuários do painel

`/administrador` e `/api/admin/**` exigem login (Spring Security). Sem perfis por enquanto:
todo usuário acessa tudo, inclusive a gestão de usuários em `/administrador/usuarios`.

- **Sessão** em cookie `SESSAO_PAINEL` HttpOnly, SameSite=Lax (Secure em HTTPS), 12 h sem uso.
  As chamadas que alteram dados levam o token CSRF (cookie `XSRF-TOKEN` → cabeçalho
  `X-XSRF-TOKEN`, enviado sozinho pelo Angular). O questionário público continua sem login.
- **Senhas** com BCrypt (`{bcrypt}` do `DelegatingPasswordEncoder`), mínimo de 8 caracteres.
- **Limite de tentativas:** 5 erros no mesmo e-mail (ou 20 no mesmo IP) em 15 min bloqueiam o
  login até a janela passar (HTTP 429).
- **Desativar ou trocar a senha** de alguém derruba as sessões abertas dessa pessoa na hora
  (`usuario.credenciais_em`, conferido a cada chamada). Ninguém desativa o próprio acesso.
- **Primeiro acesso:** criado na subida a partir de `ADMIN_EMAIL` e `ADMIN_SENHA` (e `ADMIN_NOME`),
  só se ainda não houver nenhum usuário. Localmente essas variáveis ficam em `backend/.env`
  (fora do Git). Não existe tela pública de primeiro cadastro.

| Método | Caminho                                   | O que faz                                              |
|--------|-------------------------------------------|--------------------------------------------------------|
| GET    | `/api/auth/sessao`                        | Quem está logado (`{ usuario }`) e entrega o cookie CSRF |
| POST   | `/api/auth/entrar`                        | Login (`{ email, senha }`)                              |
| POST   | `/api/auth/sair`                          | Encerra a sessão                                        |
| PUT    | `/api/auth/senha`                         | Troca a própria senha (`{ senhaAtual, novaSenha }`)     |
| GET    | `/api/admin/usuarios`                     | Usuários do painel                                      |
| POST   | `/api/admin/usuarios`                     | Cria (`{ nome, email, senha }`)                         |
| PUT    | `/api/admin/usuarios/{id}`                | Edita nome e e-mail                                     |
| PUT    | `/api/admin/usuarios/{id}/senha`          | Define nova senha (derruba as sessões da pessoa)        |
| PUT    | `/api/admin/usuarios/{id}/situacao`       | Desativa ou reativa (`{ ativo }`)                       |

### API do painel

| Método | Caminho                                         | O que faz                                                   |
|--------|-------------------------------------------------|-------------------------------------------------------------|
| GET    | `/api/admin/painel?campanha=&cargo=`            | Indicadores, pontos da curva, totais por link e os cargos do link |
| GET    | `/api/admin/diagnosticos?busca=&status=&faixa=&campanha=&cargo=&pagina=&tamanho=` | Lista paginada (mais recentes primeiro) |
| GET    | `/api/admin/diagnosticos/{id}`                  | Detalhe completo: líder, origem, resultado, todas as respostas |
| GET    | `/api/admin/diagnosticos/{id}/relatorio.pdf`    | PDF do diagnóstico                                          |
| GET    | `/api/admin/empresas`                           | Empresas com o link e quantos diagnósticos cada uma recebeu |
| POST   | `/api/admin/empresas`                           | Cadastra a empresa e cria o link (`{ nome, codigo?, cores, cargos }`) |
| GET    | `/api/admin/empresas/{id}`                      | Empresa, link e indicadores                                 |
| GET    | `/api/admin/empresas/disponibilidade?codigo=`   | Confere se o endereço do link está livre (e sugere outro)   |
| GET    | `/api/admin/empresas/{id}/relatorio.pdf`        | Relatório consolidado sem nomes (`?visualizar=true` abre no navegador) |
| GET    | `/api/admin/empresas/{id}/relatorio.html`       | O mesmo relatório em HTML, para ajustar o layout            |

### Cores da marca por empresa

Cada empresa pode ter **cor principal** (botões, monograma, destaques) e **cor secundária**
opcional (a curva da carreira). Elas valem no questionário e no resultado do link da empresa, no
PDF individual, no e-mail do líder, no relatório consolidado e no QR Code/cartão. O painel e o
link geral continuam com a identidade do Diagnóstico.

- `PaletaMarca.java` gera a paleta em OKLCH a partir das duas cores e confere o contraste do WCAG
  (texto 4,5:1, curva e foco 3:1). Cor clara ganha texto escuro nos botões; cor que some no fundo
  é escurecida na curva; cor parecida com o vermelho, o âmbar ou o verde das faixas gera aviso.
  As cores das faixas nunca mudam.
- O front só aplica a paleta pronta como variáveis de CSS (`tema-marca.ts`); os componentes leem
  `--color-acao`, `--color-abeto`, `--color-brasa` etc.
- Sem logo, o **monograma** (iniciais da empresa) faz esse papel. Com ou sem logo, a marca do
  Diagnóstico aparece discreta ("por Diagnóstico do Líder").
- `GET /api/admin/empresas/tema?primaria=&secundaria=` devolve a prévia (paleta e avisos);
  `PUT /api/admin/empresas/{id}` edita nome e cores. O link (`/c/{codigo}`) não muda na edição.

### Logomarca da empresa

Enviada no cadastro ou na edição da empresa. Aparece no questionário e no resultado do link da
empresa, no PDF individual, no e-mail do líder, no relatório consolidado e no QR Code/cartão.

- **Preparada no navegador** (`admin/logo/preparar-logo.ts`): aceita PNG, JPG, WebP e SVG
  (até 8 MB); SVG vira PNG; o fundo branco liso é removido (opcional, ligado por padrão); as
  sobras de borda são cortadas; o lado maior fica com até 1000 px. O backend só recebe PNG
  (até 2,5 MB, de 8 a 2400 px) e confere a assinatura do arquivo.
- **Cores do logo:** o painel sugere a cor principal e a secundária a partir da logo
  ("Usar estas cores"); a paleta continua passando pelos ajustes de contraste.
- **Proporção decide o layout:** logo quase quadrada (só o símbolo) mostra o nome da empresa ao
  lado e entra no centro do QR Code; logo larga já traz o nome e fica fora do código.
- **Arquivos versionados:** `{prefixo}/empresas/{id}/logo-v{versao}.png`. Trocar a logo grava
  uma versão nova e apaga a anterior depois do commit; o endereço muda junto, então o cache pode
  ser de um ano (`immutable`).
- **A imagem não passa pelo Java:** o bucket do Railway é privado, então a API entrega nas telas
  um **endereço assinado** e o navegador baixa a logo direto do bucket. A assinatura vale 7 dias
  e o mesmo endereço é reaproveitado por 6, para o cache do navegador funcionar
  (`S3Armazenamento.urlDireta`). Na subida, o app garante no bucket uma regra de CORS só de
  leitura (GET/HEAD), para o painel desenhar a logo no QR Code.
- **Onde o backend ainda lê a logo:** os PDFs embutem a imagem (data URI, com cache em memória)
  e o e-mail usa o endereço permanente do backend, porque um endereço assinado expiraria na
  caixa de entrada do líder. Em dev sem bucket, o backend serve tudo.
- **Armazenamento** (`arquivos/`): `S3Armazenamento` quando há credenciais do bucket,
  `DiscoArmazenamento` em dev. O prefixo separa os ambientes (`desenvolvimento`, `producao`).

| Método | Rota                                              | O que faz                                   |
|--------|---------------------------------------------------|---------------------------------------------|
| PUT    | `/api/admin/empresas/{id}/logo`                   | Envia a logo (multipart, campo `arquivo`, PNG) |
| DELETE | `/api/admin/empresas/{id}/logo`                   | Remove a logo                               |
| GET    | `/api/public/empresas/{id}/logo-v{versao}.png`    | Endereço permanente da versão atual (e-mail e dev sem bucket; versão antiga: 404) |

### Cargos dos líderes

Cada empresa pode ter uma lista de cargos de liderança, **em ordem hierárquica** (do primeiro
nível ao mais alto). No link da empresa, o líder escolhe o cargo e há quanto tempo está nele; o
relatório consolidado mostra em que nível a carreira mais trava.

- **No cadastro**, a lista já vem com Supervisor, Coordenador, Gerente e Diretor para editar.
  Até 12 cargos (`CargoService.MAXIMO`); setas reordenam, Enter cria a próxima linha. O painel
  mostra a lista como uma escada (nível mais alto em cima); nos dados e na API a ordem vai do
  primeiro nível (ordem 0) ao mais alto.
- **Para o líder:** duas telas depois do WhatsApp, "Qual é o seu cargo hoje?" (com "Meu cargo não
  está na lista" e o nome opcional) e "Há quanto tempo você está nesse cargo?" (menos de 1 ano,
  1 a 2, 3 a 5, mais de 5). Obrigatórias quando a empresa tem cargos; sem cargos, não aparecem.
  O diagnóstico é criado na última delas.
- **Remover um cargo** que já tem diagnósticos só o arquiva (sai da lista do líder, continua nos
  relatórios e no painel); sem diagnósticos, ele é apagado. Cadastrar de novo um nome arquivado
  traz o cargo de volta, com o histórico.
- **Anonimato no relatório:** um cargo só aparece sozinho com pelo menos 3 líderes. Os menores
  entram em "Outros cargos", junto com quem escolheu "Outro cargo"; se nem assim chegar a 3, fica
  de fora (com aviso). Faixa de tempo no cargo com menos de 3 líderes aparece sem números.
  A regra está em `AnaliseCargos.java` (com testes).
- **No relatório:** duas páginas depois do resumo executivo: a "escada" (cada cargo com a
  distribuição nas faixas e o índice médio, o mais travado em destaque) e o mapa cargo × dimensão
  com o tempo no cargo. O cargo mais travado também entra no resumo executivo.
- **No painel:** o cargo aparece na lista e no detalhe do diagnóstico, e dá para filtrar o painel
  por cargo quando um link de empresa está selecionado (`cargo=` com o id ou `outro`).

### Relatório consolidado da empresa

PDF para enviar à empresa, só com números agregados: o grupo na curva da carreira (cada líder é
um ponto, sem identificação), distribuição por faixa, índice médio, mediana e extremos, comparação
com a média geral, participação, cada dimensão (média, amplitude, % em alerta e % forte), cada
pergunta (quantos escolheram cada alternativa) e o que a empresa pode fazer por faixa.

- **Anonimato:** só é gerado com pelo menos 3 diagnósticos concluídos
  (`RelatorioEmpresaService.MINIMO_LIDERES`). Abaixo disso, o painel mostra quantos faltam.
- **Textos para a empresa** ficam no banco, junto da versão do questionário (V4): `leitura_empresa`
  e `faixa_acao_empresa` por faixa, `acao_empresa` por dimensão. Os textos atuais são de exemplo.
- Template: `templates/relatorio/empresa.html` (páginas A4 de tamanho fixo; perguntas e dimensões
  são paginadas no `RelatorioEmpresaService`).

### Prévia do link no WhatsApp

Quando o RH manda o link (`/c/{codigo}` ou `/`), o WhatsApp, o LinkedIn, o Telegram e o iMessage
mostram uma prévia com imagem: a curva da carreira com "Você está aqui?", a pergunta do
diagnóstico e, no link de uma empresa, a logo (ou o monograma) e as cores dela.

- **Por que o backend serve o `index.html` desses links:** quem monta a prévia não roda
  JavaScript, só lê as tags Open Graph do HTML. O `PreviaLinkController` devolve o mesmo
  `index.html` do Angular, trocando as tags `og:*`, `twitter:*` e `description` pelas do link
  (convite com o nome da empresa e o endereço absoluto da imagem). As outras rotas do front seguem
  pelo `SpaWebConfig`.
- **A imagem** sai do template `templates/previa/link.html` pelo mesmo motor do PDF (Chrome local
  ou rota de screenshot do Gotenberg) e é convertida em JPEG (~80 KB; o WhatsApp não mostra
  imagens grandes). Fica em memória por versão: o endereço muda quando o nome, as cores ou a logo
  da empresa mudam (ou `PreviaLinkService.DESENHO`, quando o desenho muda), e o app busca a nova.
- **Testar localmente:** o `ng serve` (porta 4200) não passa por aqui. Gere o front
  (`npm --prefix frontend run build`), copie `frontend/dist/frontend/browser/*` para
  `backend/target/classes/static/` e abra `http://localhost:8080/c/demo`.
- **O WhatsApp guarda a prévia** de cada endereço por um tempo. Para ver uma mudança no celular,
  mande o link com um parâmetro novo (ex.: `/c/demo?v=2`).

## Deploy no Railway

Quatro serviços no mesmo projeto:

1. **Postgres**: banco gerenciado do Railway.
2. **gotenberg**: imagem `gotenberg/gotenberg:9.9.3-chromium`, só na rede privada (porta 3000).
3. **logomarcas**: Railway Bucket (S3), região US West, a mesma do app.
4. **app**: este repositório (o Railway usa o `Dockerfile` da raiz). Health check: `/actuator/health`.

Variáveis do serviço **app**:

| Variável           | Valor                                         |
|--------------------|-----------------------------------------------|
| `PGHOST` … `PGPASSWORD`, `PGPORT`, `PGDATABASE`, `PGUSER` | referências ao serviço Postgres (`${{Postgres.PGHOST}}` etc.) |
| `PORT`             | `8080`                                        |
| `GOTENBERG_URL`    | `http://gotenberg.railway.internal:3000`      |
| `APP_URL_PUBLICA`  | domínio público do app (links do e-mail)      |
| `EMAIL_PROVEDOR`   | `log` (padrão) ou `resend`                    |
| `RESEND_API_KEY`   | chave da Resend, quando `EMAIL_PROVEDOR=resend` |
| `EMAIL_REMETENTE`  | ex.: `Diagnóstico do Líder <diagnostico@seudominio.com.br>` |
| `ADMIN_EMAIL`      | e-mail do primeiro acesso ao painel (só vale enquanto não houver usuários) |
| `ADMIN_SENHA`      | senha desse primeiro acesso (8+ caracteres); depois, troque em "Trocar minha senha" |
| `ADMIN_NOME`       | nome exibido (opcional, padrão "Administrador") |
| `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, `S3_REGION` | referências ao bucket (`${{logomarcas.ENDPOINT}}`, `${{logomarcas.BUCKET}}`, `${{logomarcas.ACCESS_KEY_ID}}`, `${{logomarcas.SECRET_ACCESS_KEY}}`, `${{logomarcas.REGION}}`) |
| `ARMAZENAMENTO_PREFIXO` | `producao` (pasta do ambiente dentro do bucket) |

O Railway só libera SMTP no plano Pro, por isso o e-mail usa a API HTTP da Resend.
