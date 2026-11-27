-- =====================================================================
-- Acesso do RH: os gestores da empresa acompanham a avaliação ao vivo em /acompanhar, sem senha.
-- Entram com um código de 6 dígitos que chega por e-mail (ou pelo botão do e-mail).
-- =====================================================================

-- Códigos de entrada. Só o hash fica no banco. tipo CODIGO: pedido na tela (código + botão, vale
-- 10 min); CONVITE: mandado pelo administrador (só o botão, vale 7 dias). Os dois servem uma vez.
create table rh_codigo (
    id          bigserial    primary key,
    email       varchar(254) not null,
    tipo        varchar(10)  not null check (tipo in ('CODIGO', 'CONVITE')),
    codigo_hash varchar(64),
    link_hash   varchar(64)  not null unique,
    criado_em   timestamptz  not null,
    expira_em   timestamptz  not null,
    tentativas  integer      not null default 0,
    usado_em    timestamptz
);
create index ix_rh_codigo_email on rh_codigo (email, criado_em desc);

-- Aparelhos lembrados (30 dias desde o último uso). Fica no banco, então publicar uma versão nova
-- não desloga o RH. As empresas de cada pessoa saem da lista de gestores a cada chamada: tirar o
-- e-mail da lista tira o acesso na hora.
create table rh_acesso (
    id           bigserial    primary key,
    email        varchar(254) not null,
    token_hash   varchar(64)  not null unique,
    criado_em    timestamptz  not null,
    usado_em     timestamptz  not null,
    expira_em    timestamptz  not null,
    encerrado_em timestamptz,
    aparelho     varchar(120)
);
create index ix_rh_acesso_email on rh_acesso (email);

-- O que o RH abriu e baixou, para o painel mostrar o último acesso de cada gestor.
create table rh_atividade (
    id         bigserial    primary key,
    empresa_id bigint       not null references empresa (id) on delete cascade,
    email      varchar(254) not null,
    tipo       varchar(30)  not null check (tipo in ('ENTROU', 'ABRIU', 'BAIXOU_PARTICIPACAO_PDF',
                                                     'BAIXOU_PARTICIPACAO_XLSX', 'BAIXOU_RESULTADO_PDF')),
    quando     timestamptz  not null,
    aparelho   varchar(120)
);
create index ix_rh_atividade on rh_atividade (empresa_id, email, quando desc);

-- =====================================================================
-- Resultado da empresa em "fotos". Tudo o que a empresa vê do resultado (a tela do RH, o PDF
-- consolidado e o e-mail final) sai da última foto, e uma foto nova só é tirada quando chegam
-- pelo menos 3 respostas novas. Assim a diferença entre duas fotos nunca é uma pessoa só: o RH
-- vê quem acabou de responder, e com a média mudando a cada resposta daria para fazer a conta.
-- final: a avaliação tinha chegado ao fim (aí entram cargos e perguntas).
-- A média geral de referência também fica congelada na foto, pelo mesmo motivo.
-- =====================================================================
create table resultado_foto (
    id                 bigserial   primary key,
    empresa_id         bigint      not null references empresa (id) on delete cascade,
    tirada_em          timestamptz not null,
    final              boolean     not null,
    referencia_media   double precision,
    referencia_lideres integer
);
create index ix_resultado_foto on resultado_foto (empresa_id, tirada_em desc, id desc);

create table resultado_foto_diagnostico (
    foto_id        bigint not null references resultado_foto (id) on delete cascade,
    diagnostico_id uuid   not null references diagnostico (id) on delete cascade,
    primary key (foto_id, diagnostico_id)
);

create table resultado_foto_dimensao (
    foto_id bigint           not null references resultado_foto (id) on delete cascade,
    codigo  varchar(60)      not null,
    media   double precision not null,
    primary key (foto_id, codigo)
);

-- O e-mail com o código (ou o convite do administrador) entra no histórico como os outros.
alter table email_enviado drop constraint email_enviado_tipo_check;
alter table email_enviado add constraint email_enviado_tipo_check check (tipo in (
    'CONVITE', 'LEMBRETE', 'RESULTADO', 'GESTOR_DIARIO', 'GESTOR_FINAL', 'ACESSO_RH'));
