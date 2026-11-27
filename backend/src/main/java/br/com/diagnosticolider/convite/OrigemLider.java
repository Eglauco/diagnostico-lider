package br.com.diagnosticolider.convite;

/** Como o líder entrou na avaliação da empresa. */
public enum OrigemLider {

	/** Veio na planilha que o RH mandou (importada no painel). */
	PLANILHA("Planilha"),

	/** Adicionado um a um no painel. */
	MANUAL("Cadastro manual"),

	/** Começou sozinho pelo link aberto da empresa (/c/...), sem convite. */
	LINK("Link da empresa");

	private final String rotulo;

	OrigemLider(String rotulo) {
		this.rotulo = rotulo;
	}

	public String rotulo() {
		return rotulo;
	}

}
