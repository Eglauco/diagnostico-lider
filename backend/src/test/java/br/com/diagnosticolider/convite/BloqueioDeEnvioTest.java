package br.com.diagnosticolider.convite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.convite.BloqueioDeEnvio.Motivo;
import br.com.diagnosticolider.notificacao.EmailEnviado.Situacao;

/** Não insistir no endereço que voltou ou marcou spam: é isso que protege a reputação do domínio. */
class BloqueioDeEnvioTest {

	@Test
	void spamDevolucaoEBloqueioNoMesmoEnderecoBarramOEnvio() {
		assertThat(BloqueioDeEnvio.bloqueio("ana@empresa.com", "ana@empresa.com", Situacao.SPAM))
			.hasValueSatisfying(b -> {
				assertThat(b.motivo()).isEqualTo(Motivo.SPAM);
				assertThat(b.texto()).contains("WhatsApp");
			});
		assertThat(BloqueioDeEnvio.bloqueio("ana@empresa.com", "ANA@empresa.com ", Situacao.DEVOLVIDO))
			.hasValueSatisfying(b -> {
				assertThat(b.motivo()).isEqualTo(Motivo.DEVOLVIDO);
				assertThat(b.texto()).contains("Corrija o endereço");
			});
		assertThat(BloqueioDeEnvio.bloqueio("ana@empresa.com", "ana@empresa.com", Situacao.BLOQUEADO))
			.hasValueSatisfying(b -> assertThat(b.motivo()).isEqualTo(Motivo.BLOQUEADO));
	}

	@Test
	void enderecoCorrigidoLiberaOEnvio() {
		assertThat(BloqueioDeEnvio.bloqueio("ana.souza@empresa.com", "ana@empresa.com", Situacao.DEVOLVIDO)).isEmpty();
		assertThat(BloqueioDeEnvio.bloqueio("ana.souza@empresa.com", "ana@empresa.com", Situacao.SPAM)).isEmpty();
	}

	@Test
	void entregueAtrasadoOuFalhaDoProvedorPodemSairDeNovo() {
		for (Situacao s : new Situacao[] { Situacao.ENVIADO, Situacao.ENTREGUE, Situacao.ATRASADO, Situacao.FALHOU }) {
			assertThat(BloqueioDeEnvio.bloqueio("ana@empresa.com", "ana@empresa.com", s)).as(s.name()).isEmpty();
		}
	}

	@Test
	void visitasNaoRepetemOAparelhoENaoEsquecemOPrimeiro() {
		String segunda = ConviteService.visitas(2, "25 de novembro, às 0h06", "celular", "Pelo celular.");
		assertThat(segunda).isEqualTo("Abriu 2 vezes, sempre pelo celular; a última em 25 de novembro, às 0h06.");
		String terceira = ConviteService.visitas(3, "25 de novembro, às 9h", "computador", segunda);
		assertThat(terceira)
			.isEqualTo("Abriu 3 vezes; a última em 25 de novembro, às 9h, pelo computador. A primeira foi pelo celular.");
		// Voltou ao celular: não é "sempre pelo celular", porque no meio teve o computador.
		assertThat(ConviteService.visitas(4, "26 de novembro, às 8h", "celular", terceira))
			.isEqualTo("Abriu 4 vezes; a última em 26 de novembro, às 8h, pelo celular. A primeira foi pelo celular.");
		// Texto antigo (antes desta versão) e aparelho desconhecido.
		assertThat(ConviteService.visitas(3, "25 de novembro, às 9h", "celular",
				"Pelo celular. Abriu 2 vezes; a última em 25 de novembro, às 0h06, pelo celular."))
			.isEqualTo("Abriu 3 vezes, sempre pelo celular; a última em 25 de novembro, às 9h.");
		assertThat(ConviteService.visitas(2, "25 de novembro, às 9h", null, null)).isEqualTo("Abriu 2 vezes; a última em 25 de novembro, às 9h.");
	}

}
