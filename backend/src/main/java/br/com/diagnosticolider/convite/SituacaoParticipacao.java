package br.com.diagnosticolider.convite;

/**
 * A situação do líder como a empresa precisa ver para cobrar a resposta, na ordem do relatório:
 * primeiro quem está mais perto de terminar. "Convite enviado" e "aguardando envio" viram uma
 * coisa só para a empresa: o líder ainda não abriu o link.
 */
enum SituacaoParticipacao {

	COMECOU("Começou, não terminou", "Começaram e não terminaram",
			"Já estão no meio: um lembrete curto costuma bastar. O link pessoal continua de onde pararam."),

	ABRIU("Abriu, não começou", "Abriram o link, mas não começaram",
			"Já viram o convite. Vale lembrar o prazo e que as respostas de cada um não são mostradas à empresa."),

	NAO_ABRIU("Não abriu o link", "Ainda não abriram o link",
			"Mande a cada um o link pessoal dele, por e-mail ou WhatsApp: os dados já vêm preenchidos."),

	RESPONDEU("Respondeu", "Já responderam", "Não precisam fazer mais nada.");

	private final String rotulo;

	private final String titulo;

	private final String dica;

	SituacaoParticipacao(String rotulo, String titulo, String dica) {
		this.rotulo = rotulo;
		this.titulo = titulo;
		this.dica = dica;
	}

	/** Null para o inativo: ele fica fora do relatório. */
	static SituacaoParticipacao de(StatusConvite status) {
		return switch (status) {
			case CONCLUIDO -> RESPONDEU;
			case COMECOU -> COMECOU;
			case ABRIU -> ABRIU;
			case ENVIADO, AGUARDANDO_ENVIO -> NAO_ABRIU;
			case INATIVO -> null;
		};
	}

	/** Na planilha e na legenda: "Respondeu", "Não abriu o link"… */
	String rotulo() {
		return rotulo;
	}

	/** Título do grupo no PDF: "Ainda não abriram o link". */
	String titulo() {
		return titulo;
	}

	/** Como cobrar quem está nesse grupo. */
	String dica() {
		return dica;
	}

	boolean pendente() {
		return this != RESPONDEU;
	}

}
