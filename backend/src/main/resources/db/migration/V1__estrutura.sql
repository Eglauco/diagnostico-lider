-- =====================================================================
-- Diagnóstico do Líder — estrutura inicial
--
-- Questionário orientado a dados e versionado:
--   questionario -> questionario_versao -> dimensao / pergunta -> opcao
--                                       -> faixa_resultado
-- Versões publicadas são imutáveis; cada diagnóstico aponta para a versão
-- que foi respondida, então mudar perguntas nunca invalida resultados antigos.
-- =====================================================================

create table empresa (
    id          bigint generated always as identity primary key,
    nome        varchar(160) not null,
    criado_em   timestamptz  not null default now()
);

create table questionario (
    id          bigint generated always as identity primary key,
    codigo      varchar(60)  not null unique,
    titulo      varchar(160) not null,
    criado_em   timestamptz  not null default now()
);

create table questionario_versao (
    id              bigint generated always as identity primary key,
    questionario_id bigint      not null references questionario (id),
    numero          int         not null,
    status          varchar(20) not null check (status in ('RASCUNHO', 'PUBLICADA', 'ARQUIVADA')),
    publicada_em    timestamptz,
    criado_em       timestamptz not null default now(),
    unique (questionario_id, numero)
);

-- No máximo uma versão publicada por questionário.
create unique index uk_questionario_versao_publicada
    on questionario_versao (questionario_id) where status = 'PUBLICADA';

create table dimensao (
    id          bigint generated always as identity primary key,
    versao_id   bigint       not null references questionario_versao (id),
    codigo      varchar(60)  not null,
    nome        varchar(120) not null,
    descricao   text,
    ordem       int          not null,
    unique (versao_id, codigo)
);

create table pergunta (
    id          bigint generated always as identity primary key,
    versao_id   bigint      not null references questionario_versao (id),
    dimensao_id bigint      references dimensao (id),
    codigo      varchar(60) not null,
    enunciado   text        not null,
    ajuda       text,
    tipo        varchar(20) not null check (tipo in ('ESCOLHA_UNICA', 'ESCOLHA_MULTIPLA')),
    obrigatoria boolean     not null default true,
    ordem       int         not null,
    unique (versao_id, codigo)
);

create index ix_pergunta_versao on pergunta (versao_id);

create table opcao (
    id          bigint generated always as identity primary key,
    pergunta_id bigint       not null references pergunta (id),
    texto       varchar(300) not null,
    pontos      int          not null,
    ordem       int          not null
);

create index ix_opcao_pergunta on opcao (pergunta_id);

create table faixa_resultado (
    id               bigint generated always as identity primary key,
    versao_id        bigint       not null references questionario_versao (id),
    codigo           varchar(40)  not null,
    titulo           varchar(120) not null,
    resumo           varchar(300) not null,
    descricao        text         not null,
    pontuacao_minima int          not null,
    pontuacao_maxima int          not null,
    unique (versao_id, codigo),
    check (pontuacao_minima between 0 and 100),
    check (pontuacao_maxima between 0 and 100),
    check (pontuacao_minima <= pontuacao_maxima)
);

create table faixa_recomendacao (
    faixa_id bigint not null references faixa_resultado (id),
    ordem    int    not null,
    texto    text   not null,
    primary key (faixa_id, ordem)
);

-- Campanha = o link que o líder recebe. Sem empresa: link geral, aberto a
-- qualquer cliente. Com empresa: os diagnósticos alimentam o relatório
-- consolidado daquela empresa.
create table campanha (
    id              bigint generated always as identity primary key,
    codigo          varchar(60)  not null unique,
    nome            varchar(160) not null,
    questionario_id bigint       not null references questionario (id),
    empresa_id      bigint       references empresa (id),
    ativa           boolean      not null default true,
    criado_em       timestamptz  not null default now()
);

create table diagnostico (
    id                uuid primary key,
    revisao           bigint       not null default 0,
    campanha_id       bigint       not null references campanha (id),
    versao_id         bigint       not null references questionario_versao (id),
    -- Copiada da campanha para o relatório consolidado não depender do link.
    empresa_id        bigint       references empresa (id),
    nome              varchar(160) not null,
    email             varchar(254) not null,
    whatsapp          varchar(20)  not null,
    empresa_informada varchar(160),
    status            varchar(20)  not null check (status in ('EM_ANDAMENTO', 'CONCLUIDO')),
    pontuacao         int          check (pontuacao between 0 and 100),
    faixa_id          bigint       references faixa_resultado (id),
    status_email      varchar(20)  not null default 'NAO_ENVIADO'
                      check (status_email in ('NAO_ENVIADO', 'PENDENTE', 'ENVIADO', 'FALHOU')),
    email_enviado_em  timestamptz,
    consentimento_em  timestamptz  not null,
    iniciado_em       timestamptz  not null,
    concluido_em      timestamptz
);

create index ix_diagnostico_empresa on diagnostico (empresa_id) where empresa_id is not null;
create index ix_diagnostico_campanha on diagnostico (campanha_id);
create index ix_diagnostico_email on diagnostico (lower(email));

create table resposta (
    id             bigint generated always as identity primary key,
    diagnostico_id uuid        not null references diagnostico (id) on delete cascade,
    pergunta_id    bigint      not null references pergunta (id),
    opcao_id       bigint      not null references opcao (id),
    respondida_em  timestamptz not null,
    unique (diagnostico_id, pergunta_id, opcao_id)
);

-- Pontuação por dimensão gravada na conclusão: o relatório consolidado da
-- empresa vira um simples AVG agrupado.
create table diagnostico_dimensao (
    id             bigint generated always as identity primary key,
    diagnostico_id uuid   not null references diagnostico (id) on delete cascade,
    dimensao_id    bigint not null references dimensao (id),
    pontuacao      int    not null check (pontuacao between 0 and 100),
    unique (diagnostico_id, dimensao_id)
);
