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

@Entity
@Table(name = "opcao")
public class Opcao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pergunta_id")
	private Pergunta pergunta;

	@Column(nullable = false)
	private String texto;

	/** Peso da opção no cálculo. Nunca é exposto ao front. */
	@Column(nullable = false)
	private int pontos;

	@Column(nullable = false)
	private int ordem;

	protected Opcao() {
	}

	public Long getId() {
		return id;
	}

	public Pergunta getPergunta() {
		return pergunta;
	}

	public String getTexto() {
		return texto;
	}

	public int getPontos() {
		return pontos;
	}

	public int getOrdem() {
		return ordem;
	}

}
