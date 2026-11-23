-- Empresas cadastradas pelo painel: o mesmo nome duas vezes é quase sempre engano
-- (e dividiria o relatório consolidado em dois).
create unique index uk_empresa_nome on empresa (lower(nome));
