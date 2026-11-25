package br.com.diagnosticolider.compartilhado;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelefonesTest {

	@Test
	void celularEFixoComDddValidoPassam() {
		assertThat(Telefones.problema("(11) 98765-4321")).isEmpty();
		assertThat(Telefones.problema("+55 21 99876-5432")).isEmpty();
		assertThat(Telefones.problema("1133334444")).isEmpty();
		assertThat(Telefones.problema("")).isEmpty();
		assertThat(Telefones.problema(null)).isEmpty();
	}

	@Test
	void numeroComDigitosDeMaisOuDeMenosEhRecusado() {
		assertThat(Telefones.problema("(18) 91829-128391283")).hasValueSatisfying(m -> assertThat(m).startsWith("Tem números a mais"));
		assertThat(Telefones.problema("(11) 9876")).hasValueSatisfying(m -> assertThat(m).startsWith("Faltam números"));
	}

	@Test
	void dddQueNaoExisteEhRecusado() {
		assertThat(Telefones.problema("(20) 98765-4321")).contains("O DDD 20 não existe: confira os dois primeiros números.");
		assertThat(Telefones.problema("(01) 98765-4321")).hasValueSatisfying(m -> assertThat(m).contains("DDD 01"));
	}

	@Test
	void celularSemONoveEhRecusado() {
		assertThat(Telefones.problema("(11) 88765-4321")).hasValueSatisfying(m -> assertThat(m).startsWith("Celular tem o 9"));
		assertThat(Telefones.problema("(11) 8765-4321")).contains("Falta o 9 do celular logo depois do DDD.");
		assertThat(Telefones.problema("(11) 1765-4321")).hasValueSatisfying(m -> assertThat(m).startsWith("Confira o número"));
	}

	@Test
	void formataSemO55EMantemOTextoQuandoNaoDa() {
		assertThat(Telefones.formatar("5511987654321")).isEqualTo("(11) 98765-4321");
		assertThat(Telefones.formatar("1133334444")).isEqualTo("(11) 3333-4444");
		assertThat(Telefones.formatar(" 1234 ")).isEqualTo("1234");
		assertThat(Telefones.formatar(null)).isEmpty();
	}

}
