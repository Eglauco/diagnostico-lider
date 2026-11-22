package br.com.diagnosticolider.questionario;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Uma versão do questionário. Depois de publicada é imutável: diagnósticos guardam
 * referência a ela, e é isso que mantém resultados antigos corretos.
 */
@Entity
@Table(name = "questionario_versao")
public class QuestionarioVersao {

	public enum Status {
		RASCUNHO, PUBLICADA, ARQUIVADA
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "questionario_id")
	private Questionario questionario;

	@Column(nullable = false)
	private int numero;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(name = "publicada_em")
	private Instant publicadaEm;

	@OneToMany(mappedBy = "versao")
	@OrderBy("ordem")
	private List<Dimensao> dimensoes = new ArrayList<>();

	@OneToMany(mappedBy = "versao")
	@OrderBy("ordem")
	private List<Pergunta> perguntas = new ArrayList<>();

	@OneToMany(mappedBy = "versao")
	@OrderBy("pontuacaoMinima")
	private List<FaixaResultado> faixas = new ArrayList<>();

	protected QuestionarioVersao() {
	}

	public Optional<Pergunta> pergunta(Long perguntaId) {
		return perguntas.stream().filter(p -> p.getId().equals(perguntaId)).findFirst();
	}

	public FaixaResultado faixaPara(int pontuacao) {
		return faixas.stream()
			.filter(f -> f.contem(pontuacao))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
					"Versão %d sem faixa para a pontuação %d".formatted(id, pontuacao)));
	}

	public Long getId() {
		return id;
	}

	public Questionario getQuestionario() {
		return questionario;
	}

	public int getNumero() {
		return numero;
	}

	public Status getStatus() {
		return status;
	}

	public List<Dimensao> getDimensoes() {
		return dimensoes;
	}

	public List<Pergunta> getPerguntas() {
		return perguntas;
	}

	public List<FaixaResultado> getFaixas() {
		return faixas;
	}

}
