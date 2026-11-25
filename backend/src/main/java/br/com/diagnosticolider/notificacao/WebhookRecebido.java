package br.com.diagnosticolider.notificacao;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Aviso do Resend já processado (o svix-id): o Resend repete o aviso se não recebe 200 a tempo. */
@Entity
@Table(name = "webhook_recebido")
class WebhookRecebido {

	@Id
	private String id;

	@Column(name = "recebido_em", nullable = false)
	private Instant recebidoEm;

	protected WebhookRecebido() {
	}

	WebhookRecebido(String id, Instant recebidoEm) {
		this.id = id;
		this.recebidoEm = recebidoEm;
	}

}
