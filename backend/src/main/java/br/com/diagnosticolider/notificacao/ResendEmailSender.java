package br.com.diagnosticolider.notificacao;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Envia pela API HTTP da Resend (https://resend.com). HTTP em vez de SMTP porque o
 * Railway só libera SMTP de saída no plano Pro.
 *
 * <p>Erros passageiros (limite por segundo, Resend instável, rede) são repetidos com a mesma
 * Idempotency-Key: se a primeira tentativa chegou e só a resposta se perdeu, o líder não recebe
 * dois e-mails. Os demais viram {@link FalhaEnvio} com o motivo em português.
 */
class ResendEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

	static final int TENTATIVAS = 3;

	private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(10);

	private final RestClient http;

	private final String remetente;

	private final String responderPara;

	private final Consumer<Duration> esperar;

	ResendEmailSender(RestClient.Builder builder, String apiKey, String remetente, String responderPara) {
		this(builder, apiKey, remetente, responderPara, ResendEmailSender::dormir);
	}

	ResendEmailSender(RestClient.Builder builder, String apiKey, String remetente, String responderPara,
			Consumer<Duration> esperar) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("EMAIL_PROVEDOR=resend exige RESEND_API_KEY.");
		}
		this.http = builder.baseUrl("https://api.resend.com")
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.strip())
			.build();
		this.remetente = remetente;
		this.responderPara = responderPara == null || responderPara.isBlank() ? null : responderPara.strip();
		this.esperar = esperar;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record Requisicao(String from, List<String> to, String subject, String html, List<AnexoResend> attachments,
			@JsonProperty("reply_to") String replyTo) {
	}

	/** content_id: imagem embutida (cid:); sem ele, anexo comum. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	record AnexoResend(String filename, String content, @JsonProperty("content_id") String contentId) {
	}

	/** O corpo de erro da Resend: {"statusCode":429,"name":"daily_quota_exceeded","message":"…"}. */
	record ErroResend(String name, String message) {
	}

	/** O que a Resend devolve no envio: {"id":"49a3999c-…"}. */
	record RespostaResend(String id) {
	}

	@Override
	public String enviar(Mensagem m) {
		var anexos = m.anexos()
			.stream()
			.map(a -> new AnexoResend(a.nome(), Base64.getEncoder().encodeToString(a.conteudo()), a.cid()))
			.toList();
		var corpo = new Requisicao(remetente, m.para(), m.assunto(), m.html(), anexos, responderPara);
		String chave = UUID.randomUUID().toString();
		for (int tentativa = 1;; tentativa++) {
			try {
				RespostaResend resposta = http.post()
					.uri("/emails")
					.header("Idempotency-Key", chave)
					.contentType(MediaType.APPLICATION_JSON)
					.body(corpo)
					.retrieve()
					.body(RespostaResend.class);
				return resposta == null ? null : resposta.id();
			}
			catch (RestClientResponseException e) {
				ErroResend erro = erro(e);
				if (!passageiro(e.getStatusCode().value(), erro) || tentativa == TENTATIVAS) {
					throw traduzir(e.getStatusCode().value(), erro, e);
				}
				Duration espera = espera(e.getResponseHeaders(), tentativa);
				log.warn("Resend respondeu {} ({}); nova tentativa em {} s", e.getStatusCode().value(), erro.name(),
						espera.toSeconds());
				esperar.accept(espera);
			}
			catch (ResourceAccessException e) {
				if (tentativa == TENTATIVAS) {
					throw new FalhaEnvio("Sem conexão com o Resend. Tente enviar de novo em alguns minutos.", true, e);
				}
				log.warn("Sem resposta do Resend ({}); nova tentativa", e.getMessage());
				esperar.accept(Duration.ofSeconds(tentativa));
			}
		}
	}

	/** Vale tentar de novo daqui a pouco: limite por segundo, pedido igual em andamento ou Resend instável. */
	static boolean passageiro(int status, ErroResend erro) {
		return status >= 500 || (status == 429 && "rate_limit_exceeded".equals(erro.name()))
				|| (status == 409 && "concurrent_idempotent_requests".equals(erro.name()));
	}

	static FalhaEnvio traduzir(int status, ErroResend erro, Throwable causa) {
		String nome = erro.name() == null ? "" : erro.name();
		String texto = erro.message() == null ? "" : erro.message();
		if (nome.equals("daily_quota_exceeded")) {
			return new FalhaEnvio("A cota diária de e-mails do Resend acabou. Ela renova às 21h (horário de Brasília): "
					+ "envie os que faltaram depois disso.", true, causa);
		}
		if (nome.equals("monthly_quota_exceeded")) {
			return new FalhaEnvio("A cota mensal de e-mails do Resend acabou. Aumente o plano no Resend para continuar "
					+ "enviando.", true, causa);
		}
		if (status == 429) {
			return new FalhaEnvio("O Resend recebeu envios demais ao mesmo tempo. Tente de novo em instantes.", false,
					causa);
		}
		if (status == 401 || nome.endsWith("api_key") || texto.contains("API key")) {
			return new FalhaEnvio("A chave de API do Resend é inválida ou foi desativada. Confira a RESEND_API_KEY.",
					true, causa);
		}
		if (status == 403 && (texto.contains("not verified") || texto.contains("verify a domain"))) {
			return new FalhaEnvio("O domínio do remetente ainda não foi verificado no Resend.", true, causa);
		}
		if (status >= 500) {
			return new FalhaEnvio("O Resend está fora do ar agora. Tente enviar de novo em alguns minutos.", true, causa);
		}
		if (texto.contains("`to`")) {
			return new FalhaEnvio("O Resend não aceitou o endereço de e-mail do líder. Confira se está escrito certo.",
					false, causa);
		}
		return new FalhaEnvio("O Resend recusou este e-mail" + (texto.isBlank() ? "." : ": " + texto), false, causa);
	}

	private static ErroResend erro(RestClientResponseException e) {
		try {
			ErroResend erro = e.getResponseBodyAs(ErroResend.class);
			if (erro != null) {
				return erro;
			}
		}
		catch (RuntimeException ignorada) {
			// Corpo fora do formato (proxy, HTML): fica só o texto.
		}
		return new ErroResend(null, e.getResponseBodyAsString());
	}

	/** Quanto esperar: o que a Resend pede em retry-after (até 10 s) ou 1 s, 2 s… */
	static Duration espera(HttpHeaders cabecalhos, int tentativa) {
		String pedido = cabecalhos == null ? null : cabecalhos.getFirst("retry-after");
		if (pedido != null) {
			try {
				Duration espera = Duration.ofSeconds(Math.max(1, Long.parseLong(pedido.strip())));
				return espera.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : espera;
			}
			catch (NumberFormatException ignorada) {
				// retry-after também pode vir como data; aí vale a espera padrão.
			}
		}
		return Duration.ofSeconds(tentativa);
	}

	private static void dormir(Duration espera) {
		try {
			Thread.sleep(espera);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new FalhaEnvio("O envio foi interrompido.", true, e);
		}
	}

}
