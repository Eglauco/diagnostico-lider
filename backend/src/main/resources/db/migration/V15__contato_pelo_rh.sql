-- O RH também cobra pelo próprio e-mail (Outlook, Gmail, programa do computador) e pelo WhatsApp.
-- A página registra que abriu a mensagem (não dá para confirmar que saiu), para todos os gestores
-- saberem que alguém já cobrou.
alter table convite_evento drop constraint convite_evento_tipo_check;
alter table convite_evento add constraint convite_evento_tipo_check check (tipo in (
    'CRIADO', 'EDITADO', 'ENVIADO', 'FALHA_ENVIO', 'ABERTO', 'DADOS_CONFIRMADOS', 'DADOS_CORRIGIDOS', 'CONCLUIDO',
    'LEMBRETE', 'INATIVADO', 'REATIVADO', 'RESULTADO_ENVIADO', 'EMAIL_DEVOLVIDO', 'EMAIL_SPAM', 'COMECOU_RESPONDER',
    'VIU_RESULTADO', 'BAIXOU_PDF', 'COMPARTILHOU_PDF', 'PDF_BAIXADO_PAINEL', 'COMECOU_PELO_LINK', 'RESPONDEU_DE_NOVO',
    'CONTATO_EMAIL', 'CONTATO_WHATSAPP'));
