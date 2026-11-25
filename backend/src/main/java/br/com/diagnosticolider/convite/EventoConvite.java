package br.com.diagnosticolider.convite;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Uma linha da auditoria do convite: o que aconteceu, quando e, se for o caso, o detalhe. */
@Entity
@Table(name = "convite_evento")
public class EventoConvite {

	/** Cabe na coluna (varchar 500). */
	private static final int MAXIMO_DETALHE = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "convite_id")
	private Convite convite;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoEvento tipo;

	@Column(nullable = false)
	private Instant quando;

	private String detalhe;

	/** Quem fez no painel (null: o líder, o sistema ou o envio automático). */
	private String autor;

	/** O e-mail deste evento (convite, lembrete, resultado): a entrega aparece junto. */
	@Column(name = "email_id")
	private Long emailId;

	protected EventoConvite() {
	}

	EventoConvite(Convite convite, TipoEvento tipo, Instant quando, String detalhe) {
		this(convite, tipo, quando, detalhe, null, null);
	}

	EventoConvite(Convite convite, TipoEvento tipo, Instant quando, String detalhe, String autor, Long emailId) {
		this.convite = convite;
		this.tipo = tipo;
		this.quando = quando;
		this.detalhe = cortar(detalhe);
		this.autor = autor;
		this.emailId = emailId;
	}

	private static String cortar(String detalhe) {
		return detalhe == null || detalhe.length() <= MAXIMO_DETALHE ? detalhe
				: detalhe.substring(0, MAXIMO_DETALHE - 1) + "…";
	}

	/** Eventos que se repetem (abriu o link, respondeu, viu o resultado) atualizam a mesma linha. */
	void atualizarDetalhe(String detalhe) {
		this.detalhe = cortar(detalhe);
	}

	public String getAutor() {
		return autor;
	}

	public Long getEmailId() {
		return emailId;
	}

	Convite getConvite() {
		return convite;
	}

	Long conviteId() {
		return convite.getId();
	}

	public TipoEvento getTipo() {
		return tipo;
	}

	public Instant getQuando() {
		return quando;
	}

	public String getDetalhe() {
		return detalhe;
	}

}
