-- Cores da marca de cada empresa: o questionário, o resultado, o PDF individual, o e-mail,
-- o relatório consolidado e o cartão do QR Code usam a paleta gerada a partir delas.
-- Sem cor (null), vale a identidade do Diagnóstico do Líder.
alter table empresa
    add column cor_primaria   varchar(7) check (cor_primaria ~ '^#[0-9A-F]{6}$'),
    add column cor_secundaria varchar(7) check (cor_secundaria ~ '^#[0-9A-F]{6}$');
