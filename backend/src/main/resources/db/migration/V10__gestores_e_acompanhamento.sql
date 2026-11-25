-- Gestores da empresa: recebem todo dia útil o relatório de participação (PDF e Excel) e, no fim
-- (todos responderam ou o prazo venceu), o relatório consolidado. Zero, um ou vários por empresa.
create table empresa_gestor (
    empresa_id bigint       not null references empresa (id) on delete cascade,
    ordem      integer      not null,
    email      varchar(254) not null,
    primary key (empresa_id, ordem)
);

-- O que já saiu automaticamente para cada empresa, para não repetir (o agendador roda a cada 5 min):
-- gestor_diario_em: último relatório diário; gestor_final_em: o consolidado final (volta a nulo se a
-- avaliação reabrir); lembrete_*: lembretes aos líderes antes do prazo (zeram se o prazo mudar).
alter table empresa add column gestor_pausado    boolean not null default false;
alter table empresa add column gestor_diario_em  timestamptz;
alter table empresa add column gestor_final_em   timestamptz;
alter table empresa add column lembrete_3_dias_em timestamptz;
alter table empresa add column lembrete_1_dia_em  timestamptz;

-- Histórico do que saiu sozinho (ou pelo "Enviar agora"): relatórios ao gestor e lembretes aos líderes.
create table envio_acompanhamento (
    id          bigserial    primary key,
    empresa_id  bigint       not null references empresa (id) on delete cascade,
    tipo        varchar(20)  not null check (tipo in ('DIARIO', 'FINAL', 'LEMBRETE')),
    manual      boolean      not null default false,
    quando      timestamptz  not null,
    para        varchar(2000) not null,
    responderam integer,
    total       integer,
    enviado     boolean      not null,
    detalhe     varchar(500)
);
create index ix_envio_acompanhamento on envio_acompanhamento (empresa_id, quando desc);
