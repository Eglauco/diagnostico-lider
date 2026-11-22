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
| Deploy    | Railway, via `Dockerfile` (uma imagem com o Angular embutido no Spring Boot)      |

## Estrutura

```
backend/   Spring Boot (pacote por funcionalidade)
  campanha/      links de diagnóstico (geral ou vinculado a uma empresa)
  questionario/  questionário versionado: dimensões, perguntas, opções e faixas de resultado
  diagnostico/   respostas, cálculo do índice (0–100) e API pública
  relatorio/     PDF (template Thymeleaf + Chromium) e a geometria da curva
  notificacao/   envio do PDF por e-mail após a conclusão (assíncrono)
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

Links de exemplo: `/` (link geral) e `/c/demo` (vinculado à "Empresa Demonstração").

## API pública

| Método | Caminho                                              | O que faz                                   |
|--------|------------------------------------------------------|---------------------------------------------|
| GET    | `/api/public/campanhas/{codigo}`                     | Campanha + questionário (sem pesos)         |
| POST   | `/api/public/campanhas/{codigo}/diagnosticos`        | Inicia com nome, e-mail, WhatsApp, empresa  |
| GET    | `/api/public/diagnosticos/{id}`                      | Estado para retomar                         |
| PUT    | `/api/public/diagnosticos/{id}/respondente`          | Corrige os dados do líder                   |
| PUT    | `/api/public/diagnosticos/{id}/respostas/{pergunta}` | Grava a resposta                            |
| POST   | `/api/public/diagnosticos/{id}/finalizar`            | Calcula o resultado (idempotente)           |
| GET    | `/api/public/diagnosticos/{id}/resultado`            | Resultado                                   |
| GET    | `/api/public/diagnosticos/{id}/relatorio.pdf`        | PDF (`?visualizar=true` abre no navegador)  |

Erros seguem `application/problem+json` (RFC 9457); validação traz o mapa `campos`.

## Deploy no Railway

Três serviços no mesmo projeto:

1. **Postgres**: banco gerenciado do Railway.
2. **gotenberg**: imagem `gotenberg/gotenberg:9.9.3-chromium`, só na rede privada (porta 3000).
3. **app**: este repositório (o Railway usa o `Dockerfile` da raiz). Health check: `/actuator/health`.

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

O Railway só libera SMTP no plano Pro, por isso o e-mail usa a API HTTP da Resend.
