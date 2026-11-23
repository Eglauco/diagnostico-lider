package br.com.diagnosticolider.diagnostico;

/** Há quanto tempo o líder está no cargo atual, em faixas de anos inteiros. */
public enum TempoCargo {

	MENOS_DE_1("Menos de 1 ano"),
	DE_1_A_2("De 1 a 2 anos"),
	DE_3_A_5("De 3 a 5 anos"),
	MAIS_DE_5("Mais de 5 anos");

	private final String texto;

	TempoCargo(String texto) {
		this.texto = texto;
	}

	public String texto() {
		return texto;
	}

}
