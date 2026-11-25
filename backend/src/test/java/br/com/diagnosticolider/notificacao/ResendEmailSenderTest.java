package br.com.diagnosticolider.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.diagnosticolider.notificacao.EmailSender.FalhaEnvio;

/** Garante o contrato com a API da Resend sem enviar e-mail de verdade. */
class ResendEmailSenderTest {

	private static final EmailSender.Mensagem MENSAGEM = new EmailSender.Mensagem("ana@exemplo.com", "Assunto",
			"<p>Oi</p>", List.of());

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer resend = MockRestServiceServer.bindTo(builder).build();

	private final List<Duration> esperas = new ArrayList<>();

	private ResendEmailSender sender(String responderPara) {
		return new ResendEmailSender(builder, "chave-teste", "Diagnóstico <oi@exemplo.com>", responderPara,
				esperas::add);
	}

	private static String erro(int status, String nome, String mensagem) {
		return "{\"statusCode\":%d,\"name\":\"%s\",\"message\":\"%s\"}".formatted(status, nome, mensagem);
	}

	@Test
	void enviaJsonComAnexoEmBase64() {
		resend.expect(requestTo("https://api.resend.com/emails"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer chave-teste"))
			.andExpect(jsonPath("$.from").value("Diagnóstico <oi@exemplo.com>"))
			.andExpect(jsonPath("$.to[0]").value("ana@exemplo.com"))
			.andExpect(jsonPath("$.attachments[0].filename").value("diagnostico.pdf"))
			.andExpect(jsonPath("$.attachments[0].content").value("JVBERg=="))
			.andExpect(jsonPath("$.attachments[0].content_id").doesNotExist())
			.andExpect(jsonPath("$.reply_to").doesNotExist())
			.andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

		sender(" ").enviar(new EmailSender.Mensagem("ana@exemplo.com", "Assunto", "<p>Oi</p>",
				List.of(new EmailSender.Anexo("diagnostico.pdf", new byte[] { 37, 80, 68, 70 }))));

		resend.verify();
	}

	@Test
	void logoEmbutidaVaiComContentId() {
		resend.expect(jsonPath("$.attachments[0].filename").value("logo.png"))
			.andExpect(jsonPath("$.attachments[0].content_id").value("logo-7-v3"))
			.andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

		sender(null).enviar(new EmailSender.Mensagem("ana@exemplo.com", "Assunto", "<img src=\"cid:logo-7-v3\">",
				List.of(new EmailSender.Anexo("logo.png", new byte[] { 1 }, "logo-7-v3"))));

		resend.verify();
	}

	@Test
	void respostasVaoParaOEnderecoConfigurado() {
		resend.expect(jsonPath("$.reply_to").value("contato@exemplo.com"))
			.andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

		sender("contato@exemplo.com").enviar(MENSAGEM);

		resend.verify();
	}

	@Test
	void limitePorSegundoEsperaERepeteComAMesmaChaveDeIdempotencia() {
		Set<String> chaves = new HashSet<>();
		var cabecalhos = new HttpHeaders();
		cabecalhos.add("retry-after", "2");
		resend.expect(times(2), requestTo("https://api.resend.com/emails")).andExpect(requisicao -> {
			chaves.add(requisicao.getHeaders().getFirst("Idempotency-Key"));
		}).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(cabecalhos)
			.contentType(MediaType.APPLICATION_JSON)
			.body(erro(429, "rate_limit_exceeded", "Too many requests.")));
		resend.expect(requestTo("https://api.resend.com/emails"))
			.andExpect(requisicao -> chaves.add(requisicao.getHeaders().getFirst("Idempotency-Key")))
			.andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

		assertThat(sender(null).enviar(MENSAGEM)).isEqualTo("abc");

		resend.verify();
		assertThat(chaves).hasSize(1).doesNotContainNull();
		assertThat(esperas).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(2));
	}

	@Test
	void resendInstavelDesisteDepoisDeTresTentativas() {
		resend.expect(times(ResendEmailSender.TENTATIVAS), requestTo("https://api.resend.com/emails"))
			.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
				.body(erro(503, "service_unavailable", "API is temporarily unavailable")));

		assertThatThrownBy(() -> sender(null).enviar(MENSAGEM)).isInstanceOfSatisfying(FalhaEnvio.class, f -> {
			assertThat(f).hasMessageContaining("fora do ar");
			assertThat(f.barraOsProximos()).isTrue();
		});
		resend.verify();
		assertThat(esperas).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
	}

	@Test
	void cotaDiariaParaOLoteSemRepetir() {
		resend.expect(times(1), requestTo("https://api.resend.com/emails"))
			.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
				.body(erro(429, "daily_quota_exceeded", "You have exceeded your daily email sending quota.")));

		assertThatThrownBy(() -> sender(null).enviar(MENSAGEM)).isInstanceOfSatisfying(FalhaEnvio.class, f -> {
			assertThat(f).hasMessageStartingWith("A cota diária de e-mails do Resend acabou");
			assertThat(f.barraOsProximos()).isTrue();
		});
		resend.verify();
		assertThat(esperas).isEmpty();
	}

	@Test
	void dominioNaoVerificadoParaOLote() {
		resend.expect(requestTo("https://api.resend.com/emails"))
			.andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
				.body(erro(403, "validation_error",
						"The exemplo.com domain is not verified. Please, add and verify your domain.")));

		assertThatThrownBy(() -> sender(null).enviar(MENSAGEM)).isInstanceOfSatisfying(FalhaEnvio.class, f -> {
			assertThat(f).hasMessage("O domínio do remetente ainda não foi verificado no Resend.");
			assertThat(f.barraOsProximos()).isTrue();
		});
	}

	@Test
	void enderecoRecusadoSoAfetaAqueleLider() {
		resend.expect(requestTo("https://api.resend.com/emails"))
			.andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON)
				.body(erro(422, "validation_error",
						"Invalid `to` field. The email address needs to follow the `email@example.com` format.")));

		assertThatThrownBy(() -> sender(null).enviar(MENSAGEM)).isInstanceOfSatisfying(FalhaEnvio.class, f -> {
			assertThat(f).hasMessageContaining("endereço de e-mail do líder");
			assertThat(f.barraOsProximos()).isFalse();
		});
	}

}
