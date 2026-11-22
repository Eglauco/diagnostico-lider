package br.com.diagnosticolider.notificacao;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Garante o contrato com a API da Resend sem enviar e-mail de verdade. */
class ResendEmailSenderTest {

	@Test
	void enviaJsonComAnexoEmBase64() {
		var builder = RestClient.builder();
		var resend = MockRestServiceServer.bindTo(builder).build();
		resend.expect(requestTo("https://api.resend.com/emails"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer chave-teste"))
			.andExpect(jsonPath("$.from").value("Diagnóstico <oi@exemplo.com>"))
			.andExpect(jsonPath("$.to[0]").value("ana@exemplo.com"))
			.andExpect(jsonPath("$.attachments[0].filename").value("diagnostico.pdf"))
			.andExpect(jsonPath("$.attachments[0].content").value("JVBERg=="))
			.andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

		new ResendEmailSender(builder, "chave-teste", "Diagnóstico <oi@exemplo.com>")
			.enviar(new EmailSender.Mensagem("ana@exemplo.com", "Assunto", "<p>Oi</p>",
					List.of(new EmailSender.Anexo("diagnostico.pdf", new byte[] { 37, 80, 68, 70 }))));

		resend.verify();
	}

}
