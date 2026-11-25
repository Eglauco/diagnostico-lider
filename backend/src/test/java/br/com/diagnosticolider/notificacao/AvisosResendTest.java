package br.com.diagnosticolider.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.notificacao.EmailEnviado.Situacao;

/** Os avisos do Resend: a assinatura, a tradução para o painel e a ordem em que chegam. */
class AvisosResendTest {

	private static final String SEGREDO = "whsec_" + Base64.getEncoder().encodeToString("segredo-de-teste-32-bytes!!!!!!!".getBytes());

	private static final Instant AGORA = Instant.parse("2026-11-24T23:00:00Z");

	private static final String TS = String.valueOf(AGORA.getEpochSecond());

	private static final String CORPO = "{\"type\":\"email.delivered\",\"data\":{\"email_id\":\"abc\"}}";

	@Test
	void aceitaSoOAvisoAssinadoComOSegredo() {
		String assinatura = AssinaturaSvix.assinar(SEGREDO, "msg_1", TS, CORPO);
		assertThat(AssinaturaSvix.valida(SEGREDO, "msg_1", TS, assinatura, CORPO, AGORA)).isTrue();
		// Várias assinaturas no cabeçalho (troca de segredo): basta uma conferir.
		assertThat(AssinaturaSvix.valida(SEGREDO, "msg_1", TS, "v1,b3V0cmE= " + assinatura, CORPO, AGORA)).isTrue();
		// Corpo, id ou segredo diferentes: recusa.
		assertThat(AssinaturaSvix.valida(SEGREDO, "msg_1", TS, assinatura, CORPO.replace("abc", "xyz"), AGORA)).isFalse();
		assertThat(AssinaturaSvix.valida(SEGREDO, "msg_2", TS, assinatura, CORPO, AGORA)).isFalse();
		String outro = "whsec_" + Base64.getEncoder().encodeToString("outro-segredo-outro-segredo-1234".getBytes());
		assertThat(AssinaturaSvix.valida(outro, "msg_1", TS, assinatura, CORPO, AGORA)).isFalse();
		// Sem segredo configurado ou cabeçalho faltando: recusa.
		assertThat(AssinaturaSvix.valida("", "msg_1", TS, assinatura, CORPO, AGORA)).isFalse();
		assertThat(AssinaturaSvix.valida(SEGREDO, "msg_1", TS, null, CORPO, AGORA)).isFalse();
		assertThat(AssinaturaSvix.valida(SEGREDO, "msg_1", TS, "v1,isso-nao-e-base64", CORPO, AGORA)).isFalse();
	}

	@Test
	void avisoAntigoNaoVale() {
		String antigo = String.valueOf(AGORA.minusSeconds(6 * 60).getEpochSecond());
		String assinatura = AssinaturaSvix.assinar(SEGREDO, "msg_1", antigo, CORPO);
		assertThat(AssinaturaSvix.valida(SEGREDO, "msg_1", antigo, assinatura, CORPO, AGORA)).isFalse();
	}

	@Test
	void traduzOsAvisosParaQuemVaiAgir() {
		assertThat(WebhookResendService.situacao("email.delivered")).isEqualTo(Situacao.ENTREGUE);
		assertThat(WebhookResendService.situacao("email.bounced")).isEqualTo(Situacao.DEVOLVIDO);
		assertThat(WebhookResendService.situacao("email.complained")).isEqualTo(Situacao.SPAM);
		assertThat(WebhookResendService.situacao("email.opened")).isNull();
		assertThat(WebhookResendService.situacao("email.clicked")).isNull();

		assertThat(WebhookResendService.devolucao(new AvisoResend.Devolucao("Permanent", "General", "550 no such user")))
			.isEqualTo("O endereço não existe ou o servidor recusou. Confira o e-mail com o RH.");
		assertThat(WebhookResendService.devolucao(new AvisoResend.Devolucao("Permanent", "MessageRejected", null)))
			.contains("Peça à TI para liberar o remetente");
		assertThat(WebhookResendService.devolucao(new AvisoResend.Devolucao("Transient", "MailboxFull", null)))
			.contains("caixa de entrada do líder está cheia");
		assertThat(WebhookResendService.devolucao(null)).startsWith("O e-mail voltou");
	}

	@Test
	void entregueNaoApagaDevolucaoNemSpam() {
		var email = new EmailEnviado("abc", EmailEnviado.Tipo.CONVITE, 1L, 2L, null, "ana@empresa.com.br", "Convite",
				AGORA);
		assertThat(email.atualizar(Situacao.ATRASADO, AGORA.plusSeconds(10), "atrasou")).isTrue();
		assertThat(email.atualizar(Situacao.ENTREGUE, AGORA.plusSeconds(20), null)).isTrue();
		assertThat(email.getEntregueEm()).isEqualTo(AGORA.plusSeconds(20));
		assertThat(email.atualizar(Situacao.ATRASADO, AGORA.plusSeconds(30), "atrasou")).isFalse();
		// Spam chega depois da entrega e vale.
		assertThat(email.atualizar(Situacao.SPAM, AGORA.plusSeconds(40), "spam")).isTrue();
		assertThat(email.atualizar(Situacao.ENTREGUE, AGORA.plusSeconds(50), null)).isFalse();
		assertThat(email.getSituacao()).isEqualTo(Situacao.SPAM);

		var devolvido = new EmailEnviado("def", EmailEnviado.Tipo.CONVITE, 1L, 2L, null, "x@y.com", "Convite", AGORA);
		assertThat(devolvido.atualizar(Situacao.DEVOLVIDO, AGORA.plusSeconds(5), "voltou")).isTrue();
		assertThat(devolvido.atualizar(Situacao.ENTREGUE, AGORA.plusSeconds(6), null)).isFalse();
		assertThat(devolvido.getSituacao()).isEqualTo(Situacao.DEVOLVIDO);
	}

}
