-- Todo e-mail que o sistema manda (convite, lembrete, resultado, relatórios ao gestor), com o id
-- do provedor (Resend) e o que aconteceu depois: entregue, atrasado, devolvido, spam ou bloqueado.
-- Sem pixel de abertura: e-mail corporativo entrega melhor sem rastreio.
create table email_enviado (
    id             bigserial     primary key,
    provedor_id    varchar(100)  unique,
    tipo           varchar(20)   not null check (tipo in ('CONVITE', 'LEMBRETE', 'RESULTADO', 'GESTOR_DIARIO',
                                                          'GESTOR_FINAL')),
    empresa_id     bigint        references empresa (id) on delete cascade,
    convite_id     bigint        references convite (id) on delete cascade,
    diagnostico_id uuid          references diagnostico (id) on delete cascade,
    para           varchar(2000) not null,
    assunto        varchar(300)  not null,
    enviado_em     timestamptz   not null,
    situacao       varchar(20)   not null default 'ENVIADO'
                                 check (situacao in ('ENVIADO', 'ENTREGUE', 'ATRASADO', 'DEVOLVIDO', 'SPAM',
                                                     'BLOQUEADO', 'FALHOU')),
    entregue_em    timestamptz,
    atualizado_em  timestamptz,
    detalhe        varchar(500)
);
create index ix_email_enviado_convite on email_enviado (convite_id);
create index ix_email_enviado_empresa on email_enviado (empresa_id, enviado_em desc);

-- Avisos do Resend já processados (ele repete o mesmo aviso se não recebe 200 a tempo).
create table webhook_recebido (
    id          varchar(100) primary key,
    recebido_em timestamptz  not null
);

-- Linha do tempo do líder: quem fez (administrador; nulo quando foi o líder ou o sistema), o e-mail
-- daquele evento e os novos passos (resultado por e-mail, e-mail devolvido, passos do diagnóstico).
alter table convite_evento add column autor varchar(160);
alter table convite_evento add column email_id bigint references email_enviado (id) on delete set null;
alter table convite_evento drop constraint convite_evento_tipo_check;
alter table convite_evento add constraint convite_evento_tipo_check check (tipo in (
    'CRIADO', 'EDITADO', 'ENVIADO', 'FALHA_ENVIO', 'ABERTO', 'DADOS_CONFIRMADOS', 'DADOS_CORRIGIDOS', 'CONCLUIDO',
    'LEMBRETE', 'INATIVADO', 'REATIVADO', 'RESULTADO_ENVIADO', 'EMAIL_DEVOLVIDO', 'EMAIL_SPAM', 'COMECOU_RESPONDER',
    'VIU_RESULTADO', 'BAIXOU_PDF', 'COMPARTILHOU_PDF', 'PDF_BAIXADO_PAINEL'));

alter table envio_acompanhamento add column autor varchar(160);
alter table envio_acompanhamento add column email_id bigint references email_enviado (id) on delete set null;
