package br.com.diagnosticolider.diagnostico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.compartilhado.RegraNegocioException;

class RespondenteTest {

	@Test
	void normalizaNomeEmailEWhatsapp() {
		var r = Respondente.de("  Ana   Souza ", " Ana.Souza@Empresa.COM ", "(11) 98765-4321", " ACME ");

		assertThat(r.getNome()).isEqualTo("Ana Souza");
		assertThat(r.primeiroNome()).isEqualTo("Ana");
		assertThat(r.getEmail()).isEqualTo("ana.souza@empresa.com");
		assertThat(r.getWhatsapp()).isEqualTo("5511987654321");
		assertThat(r.getEmpresaInformada()).isEqualTo("ACME");
	}

	@Test
	void aceitaWhatsappComDdiETelefoneFixo() {
		assertThat(Respondente.normalizarWhatsapp("+55 11 98765-4321")).isEqualTo("5511987654321");
		assertThat(Respondente.normalizarWhatsapp("(11) 3456-7890")).isEqualTo("551134567890");
	}

	@Test
	void recusaWhatsappSemDdd() {
		assertThatThrownBy(() -> Respondente.normalizarWhatsapp("98765-4321"))
			.isInstanceOf(RegraNegocioException.class);
	}

	@Test
	void mascaraEmailParaExibicao() {
		assertThat(Respondente.de("Ana", "ana.souza@empresa.com", "11987654321", null).emailMascarado())
			.isEqualTo("an•••@empresa.com");
	}

}
