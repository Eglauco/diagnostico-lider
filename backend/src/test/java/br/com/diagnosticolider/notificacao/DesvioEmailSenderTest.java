package br.com.diagnosticolider.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.notificacao.EmailSender.Anexo;
import br.com.diagnosticolider.notificacao.EmailSender.Mensagem;

/** Em desenvolvimento, tudo sai de verdade para um endereço só, com o destinatário original no assunto. */
class DesvioEmailSenderTest {

	private final List<Mensagem> enviadas = new ArrayList<>();

	private final EmailSender provedor = mensagem -> {
		enviadas.add(mensagem);
		return "id-resend";
	};

	private final DesvioEmailSender desvio = new DesvioEmailSender(provedor, "eu@gmail.com");

	@Test
	void mandaParaOEnderecoDoDesvioComODestinoNoAssunto() {
		var anexo = new Anexo("diagnostico.pdf", new byte[] { 1, 2 });

		String id = desvio.enviar(new Mensagem("ana@empresa.com.br", "Seu diagnóstico", "<p>Oi</p>", List.of(anexo)));

		assertThat(id).isEqualTo("id-resend");
		assertThat(enviadas).singleElement().satisfies(m -> {
			assertThat(m.para()).containsExactly("eu@gmail.com");
			assertThat(m.assunto()).isEqualTo("[Teste → ana@empresa.com.br] Seu diagnóstico");
			assertThat(m.html()).isEqualTo("<p>Oi</p>");
			assertThat(m.anexos()).containsExactly(anexo);
		});
	}

	@Test
	void variosGestoresViramUmSoNoAssunto() {
		desvio.enviar(new Mensagem(List.of("a@x.com", "b@x.com", "c@x.com"), "Participação", "<p>Oi</p>", List.of()));

		assertThat(enviadas.getFirst().para()).containsExactly("eu@gmail.com");
		assertThat(enviadas.getFirst().assunto()).isEqualTo("[Teste → a@x.com +2] Participação");
	}

	@Test
	void segueOProvedorParaSaberSeOEnvioEstaLigado() {
		assertThat(desvio.ativo()).isTrue();
		assertThat(new DesvioEmailSender(new LogEmailSender(), "eu@gmail.com").ativo()).isFalse();
	}

}
