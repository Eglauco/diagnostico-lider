package br.com.diagnosticolider.campanha;

import br.com.diagnosticolider.questionario.QuestionarioDto;

/** O que o front precisa para abrir o link: a campanha, a empresa (se houver) e o questionário. */
public record CampanhaPublicaDto(String codigo, String nome, EmpresaDto empresa, QuestionarioDto questionario) {

	public record EmpresaDto(String nome) {
	}

}
