-- =====================================================================
-- Usuários do painel administrativo. Sem perfis por enquanto: quem entra
-- acessa tudo. "credenciais_em" muda quando a senha é trocada ou o acesso
-- é desativado, e derruba as sessões abertas com a credencial antiga.
-- =====================================================================

create table usuario (
    id                bigint generated always as identity primary key,
    nome              varchar(120) not null,
    email             varchar(254) not null,
    senha_hash        varchar(200) not null,
    ativo             boolean      not null default true,
    credenciais_em    timestamptz  not null default now(),
    criado_em         timestamptz  not null default now(),
    ultimo_acesso_em  timestamptz
);

create unique index uk_usuario_email on usuario (lower(email));
