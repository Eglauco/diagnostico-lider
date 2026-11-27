-- =====================================================================
-- Todo líder da avaliação vira um cadastro (a tabela convite), com a origem: veio da planilha do RH,
-- foi adicionado no painel ou começou sozinho pelo link aberto da empresa. Assim o painel e a página
-- do RH contam as mesmas pessoas, e quem respondeu pelo link aberto não fica "não abriu" no convite.
-- =====================================================================
alter table convite add column origem varchar(10) not null default 'PLANILHA'
    check (origem in ('PLANILHA', 'MANUAL', 'LINK'));

-- Quem foi adicionado um a um no painel (a auditoria guarda isso desde o início).
update convite c set origem = 'MANUAL'
where exists (select 1 from convite_evento e
              where e.convite_id = c.id and e.tipo = 'CRIADO' and e.detalhe = 'Adicionado no painel.');

alter table convite alter column origem drop default;

-- Respondeu de novo: vale a resposta mais recente. A anterior sai dos cálculos (inativado_em) e
-- aponta para a que ficou no lugar dela.
alter table diagnostico add column substituido_por uuid references diagnostico (id);

alter table convite_evento drop constraint convite_evento_tipo_check;
alter table convite_evento add constraint convite_evento_tipo_check check (tipo in (
    'CRIADO', 'EDITADO', 'ENVIADO', 'FALHA_ENVIO', 'ABERTO', 'DADOS_CONFIRMADOS', 'DADOS_CORRIGIDOS', 'CONCLUIDO',
    'LEMBRETE', 'INATIVADO', 'REATIVADO', 'RESULTADO_ENVIADO', 'EMAIL_DEVOLVIDO', 'EMAIL_SPAM', 'COMECOU_RESPONDER',
    'VIU_RESULTADO', 'BAIXOU_PDF', 'COMPARTILHOU_PDF', 'PDF_BAIXADO_PAINEL', 'COMECOU_PELO_LINK', 'RESPONDEU_DE_NOVO'));

-- Os líderes que começaram pelo link aberto antes desta versão viram cadastro na subida do app
-- (convite.LideresDoLink), com a mesma regra de quem chega agora.
