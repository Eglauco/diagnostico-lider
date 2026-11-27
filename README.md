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
  acompanhamento/ relatórios automáticos ao gestor e lembretes aos líderes (agendador de 5 em 5 min)
                 e o resultado da empresa em "fotos" anônimas (ResultadoDaEmpresa, RegraDaFoto)
  rh/            página do RH: entrada por código no e-mail, sem senha, e a API de /acompanhar
  empresa/       empresa, paleta de cores, logomarca e cargos
  arquivos/      onde ficam os arquivos: bucket S3 ou disco local
  db/migration/  Flyway (V2 traz o questionário de exemplo)
frontend/  Angular
  diagnostico/   questionário: uma etapa por tela, progresso salvo no aparelho
  resultado/     resultado com a curva, dimensões, próximos passos e download do PDF
  acompanhar/    página do RH: participação ao vivo e resultado do grupo (marca da empresa)
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
O e-mail em dev sai de verdade pelo Resend, como em produção, mas desviado: com estas linhas no
`backend/.env`, todo e-mail (convite, lembrete, resultado, relatório do gestor) vai para um endereço
só, com o destinatário original no assunto ("[Teste → ana@empresa.com.br] ..."). Assim os testes
não escrevem para os endereços de teste do banco, que voltariam e pesariam na reputação do domínio.
A linha do tempo do painel continua mostrando o destinatário original.

```properties
EMAIL_PROVEDOR=resend
EMAIL_DESVIAR_PARA=voce@gmail.com
```

Sem `EMAIL_PROVEDOR`, o e-mail não sai: o log mostra o que seria enviado e o caminho do HTML gerado
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
| GET    | `/api/public/convites/{token}`                       | Link pessoal: dados para confirmar, link da empresa e diagnóstico |
| POST   | `/api/public/convites/{token}/acesso`                | A página abriu no aparelho do líder (auditoria) |
| POST   | `/api/public/convites/{token}/diagnosticos`          | Confirma (ou corrige) os dados e começa/atualiza o diagnóstico |

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
| GET    | `/api/admin/diagnosticos?busca=&status=&faixa=&campanha=&cargo=&inativos=&pagina=&tamanho=` | Lista paginada (mais recentes primeiro); `inativos=true` mostra só os inativados |
| GET    | `/api/admin/diagnosticos/exportacao.xlsx?busca=&status=&faixa=&campanha=&cargo=&inativos=` | A lista inteira com os filtros da tela, em Excel (resumo com fórmulas, uma coluna por dimensão) |
| GET    | `/api/admin/diagnosticos/exportacao.pdf?…`      | A mesma lista em PDF (indicadores, curva e uma linha por líder; até 1.000) |
| GET    | `/api/admin/diagnosticos/exportacao.html?…`     | O PDF em HTML, para ajustar o layout                        |
| GET    | `/api/admin/diagnosticos/{id}`                  | Detalhe completo: líder, origem, resultado, todas as respostas |
| GET    | `/api/admin/diagnosticos/{id}/relatorio.pdf`    | PDF do diagnóstico                                          |
| GET    | `/api/admin/empresas`                           | Empresas com o link e quantos diagnósticos cada uma recebeu |
| POST   | `/api/admin/empresas`                           | Cadastra a empresa e cria o link (`{ nome, codigo?, cores, cargos }`) |
| GET    | `/api/admin/empresas/{id}`                      | Empresa, link e indicadores                                 |
| GET    | `/api/admin/empresas/disponibilidade?codigo=`   | Confere se o endereço do link está livre (e sugere outro)   |
| GET    | `/api/admin/empresas/{id}/relatorio.pdf`        | Relatório consolidado sem nomes (`?visualizar=true` abre no navegador) |
| GET    | `/api/admin/empresas/{id}/relatorio.html`       | O mesmo relatório em HTML, para ajustar o layout            |
| GET    | `/api/admin/empresas/{id}/convites`             | Convites da empresa, o funil e se o e-mail está ligado      |
| GET    | `/api/admin/empresas/{id}/convites/modelo.xlsx` | Planilha modelo com os cargos da empresa                    |
| POST   | `/api/admin/empresas/{id}/convites/importar`    | Lê a planilha (`arquivo`, .xlsx ou .csv) para revisão; não grava |
| POST   | `/api/admin/empresas/{id}/convites`             | Cria os convites revisados (`{ convites: [...] }`; erros por linha) |
| POST   | `/api/admin/empresas/{id}/convites/enviar`      | Dispara os e-mails (`{ ids }`; vazio: os não enviados). 409 sem e-mail ou encerrada |
| GET    | `/api/admin/empresas/{id}/convites/email-previa`| O e-mail de convite em HTML                                 |
| PUT    | `/api/admin/convites/{id}`                      | Corrige os dados de um convite (vai para a auditoria)       |
| POST   | `/api/admin/convites/{id}/enviar`               | Envia o convite de um líder na hora (`{ para }`); 409 com o motivo se não sair |
| POST   | `/api/admin/convites/{id}/inativar`             | Inativa o líder: o link fecha e ele sai dos cálculos        |
| POST   | `/api/admin/convites/{id}/reativar`             | Reativa o líder (link e cálculos de volta)                  |
| POST   | `/api/admin/diagnosticos/{id}/inativar`         | Tira o diagnóstico dos cálculos (e inativa o convite dele)  |
| POST   | `/api/admin/diagnosticos/{id}/reativar`         | Devolve o diagnóstico aos cálculos                          |
| GET    | `/api/admin/convites/{id}/eventos`              | Linha do tempo (auditoria) do convite                       |
| GET    | `/api/admin/empresas/{id}/convites/participacao.pdf`  | Relatório de participação (quem falta, com o link pessoal) |
| GET    | `/api/admin/empresas/{id}/convites/participacao.xlsx` | A participação em Excel, com filtro e resumo             |
| GET    | `/api/admin/empresas/{id}/convites/participacao.html` | O PDF em HTML, para ajustar o layout                     |
| GET    | `/api/admin/empresas/{id}/convites/lembrete-previa`   | O lembrete aos líderes em HTML                           |
| GET    | `/api/admin/empresas/{id}/acompanhamento`             | Relatórios ao gestor: situação, lembretes e histórico    |
| POST   | `/api/admin/empresas/{id}/acompanhamento/enviar`      | "Enviar agora" (o do dia, ou o final). 409 sem gestor/e-mail |
| PUT    | `/api/admin/empresas/{id}/acompanhamento/pausa`       | Pausa ou retoma o diário (`{ pausado }`)                 |
| GET    | `/api/admin/empresas/{id}/acompanhamento/email-previa`| O e-mail que sairia agora, em HTML                       |
| GET    | `/api/admin/empresas/{id}/acesso-rh`                  | Cada gestor: último acesso à página do RH e acesso mandado |
| POST   | `/api/admin/empresas/{id}/acesso-rh/convite`          | "Mandar acesso" (`{ email }`): botão que entra direto, 7 dias, uma vez |
| GET    | `/api/admin/empresas/{id}/acesso-rh/convite-previa`   | Esse e-mail em HTML                                      |

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
- **Onde o backend ainda lê a logo:** os PDFs embutem a imagem (data URI, com cache em memória).
  O e-mail também leva a logo dentro dele (imagem embutida, `cid:`, em `LogoEmbutida`), na hora
  de enviar: o Outlook corporativo bloqueia imagem de fora até o leitor clicar em "Baixar
  imagens", e o Gmail não alcança o localhost do dev. Os templates e as prévias do painel usam o
  endereço permanente do backend, que também fica de reserva se a logo não puder ser lida no
  envio. Em dev sem bucket, o backend serve tudo.
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

- **Resultado em fotos:** o que a empresa recebe (este PDF, o e-mail final e a página do RH) sai da última
  foto do resultado, que só muda com 3 respostas novas (ver "Acesso do RH"). Enquanto a avaliação está
  aberta, o relatório sai sem a análise por cargo e por pergunta.
- **Anonimato:** só é gerado com pelo menos 3 diagnósticos concluídos
  (`RelatorioEmpresaService.MINIMO_LIDERES`). Abaixo disso, o painel mostra quantos faltam.
- **Textos para a empresa** ficam no banco, junto da versão do questionário (V4): `leitura_empresa`
  e `faixa_acao_empresa` por faixa, `acao_empresa` por dimensão. Os textos atuais são de exemplo.
- Template: `templates/relatorio/empresa.html` (páginas A4 de tamanho fixo; perguntas e dimensões
  são paginadas no `RelatorioEmpresaService`).

### Encerramento da avaliação

No cadastro da empresa, "Encerramento da avaliação" (data e hora, com atalhos de 7, 15 e 30 dias)
fecha tudo de uma vez quando chega: os links da empresa e os links pessoais deixam de aceitar
respostas (`CampanhaService.exigirAberta`, erro 409 com `encerrada: true`), os convites e lembretes
param de sair e o relatório consolidado passa a dizer "resultado final" (antes: "resultado parcial").
Quem já concluiu continua vendo o próprio resultado. Sem data, a avaliação fica aberta.

### Convites nominais e o cadastro de líderes

O RH manda a lista de líderes numa planilha e cada um recebe por e-mail um link pessoal
(`/i/{token}`, 10 caracteres aleatórios), já com os dados preenchidos.

**Todo líder da avaliação é um cadastro** (tabela `convite`, tela "Líderes da avaliação"), com a
origem (`OrigemLider`): Planilha, Cadastro manual ou Link da empresa. Assim o painel, a página do RH
e o relatório de participação contam as mesmas pessoas.

- **Link aberto da empresa:** quem começa por `/c/{codigo}` entra no cadastro na hora (evento
  `DiagnosticoIniciadoPeloLink`), com um link pessoal para continuar de onde parou. Se o e-mail já é de
  um líder da empresa (o convidado que respondeu pelo link aberto), a resposta entra no cadastro dele,
  em vez de ele seguir como "não abriu". Os diagnósticos de antes desta regra entram na subida do app
  (`LideresDoLink`). O link da empresa avisa o líder: "a empresa vê que você participou, nunca o seu
  resultado nem as suas respostas".
- **Respondeu de novo:** vale uma resposta por líder. A concluída vence a que ficou pela metade, e
  entre duas concluídas vale a mais recente; a outra sai dos cálculos (`diagnostico.substituido_por`,
  com aviso no detalhe do diagnóstico). Quem já tinha concluído e começa de novo só troca de resposta
  ao terminar. Regra em `ConviteService.entrarNoCadastro` (com testes).
- **No painel:** o funil (enviados, abriram, começaram, concluíram) é dos convidados; quem veio pelo
  link aparece à parte, com filtro por origem e a etiqueta "pelo link". A página da empresa mostra
  líderes, convidados e pelo link. Os lembretes automáticos também vão para quem começou pelo link e
  não terminou. O Excel de participação ganhou a coluna "Origem".

- **Planilha:** o painel gera o modelo com os cargos da empresa numa lista suspensa
  (`PlanilhaConvites`, Apache POI). Na importação, as colunas são achadas pelo cabeçalho (aceita
  .xlsx e .csv com ponto e vírgula ou vírgula); a linha de exemplo do modelo é ignorada.
- **Revisão:** antes de gravar, uma janela mostra todas as linhas: e-mail inválido ou repetido,
  WhatsApp errado, cargo fora da lista (com a sugestão mais parecida, ex.: "Gerente Comercial"
  → Gerente) e quem já tem convite. Dá para editar e remover ali mesmo.
- **Link pessoal:** a primeira tela é "Confira seus dados" (um toque em "Está tudo certo" ou
  corrigir ali). Depois vêm o tempo no cargo e as perguntas. Os convites entram no link principal
  da empresa, então os diagnósticos aparecem no relatório dela como os do link aberto.
- **Adicionar e corrigir:** quem ficou de fora da planilha entra por "Adicionar líder" (mesmas
  regras da importação). Corrigir os dados de quem já respondeu vale também no diagnóstico dele,
  então o cargo certo entra nos relatórios (as respostas e o resultado não mudam).
- **Inativar:** tira o líder da lista e de todos os cálculos (painel, funil, relatório da empresa
  e média geral; `diagnostico.inativado_em`), fecha o link pessoal e não apaga nada. Dá para
  reativar. Também vale para quem respondeu pelo link aberto, no detalhe do diagnóstico.
- **Auditoria:** cada convite guarda a linha do tempo: criado, editado, enviado (ou falha), abriu
  (com o aparelho), confirmou ou corrigiu os dados (com o que mudou), concluiu, inativado e reativado. O
  "abriu" é registrado pela própria página: o WhatsApp também abre o link para montar a prévia,
  mas não roda o JavaScript.
- **WhatsApp:** máscara `(11) 98765-4321` em todos os campos (no máximo 11 números; o 55 do país
  é aceito e descartado) e uma regra só, no front (`entrada-telefone.ts`) e no servidor
  (`Telefones.java`): DDD que existe, celular com o 9 depois do DDD, 10 ou 11 números. Mude as
  duas juntas. No painel o erro aparece ao sair do campo (DDD inexistente, já durante a digitação).
- **Relatório de participação** (PDF e Excel), para a empresa cobrar quem falta, já que ela não
  acessa o painel. Separa os líderes em "começaram e não terminaram", "abriram e não começaram",
  "ainda não abriram" e "já responderam", com o contato e o link pessoal de quem falta; nenhuma
  resposta individual, e os inativos ficam de fora. O PDF leva a marca da empresa e é paginado no
  servidor (`ParticipacaoService.paginar`, peças de altura fixa em `participacao.html`); o Excel
  (`PlanilhaParticipacao`) traz filtro, datas de verdade e um resumo com fórmulas já calculadas.
  No celular, o painel abre a folha de compartilhar com o PDF.
- **Exportar a lista de respostas** (PDF e Excel, ao lado dos filtros do painel): leva todos os
  diagnósticos com os filtros da tela (link, cargo, situação, faixa e busca), não só a página
  aberta (`ExportacaoDiagnosticos`). Documento interno: tem o nome e o resultado de cada líder,
  por isso sai com a marca do Diagnóstico do Líder, não a da empresa. O PDF (`diagnosticos.html`)
  é paginado no servidor como o de participação e vai até 1.000 diagnósticos; acima disso, o Excel
  (`PlanilhaDiagnosticos`), com datas de verdade, a pontuação de cada dimensão e o resumo com fórmulas.
- **E-mail:** o disparo usa o mesmo `EmailSender` do diagnóstico. Enquanto `EMAIL_PROVEDOR=log`,
  ele fica desligado no painel ("Envio por e-mail desligado neste servidor"), e nada é marcado
  como enviado.
- **Falhas de envio:** o `ResendEmailSender` repete o que é passageiro (limite por segundo,
  Resend instável, rede) com a mesma `Idempotency-Key`, para o líder não receber dois e-mails,
  e traduz o resto em `FalhaEnvio` com o motivo em português. Cota do plano, chave inválida ou
  domínio não verificado valem para todos: o lote para e os que faltam ficam com o motivo, sem
  chamar a Resend de novo. O painel mostra "N convites não saíram", o motivo e "Enviar de novo".

### Relatórios ao gestor e lembretes aos líderes

A empresa tem uma lista de gestores (nenhum, um ou vários; cadastro e edição). O pacote
`acompanhamento` roda a cada 5 minutos e a `Agenda` decide o que sai, sem repetir:

- **Diário:** dias úteis, a partir das 8h (Brasília), um por dia, para todos os gestores num
  e-mail só, com o relatório de participação em PDF e Excel anexos. O corpo traz o placar, o
  que mudou desde o último relatório e quem ainda não respondeu (os 15 primeiros). Começa
  quando sai o primeiro convite e sai mesmo sem novidade. O administrador pode pausar.
- **Final:** assim que todos os convidados respondem ou o prazo vence (mesmo pausado e em
  qualquer dia/hora), com o consolidado, a participação e o Excel. Com menos de
  `MINIMO_LIDERES` concluídos, vai só a participação e o aviso. Se a avaliação reabrir (prazo
  novo, líder novo), o diário volta e o final sai de novo no fim. A empresa que só usa o link
  geral recebe o consolidado no prazo.
- **Lembretes aos líderes:** 3 dias e 1 dia antes do prazo, a partir das 8h, para quem recebeu
  o convite e não concluiu (o mesmo template do convite, com a chamada do prazo). Zeram se o
  prazo mudar.
- Nada sai depois das 20h, e um envio ao gestor que falha é tentado até 3 vezes no dia.
  O histórico (`envio_acompanhamento`) mostra o que saiu, quando e para quem.

### Acesso do RH (página de acompanhamento)

Os gestores da empresa (a mesma lista que recebe os relatórios) acompanham a avaliação em
`/acompanhar`, no celular ou no computador, com a marca da empresa. Só leitura.

- **Entrada sem senha:** a pessoa digita o e-mail e recebe um código de 6 números (vale 10 minutos,
  5 tentativas, uma vez) com um botão "Entrar" no mesmo e-mail. A tela responde igual para quem não é
  gestor, sem mandar nada: não revela quem acompanha uma empresa. O aparelho fica lembrado por 30 dias
  desde o último uso (cookie `ACESSO_RH` HttpOnly, só para `/api/acompanhar`; o hash do token fica no
  banco, então publicar uma versão nova não desloga ninguém). Tirar o e-mail da lista de gestores tira o
  acesso na hora. Pedir código tem limite (3 por e-mail e 10 por IP a cada 15 min), porque gasta e-mail.
- **No painel**, o cartão "Relatórios para o gestor" mostra cada gestor com o último acesso e o
  "Mandar acesso" (botão que entra direto, vale 7 dias, uma vez). O token do botão vai depois do `#`
  (`/acompanhar/entrar#…`): não passa pelo servidor nem por quem pré-visualiza links. O e-mail diário
  e o final ao gestor trazem o botão "Acompanhar ao vivo".
- **Participação ao vivo** (atualiza a cada minuto com a tela aberta): o placar, quem falta em grupos
  (começou, abriu, não abriu) com o WhatsApp do líder já com a mensagem e o link pessoal, copiar o link,
  quem já respondeu (só o nome) e o relatório de participação em PDF e Excel.
- **Resultado do grupo, anônimo:** o RH vê quem acabou de responder, então se a média mudasse a cada
  resposta uma conta mostraria o índice dessa pessoa (antes 10 com média 50, depois 11 com 52: quem
  entrou tem 72). Por isso o resultado é uma foto (`resultado_foto`, regra em `RegraDaFoto`, com testes):
  abre com 3 concluídos e só muda quando chegam pelo menos 3 respostas novas. No fim da avaliação, se só
  1 ou 2 responderam depois da última foto, eles ficam de fora do resultado final, com aviso. A média
  geral de referência fica congelada na foto. Cargos e perguntas só aparecem no resultado final (um cargo
  pode ganhar uma pessoa só entre duas fotos). Os pontos da curva vão em ordem de índice, nunca na
  ordem de chegada. Limite que continua valendo: se as 3 respostas novas caem todas na mesma faixa, quem
  sabe quem foram as 3 sabe a faixa delas.

| Método | Caminho                                          | O que faz                                               |
|--------|--------------------------------------------------|---------------------------------------------------------|
| GET    | `/api/acompanhar/sessao`                         | Quem está neste aparelho (`{ gestor }`) e o cookie CSRF  |
| POST   | `/api/acompanhar/codigo`                         | Manda o código (`{ email }`); 204 sempre                 |
| POST   | `/api/acompanhar/entrar`                         | `{ email, codigo }` ou `{ link }` (o botão do e-mail)    |
| POST   | `/api/acompanhar/sair`                           | Esquece este aparelho                                   |
| GET    | `/api/acompanhar/empresas/{id}`                  | Marca, prazo e participação ao vivo                     |
| POST   | `/api/acompanhar/empresas/{id}/visita`           | A página abriu (último acesso no painel)                |
| GET    | `/api/acompanhar/empresas/{id}/resultado`        | A foto do resultado (AGUARDANDO, PARCIAL ou FINAL)      |
| GET    | `/api/acompanhar/empresas/{id}/resultado.pdf`    | O consolidado da foto em PDF                            |
| GET    | `/api/acompanhar/empresas/{id}/participacao.pdf` | Participação em PDF (também `.xlsx`)                    |

### Auditoria (linha do tempo do líder)

Todo e-mail sai pelo `Correio` e fica em `email_enviado` com o id do Resend. O Resend avisa o
que aconteceu em `POST /api/public/webhooks/resend` (assinatura Svix conferida com
`RESEND_WEBHOOK_SECRET`; aviso repetido ou com mais de 5 min é ignorado): entregue, atrasado,
devolvido, spam, bloqueado ou falhou. **Sem pixel de abertura nem rastreio de clique**: e-mail
corporativo entrega melhor sem rastreio. O "abriu o link" já mostra o engajamento.

Na linha do tempo de cada convite: quem fez cada ação no painel (criar, editar com o antes e
depois, enviar, inativar, reativar, baixar o PDF individual), cada e-mail com a entrega, as
visitas ao link (com o aparelho), quantas perguntas respondeu, se viu o resultado e se
compartilhou ou baixou o PDF. Passos que se repetem atualizam a mesma linha, com a contagem.
E-mail devolvido ou marcado como spam aparece em vermelho na lista, no relatório diário ao
gestor e no PDF/Excel de participação ("O e-mail voltou: mande o link por WhatsApp"). A planilha
de participação traz a aba **Auditoria** com tudo de todos os líderes.

**Sem insistir no endereço que falhou** (`BloqueioDeEnvio`): se o último convite ou lembrete
voltou, foi marcado como spam ou o Resend bloqueou, nenhum envio sai de novo para o mesmo
e-mail (botão do detalhe, "Enviar de novo" da lista e lembretes automáticos). Outra devolução
ou outra queixa de spam derruba a reputação do domínio. Corrigir o e-mail do líder libera o
envio para o endereço novo. O envio em lote pede confirmação antes de sair.

No Resend: Webhooks → Add endpoint com a URL de produção e os eventos `email.delivered`,
`email.delivery_delayed`, `email.bounced`, `email.complained`, `email.failed` e `email.suppressed`.

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
| `EMAIL_REMETENTE`  | padrão `Diagnóstico do Líder <diagnostico@terasoftware.com.br>` |
| `RESEND_WEBHOOK_SECRET` | segredo do webhook do Resend (`whsec_…`): entrega, devolução e spam de cada e-mail |
| `EMAIL_RESPONDER_PARA` | para onde vão as respostas dos líderes (reply-to; o remetente não tem caixa de entrada) |
| `EMAIL_DESVIAR_PARA` | **só em desenvolvimento** (`backend/.env`): todo e-mail vai para este endereço. Nunca no Railway |
| `ADMIN_EMAIL`      | e-mail do primeiro acesso ao painel (só vale enquanto não houver usuários) |
| `ADMIN_SENHA`      | senha desse primeiro acesso (8+ caracteres); depois, troque em "Trocar minha senha" |
| `ADMIN_NOME`       | nome exibido (opcional, padrão "Administrador") |
| `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, `S3_REGION` | referências ao bucket (`${{logomarcas.ENDPOINT}}`, `${{logomarcas.BUCKET}}`, `${{logomarcas.ACCESS_KEY_ID}}`, `${{logomarcas.SECRET_ACCESS_KEY}}`, `${{logomarcas.REGION}}`) |
| `ARMAZENAMENTO_PREFIXO` | `producao` (pasta do ambiente dentro do bucket) |

O Railway só libera SMTP no plano Pro, por isso o e-mail usa a API HTTP da Resend.

O domínio `terasoftware.com.br` está na Resend (região São Paulo) e no DNS do Registro.br em
modo avançado: TXT `resend._domainkey` (DKIM), CNAME `send` e `rsend` (envio) e TXT `_dmarc`
(`v=DMARC1; p=none;`). No plano gratuito da Resend a cota é de 100 e-mails por dia e 3.000 por mês.
