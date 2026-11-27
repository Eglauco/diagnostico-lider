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

/** O que um gestor fez na página do RH: entrar, abrir, baixar. O painel mostra o último acesso. */
@Entity
@Table(name = "rh_atividade")
class AtividadeRh {

	enum Tipo {
		ENTROU, ABRIU, BAIXOU_PARTICIPACAO_PDF, BAIXOU_PARTICIPACAO_XLSX, BAIXOU_RESULTADO_PDF
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "empresa_id", nullable = false)
	private Long empresaId;

	@Column(nullable = false)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Tipo tipo;

	@Column(nullable = false)
	private Instant quando;

	private String aparelho;

	protected AtividadeRh() {
	}

	AtividadeRh(Long empresaId, String email, Tipo tipo, Instant quando, String aparelho) {
		this.empresaId = empresaId;
		this.email = email;
		this.tipo = tipo;
		this.quando = quando;
		this.aparelho = aparelho;
	}

	String getEmail() {
		return email;
	}

	Tipo getTipo() {
		return tipo;
	}

	Instant getQuando() {
		return quando;
	}

	String getAparelho() {
		return aparelho;
	}

}
