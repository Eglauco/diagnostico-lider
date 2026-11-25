package br.com.diagnosticolider.acompanhamento;

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
 * Um envio automático (ou pelo "Enviar agora") de uma empresa: relatório ao gestor ou lote de
 * lembretes aos líderes. É o histórico do painel e a base do "+3 desde o último relatório".
 */
@Entity
@Table(name = "envio_acompanhamento")
class EnvioAcompanhamento {

	private static final int MAXIMO_DETALHE = 500;

	private static final int MAXIMO_PARA = 2000;

	enum Tipo {
		DIARIO, FINAL, LEMBRETE
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "empresa_id", nullable = false)
	private Long empresaId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Tipo tipo;

	@Column(nullable = false)
	private boolean manual;

	@Column(nullable = false)
	private Instant quando;

	/** E-mails dos gestores, ou "12 líderes" no lembrete. */
	@Column(nullable = false)
	private String para;

	private Integer responderam;

	private Integer total;

	@Column(nullable = false)
	private boolean enviado;

	private String detalhe;

	/** Quem clicou em "Enviar agora" (null: o envio automático). */
	private String autor;

	@Column(name = "email_id")
	private Long emailId;

	protected EnvioAcompanhamento() {
	}

	EnvioAcompanhamento(Long empresaId, Tipo tipo, boolean manual, Instant quando, String para, Integer responderam,
			Integer total, boolean enviado, String detalhe) {
		this(empresaId, tipo, manual, quando, para, responderam, total, enviado, detalhe, null, null);
	}

	EnvioAcompanhamento(Long empresaId, Tipo tipo, boolean manual, Instant quando, String para, Integer responderam,
			Integer total, boolean enviado, String detalhe, String autor, Long emailId) {
		this.autor = autor;
		this.emailId = emailId;
		this.empresaId = empresaId;
		this.tipo = tipo;
		this.manual = manual;
		this.quando = quando;
		this.para = para.length() <= MAXIMO_PARA ? para : para.substring(0, MAXIMO_PARA - 1) + "…";
		this.responderam = responderam;
		this.total = total;
		this.enviado = enviado;
		this.detalhe = detalhe == null || detalhe.length() <= MAXIMO_DETALHE ? detalhe
				: detalhe.substring(0, MAXIMO_DETALHE - 1) + "…";
	}

	Long getId() {
		return id;
	}

	Tipo getTipo() {
		return tipo;
	}

	boolean isManual() {
		return manual;
	}

	Instant getQuando() {
		return quando;
	}

	String getPara() {
		return para;
	}

	Integer getResponderam() {
		return responderam;
	}

	Integer getTotal() {
		return total;
	}

	boolean isEnviado() {
		return enviado;
	}

	String getDetalhe() {
		return detalhe;
	}

	String getAutor() {
		return autor;
	}

	Long getEmailId() {
		return emailId;
	}

}
