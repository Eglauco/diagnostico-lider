package br.com.diagnosticolider.acesso;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** O autor das ações feitas pela página do RH ("RH · e-mail"), que a linha do tempo do RH mostra. */
class QuemEstaTest {

	@Test
	void reconheceQuemAgiuPelaPaginaDoRh() {
		String autor = QuemEsta.PREFIXO_RH + "rh@empresa.com.br";

		assertThat(QuemEsta.doRh(autor)).isTrue();
		assertThat(QuemEsta.emailDoRh(autor)).isEqualTo("rh@empresa.com.br");
	}

	@Test
	void administradorELiderNaoSaoDoRh() {
		assertThat(QuemEsta.doRh("Ana Souza")).isFalse();
		assertThat(QuemEsta.emailDoRh("Ana Souza")).isNull();
		assertThat(QuemEsta.doRh(null)).isFalse();
		assertThat(QuemEsta.emailDoRh(null)).isNull();
	}

}
