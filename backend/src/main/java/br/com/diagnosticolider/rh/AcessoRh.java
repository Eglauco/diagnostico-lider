package br.com.diagnosticolider.rh;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um aparelho lembrado: vale 30 dias desde o último uso. Só o hash do token fica no banco. */
@Entity
@Table(name = "rh_acesso")
class AcessoRh {

	private static final int MAXIMO_APARELHO = 120;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String email;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;

	@Column(name = "usado_em", nullable = false)
	private Instant usadoEm;

	@Column(name = "expira_em", nullable = false)
	private Instant expiraEm;

	@Column(name = "encerrado_em")
	private Instant encerradoEm;

	private String aparelho;

	protected AcessoRh() {
	}

	AcessoRh(String email, String tokenHash, Instant agora, Duration validade, String aparelho) {
		this.email = email;
		this.tokenHash = tokenHash;
		this.criadoEm = agora;
		this.usadoEm = agora;
		this.expiraEm = agora.plus(validade);
		this.aparelho = aparelho == null ? null : aparelho.substring(0, Math.min(aparelho.length(), MAXIMO_APARELHO));
	}

	boolean valido(Instant agora) {
		return encerradoEm == null && agora.isBefore(expiraEm);
	}

	/** Usar estende o prazo. Devolve true quando estendeu (aí o cookie também é renovado). */
	boolean usar(Instant agora, Duration validade, Duration intervalo) {
		if (Duration.between(usadoEm, agora).compareTo(intervalo) < 0) {
			return false;
		}
		usadoEm = agora;
		expiraEm = agora.plus(validade);
		return true;
	}

	void encerrar(Instant agora) {
		encerradoEm = agora;
	}

	Long getId() {
		return id;
	}

	String getEmail() {
		return email;
	}

	String getAparelho() {
		return aparelho;
	}

}
