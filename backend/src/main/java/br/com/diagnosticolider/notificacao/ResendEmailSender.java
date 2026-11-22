package br.com.diagnosticolider.notificacao;

import java.util.Base64;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Envia pela API HTTP da Resend (https://resend.com). HTTP em vez de SMTP porque o
 * Railway só libera SMTP de saída no plano Pro.
 */
class ResendEmailSender implements EmailSender {

	private final RestClient http;

	private final String remetente;

	ResendEmailSender(RestClient.Builder builder, String apiKey, String remetente) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("EMAIL_PROVEDOR=resend exige RESEND_API_KEY.");
		}
		this.http = builder.baseUrl("https://api.resend.com").defaultHeader("Authorization", "Bearer " + apiKey).build();
		this.remetente = remetente;
	}

	record Requisicao(String from, List<String> to, String subject, String html, List<AnexoResend> attachments) {
	}

	record AnexoResend(String filename, String content) {
	}

	@Override
	public void enviar(Mensagem m) {
		var anexos = m.anexos()
			.stream()
			.map(a -> new AnexoResend(a.nome(), Base64.getEncoder().encodeToString(a.conteudo())))
			.toList();
		http.post()
			.uri("/emails")
			.contentType(MediaType.APPLICATION_JSON)
			.body(new Requisicao(remetente, List.of(m.para()), m.assunto(), m.html(), anexos))
			.retrieve()
			.toBodilessEntity();
	}

}
