package br.com.diagnosticolider.campanha;

import java.time.Instant;

import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.questionario.Questionario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * O link que o líder recebe. Sem empresa é o link geral; com empresa, os diagnósticos
 * entram no relatório consolidado daquela empresa.
 */
@Entity
@Table(name = "campanha")
public class Campanha {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String codigo;

	@Column(nullable = false)
	private String nome;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "questionario_id")
	private Questionario questionario;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "empresa_id")
	private Empresa empresa;

	@Column(nullable = false)
	private boolean ativa;

	protected Campanha() {
	}

	/** Link de uma empresa: os diagnósticos feitos por ele entram no consolidado dela. */
	public static Campanha daEmpresa(String codigo, Empresa empresa, Questionario questionario) {
		Campanha campanha = new Campanha();
		campanha.codigo = codigo;
		campanha.nome = empresa.getNome();
		campanha.empresa = empresa;
		campanha.questionario = questionario;
		campanha.ativa = true;
		return campanha;
	}

	public void renomear(String nome) {
		this.nome = nome;
	}

	public boolean vinculadaAEmpresa() {
		return empresa != null;
	}

	/** A avaliação da empresa já encerrou? O link geral não encerra por data. */
	public boolean encerrada(Instant agora) {
		return empresa != null && empresa.encerrada(agora);
	}

	/** Data marcada para o encerramento (null no link geral ou sem data). */
	public Instant encerraEm() {
		return empresa == null ? null : empresa.getEncerraEm();
	}

	public Long getId() {
		return id;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getNome() {
		return nome;
	}

	public Questionario getQuestionario() {
		return questionario;
	}

	public Empresa getEmpresa() {
		return empresa;
	}

	public boolean isAtiva() {
		return ativa;
	}

}
