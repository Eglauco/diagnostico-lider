-- Cargos de cada empresa, em ordem hierárquica (ordem 0 = primeiro nível de liderança).
-- O líder escolhe o cargo no link da empresa e o relatório mostra em que nível a carreira trava.
-- Cargo removido que já tem diagnósticos é arquivado (ativo = false): sai da lista do líder,
-- mas continua nos relatórios. Sem diagnósticos, ele é apagado.
create table cargo (
    id         bigint generated always as identity primary key,
    empresa_id bigint       not null references empresa (id) on delete cascade,
    nome       varchar(80)  not null,
    ordem      int          not null,
    ativo      boolean      not null default true,
    criado_em  timestamptz  not null default now()
);

create index ix_cargo_empresa on cargo (empresa_id, ordem);

-- O que o líder informou no link da empresa. Vazio no link geral, em empresas sem cargos e nos
-- diagnósticos feitos antes desta versão. "Outro cargo": cargo_id nulo e cargo_outro = true.
alter table diagnostico
    add column cargo_id         bigint      references cargo (id),
    add column cargo_outro      boolean     not null default false,
    add column cargo_outro_nome varchar(80),
    add column tempo_cargo      varchar(20) check (tempo_cargo in ('MENOS_DE_1', 'DE_1_A_2', 'DE_3_A_5', 'MAIS_DE_5'));

create index ix_diagnostico_cargo on diagnostico (cargo_id) where cargo_id is not null;
