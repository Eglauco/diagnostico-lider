package br.com.diagnosticolider.notificacao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Um aviso (webhook) do Resend, só com o que usamos:
 * {"type":"email.bounced","created_at":"…","data":{"email_id":"…","bounce":{"type":"Permanent","subType":"General","message":"…"}}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AvisoResend(String type, String created_at, Dados data) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Dados(String email_id, Devolucao bounce, Falha failed) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Devolucao(String type, String subType, String message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Falha(String reason) {
	}

}
