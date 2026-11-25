package br.com.diagnosticolider.notificacao;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import br.com.diagnosticolider.config.AppProperties;

/**
 * Onde o Resend avisa o que aconteceu com cada e-mail. Público (o Resend não tem sessão), mas só
 * aceita aviso assinado com o segredo do webhook (RESEND_WEBHOOK_SECRET). Sem o segredo, recusa tudo.
 */
@RestController
class WebhookResendController {

	private static final Logger log = LoggerFactory.getLogger(WebhookResendController.class);

	private final WebhookResendService service;

	private final String segredo;

	private final Clock clock;

	WebhookResendController(WebhookResendService service, AppProperties propriedades, Clock clock) {
		this.service = service;
		this.segredo = propriedades.email().resendWebhookSecret();
		this.clock = clock;
	}

	@PostMapping("/api/public/webhooks/resend")
	ResponseEntity<Void> receber(@RequestHeader(name = "svix-id", required = false) String id,
			@RequestHeader(name = "svix-timestamp", required = false) String timestamp,
			@RequestHeader(name = "svix-signature", required = false) String assinatura, @RequestBody String corpo) {
		if (segredo == null || segredo.isBlank()) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}
		if (!AssinaturaSvix.valida(segredo, id, timestamp, assinatura, corpo, clock.instant())) {
			log.warn("Aviso do Resend com assinatura inválida (id {})", id);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		service.processar(id, corpo);
		return ResponseEntity.ok().build();
	}

}
