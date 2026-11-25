package br.com.diagnosticolider.campanha;

import java.time.Instant;
import java.util.List;

import br.com.diagnosticolider.empresa.MarcaEmpresa;
import br.com.diagnosticolider.questionario.QuestionarioDto;

/**
 * O que o front precisa para abrir o link: a campanha, a empresa (se houver, com a paleta), o
 * questionário e, quando a empresa tem cargos cadastrados, as opções de cargo e de tempo no cargo.
 * encerraEm: até quando a avaliação da empresa vai (null sem data); encerrada: já passou dela.
 */
public record CampanhaPublicaDto(String codigo, String nome, MarcaEmpresa empresa, Perfil perfil,
		QuestionarioDto questionario, Instant encerraEm, boolean encerrada) {

	/** Nulo quando não há o que perguntar (link geral ou empresa sem cargos). */
	public record Perfil(List<Opcao> cargos, List<Opcao> tempos) {
	}

	/** id do cargo ou código do tempo no cargo. */
	public record Opcao(String valor, String texto) {
	}

}
