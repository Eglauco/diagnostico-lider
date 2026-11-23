package br.com.diagnosticolider.campanha;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodigoCampanhaTest {

	@Test
	void tiraAcentosEspacosESimbolos() {
		assertThat(CodigoCampanha.normalizar("Grupo Ação & Cia.")).isEqualTo("grupo-acao-cia");
		assertThat(CodigoCampanha.normalizar("  --Olá   Mundo--  ")).isEqualTo("ola-mundo");
		assertThat(CodigoCampanha.normalizar("São João 2026")).isEqualTo("sao-joao-2026");
		assertThat(CodigoCampanha.normalizar(null)).isEmpty();
	}

	@Test
	void validaTamanhoEFormato() {
		assertThat(CodigoCampanha.valido("ab")).isFalse();
		assertThat(CodigoCampanha.valido("abc")).isTrue();
		assertThat(CodigoCampanha.valido("Grupo")).isFalse();
		assertThat(CodigoCampanha.valido("grupo--alfa")).isFalse();
		assertThat(CodigoCampanha.valido("a".repeat(61))).isFalse();
	}

	@Test
	void sufixoNaoPassaDoTamanhoMaximo() {
		String longo = "a".repeat(30) + "-" + "b".repeat(29);
		assertThat(CodigoCampanha.comSufixo("grupo-alfa", 2)).isEqualTo("grupo-alfa-2");
		assertThat(CodigoCampanha.comSufixo(longo, 12)).hasSize(60).endsWith("-12");
		assertThat(CodigoCampanha.comSufixo("a".repeat(30) + "-" + "b".repeat(29), 2)).doesNotContain("--");
	}

}
