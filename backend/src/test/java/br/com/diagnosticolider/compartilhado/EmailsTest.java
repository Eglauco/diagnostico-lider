package br.com.diagnosticolider.compartilhado;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A mensagem diz o que falta no e-mail, em vez de um "e-mail inválido" genérico. */
class EmailsTest {

	@Test
	void emailCertoNaoTemProblema() {
		assertThat(Emails.problema("ana.souza+rh@empresa.com.br")).isEmpty();
		assertThat(Emails.problema("  ana@empresa.com  ")).isEmpty();
		assertThat(Emails.problema("")).isEmpty();
		assertThat(Emails.problema(null)).isEmpty();
	}

	@Test
	void dizOQueFalta() {
		assertThat(Emails.problema("grupoefmelo@gmail")).contains("Falta o final do domínio (ex.: .com ou .com.br).");
		assertThat(Emails.problema("anaempresa.com")).contains("Falta o @ no e-mail (ex.: nome@empresa.com.br).");
		assertThat(Emails.problema("@empresa.com")).hasValueSatisfying(m -> assertThat(m).startsWith("Falta o nome antes do @"));
		assertThat(Emails.problema("ana@")).hasValueSatisfying(m -> assertThat(m).startsWith("Falta o domínio depois do @"));
		assertThat(Emails.problema("ana@@empresa.com")).contains("O e-mail tem mais de um @.");
		assertThat(Emails.problema("ana souza@empresa.com")).contains("Tire os espaços do e-mail.");
		assertThat(Emails.problema("ana@empresa..com")).hasValueSatisfying(m -> assertThat(m).contains("ponto"));
		assertThat(Emails.problema("ana@empresa.com.")).hasValueSatisfying(m -> assertThat(m).contains("ponto"));
		assertThat(Emails.problema("a".repeat(250) + "@x.com")).contains("E-mail muito longo.");
	}

}
