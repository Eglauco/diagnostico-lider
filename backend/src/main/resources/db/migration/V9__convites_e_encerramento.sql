-- Encerramento da avaliação da empresa: depois desta data e hora os links fecham, os convites
-- e lembretes param de sair e o relatório da empresa passa a ser o resultado final.
alter table empresa add column avaliacao_encerra_em timestamptz;

-- Líder inativado pelo administrador: o diagnóstico continua guardado, mas sai de todos os
-- cálculos (painel, funil, relatório da empresa e média geral) até ser reativado.
alter table diagnostico add column inativado_em timestamptz;

-- Convite nominal: um link pessoal por líder (/i/{token}), com os dados da planilha do RH para
-- ele só confirmar. O diagnóstico nasce do convite (diagnostico_id) quando ele confirma os dados.
create table convite (
    id                 bigserial    primary key,
    campanha_id        bigint       not null references campanha (id),
    token              varchar(16)  not null unique,
    nome               varchar(160) not null,
    email              varchar(254) not null,
    whatsapp           varchar(20),
    cargo_id           bigint       references cargo (id),
    cargo_outro        boolean      not null default false,
    criado_em          timestamptz  not null,
    enviado_em         timestamptz,
    envios             integer      not null default 0,
    primeiro_acesso_em timestamptz,
    ultimo_acesso_em   timestamptz,
    acessos            integer      not null default 0,
    diagnostico_id     uuid         unique references diagnostico (id),
    inativado_em       timestamptz
);

-- Um convite por e-mail em cada link (o e-mail identifica o líder; inativo também: reativa-se).
create unique index ux_convite_email on convite (campanha_id, lower(email));
create index ix_convite_campanha on convite (campanha_id);

-- Auditoria de cada convite: o que aconteceu com o link, e quando.
create table convite_evento (
    id         bigserial    primary key,
    convite_id bigint       not null references convite (id) on delete cascade,
    tipo       varchar(30)  not null check (tipo in ('CRIADO', 'EDITADO', 'ENVIADO', 'FALHA_ENVIO', 'ABERTO',
                                                     'DADOS_CONFIRMADOS', 'DADOS_CORRIGIDOS', 'CONCLUIDO',
                                                     'LEMBRETE', 'INATIVADO', 'REATIVADO')),
    quando     timestamptz  not null,
    detalhe    varchar(500)
);
create index ix_convite_evento on convite_evento (convite_id, quando);
