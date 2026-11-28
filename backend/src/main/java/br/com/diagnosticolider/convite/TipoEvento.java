package br.com.diagnosticolider.convite;

/** O que aconteceu com um convite (auditoria), com o rótulo que aparece no painel e no Excel. */
public enum TipoEvento {

	CRIADO("Convite criado"),
	EDITADO("Dados editados no painel"),
	ENVIADO("E-mail de convite enviado"),
	FALHA_ENVIO("O e-mail não saiu"),
	ABERTO("Abriu o link"),
	DADOS_CONFIRMADOS("Confirmou os dados"),
	DADOS_CORRIGIDOS("Corrigiu os dados"),
	COMECOU_RESPONDER("Começou a responder"),
	CONCLUIDO("Concluiu o diagnóstico"),
	RESULTADO_ENVIADO("Resultado enviado por e-mail"),
	VIU_RESULTADO("Viu o resultado"),
	BAIXOU_PDF("Baixou o PDF"),
	COMPARTILHOU_PDF("Compartilhou o PDF"),
	LEMBRETE("Lembrete enviado"),
	EMAIL_DEVOLVIDO("O e-mail voltou"),
	EMAIL_SPAM("Marcou o e-mail como spam"),
	PDF_BAIXADO_PAINEL("PDF individual baixado no painel"),
	INATIVADO("Líder inativado"),
	REATIVADO("Líder reativado"),
	COMECOU_PELO_LINK("Começou pelo link da empresa"),
	RESPONDEU_DE_NOVO("Respondeu de novo"),
	/** O RH abriu uma mensagem no próprio e-mail (Outlook, Gmail...): a plataforma não confirma que saiu. */
	CONTATO_EMAIL("Mensagem pelo e-mail do RH"),
	/** O RH abriu o WhatsApp com a mensagem pronta. */
	CONTATO_WHATSAPP("Mensagem pelo WhatsApp do RH");

	private final String rotulo;

	TipoEvento(String rotulo) {
		this.rotulo = rotulo;
	}

	public String rotulo() {
		return rotulo;
	}

	/** O e-mail não chegou ao líder: aparece em vermelho na lista e no relatório ao gestor. */
	boolean problemaDeEntrega() {
		return this == FALHA_ENVIO || this == EMAIL_DEVOLVIDO || this == EMAIL_SPAM;
	}

}
