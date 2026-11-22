package br.com.diagnosticolider.diagnostico;

import br.com.diagnosticolider.questionario.Dimensao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Pontuação 0–100 de uma dimensão, gravada quando o diagnóstico é concluído. */
@Entity
@Table(name = "diagnostico_dimensao")
public class DiagnosticoDimensao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "diagnostico_id")
	private Diagnostico diagnostico;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "dimensao_id")
	private Dimensao dimensao;

	@Column(nullable = false)
	private int pontuacao;

	protected DiagnosticoDimensao() {
	}

	DiagnosticoDimensao(Diagnostico diagnostico, Dimensao dimensao, int pontuacao) {
		this.diagnostico = diagnostico;
		this.dimensao = dimensao;
		this.pontuacao = pontuacao;
	}

	public Dimensao getDimensao() {
		return dimensao;
	}

	public int getPontuacao() {
		return pontuacao;
	}

}
