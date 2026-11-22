package br.com.diagnosticolider.questionario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Competência avaliada (ex.: Direção). Agrupa perguntas e ganha pontuação própria. */
@Entity
@Table(name = "dimensao")
public class Dimensao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "versao_id")
	private QuestionarioVersao versao;

	@Column(nullable = false)
	private String codigo;

	@Column(nullable = false)
	private String nome;

	private String descricao;

	@Column(nullable = false)
	private int ordem;

	protected Dimensao() {
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

	public String getDescricao() {
		return descricao;
	}

	public int getOrdem() {
		return ordem;
	}

}
