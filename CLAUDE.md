# Diagnóstico do Líder

App de diagnóstico de carreira para líderes (questionário → resultado na tela + PDF por e-mail).
Visão geral, estrutura, API e deploy: ver `README.md`. Regras do Angular: `frontend/CLAUDE.md`.

## Regras do projeto

- **UI/UX é a prioridade máxima.** A maioria dos líderes responde pelo celular: desenhe para
  375px primeiro (alvos de toque ≥ 48px, teclado certo por campo, safe area, `100dvh`) e teste
  no navegador em viewport mobile antes de dar por pronto.
- **Versões sempre as mais recentes** (Java, Spring Boot, Angular, libs), desde que rodem no
  Railway (deploy via Dockerfile: confira se a imagem base existe).
- Textos da interface em português do Brasil, segunda pessoa ("você"), verbos claros.

## Identidade visual

- Cores (tokens em `frontend/src/styles.css`): abeto `#0F2B26` (tinta), névoa `#EDF2EF` (fundo),
  brasa `#FF5B37` reservada à curva da carreira. Faixas: travada `#C2364B`, platô `#A86A00`,
  ascensão `#1F8A5B`.
- Tipografia: Bricolage Grotesque (títulos, classe `display`) e Instrument Sans (texto).
- A curva da carreira é a assinatura visual. A mesma função existe em
  `frontend/src/app/compartilhado/curva-carreira.ts` e
  `backend/.../relatorio/CurvaCarreira.java`: mude as duas juntas.
- A regra do WhatsApp (DDD, 9 do celular, tamanho e mensagens) também é dupla:
  `frontend/src/app/compartilhado/entrada-telefone.ts` e `backend/.../compartilhado/Telefones.java`.
- A regra do e-mail (o que falta: @, domínio, final `.com.br`) também: `frontend/src/app/compartilhado/email.ts`
  e `backend/.../compartilhado/Emails.java`.

## Rodando nesta máquina

- JDK 27 portátil em `~/.jdks/jdk-27.0.1.0.2+1` (o JDK do sistema é 21): exporte `JAVA_HOME`
  antes do `./mvnw`.
- Se o Tomcat falhar com `Unable to establish loopback connection`, o diretório temporário é
  longo demais para o socket Unix do JDK: rode com
  `-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=<caminho curto sem espaços>`.
- Sem Docker local: o PDF em dev usa o Chrome instalado (`PDF_MOTOR=chrome`, padrão).
- E-mail em dev: o `backend/.env` liga o Resend (`EMAIL_PROVEDOR=resend`) e desvia tudo para
  `EMAIL_DESVIAR_PARA` (o Gmail do dono), com o destinatário original no assunto. Deixe sempre
  ligado: os testes precisam ser reais como em produção. Nunca leve `EMAIL_DESVIAR_PARA` ao Railway.
- PostgreSQL local: `localhost:5432/diagnostico_lider` (postgres/root).
