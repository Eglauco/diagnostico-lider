package br.com.diagnosticolider.rh;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Um jeito de entrar uma vez: o código de 6 dígitos (com o botão do mesmo e-mail) ou o convite
 * mandado pelo administrador (só o botão). Só os hashes ficam no banco.
 */
@Entity
@Table(name = "rh_codigo")
class CodigoRh {

	enum Tipo {
		CODIGO, CONVITE
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Tipo tipo;

	@Column(name = "codigo_hash")
	private String codigoHash;

	@Column(name = "link_hash", nullable = false)
	private String linkHash;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;

	@Column(name = "expira_em", nullable = false)
	private Instant expiraEm;

	@Column(nullable = false)
	private int tentativas;

	@Column(name = "usado_em")
	private Instant usadoEm;

	protected CodigoRh() {
	}

	CodigoRh(String email, Tipo tipo, String codigoHash, String linkHash, Instant criadoEm, Instant expiraEm) {
		this.email = email;
		this.tipo = tipo;
		this.codigoHash = codigoHash;
		this.linkHash = linkHash;
		this.criadoEm = criadoEm;
		this.expiraEm = expiraEm;
	}

	/** Ainda serve: não foi usado, não venceu e não passou do limite de tentativas. */
	boolean valido(Instant agora, int maximoTentativas) {
		return usadoEm == null && agora.isBefore(expiraEm) && tentativas < maximoTentativas;
	}

	void errou() {
		tentativas++;
	}

	void usar(Instant agora) {
		usadoEm = agora;
	}

	String getEmail() {
		return email;
	}

	Tipo getTipo() {
		return tipo;
	}

	String getCodigoHash() {
		return codigoHash;
	}

	int getTentativas() {
		return tentativas;
	}

	Instant getCriadoEm() {
		return criadoEm;
	}

	Instant getUsadoEm() {
		return usadoEm;
	}

}
