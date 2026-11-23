package br.com.diagnosticolider.questionario;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ListIndexBase;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** Faixa de pontuação (0–100) com o diagnóstico e as recomendações correspondentes. */
@Entity
@Table(name = "faixa_resultado")
public class FaixaResultado {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "versao_id")
	private QuestionarioVersao versao;

	@Column(nullable = false)
	private String codigo;

	@Column(nullable = false)
	private String titulo;

	@Column(nullable = false)
	private String resumo;

	@Column(nullable = false)
	private String descricao;

	@Column(name = "pontuacao_minima", nullable = false)
	private int pontuacaoMinima;

	@Column(name = "pontuacao_maxima", nullable = false)
	private int pontuacaoMaxima;

	@ElementCollection
	@CollectionTable(name = "faixa_recomendacao", joinColumns = @JoinColumn(name = "faixa_id"))
	@OrderColumn(name = "ordem", nullable = false)
	@ListIndexBase(1)
	@Column(name = "texto", nullable = false)
	private List<String> recomendacoes = new ArrayList<>();

	/** Como a faixa é descrita para a empresa (o resumo e a descrição falam com o líder). */
	@Column(name = "leitura_empresa")
	private String leituraEmpresa;

	/** O que a empresa pode fazer pelos líderes desta faixa (relatório consolidado). */
	@ElementCollection
	@CollectionTable(name = "faixa_acao_empresa", joinColumns = @JoinColumn(name = "faixa_id"))
	@OrderColumn(name = "ordem", nullable = false)
	@ListIndexBase(1)
	@Column(name = "texto", nullable = false)
	private List<String> acoesEmpresa = new ArrayList<>();

	protected FaixaResultado() {
	}

	public String getLeituraEmpresa() {
		return leituraEmpresa;
	}

	public List<String> getAcoesEmpresa() {
		return acoesEmpresa;
	}

	public boolean contem(int pontuacao) {
		return pontuacao >= pontuacaoMinima && pontuacao <= pontuacaoMaxima;
	}

	public Long getId() {
		return id;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getTitulo() {
		return titulo;
	}

	public String getResumo() {
		return resumo;
	}

	public String getDescricao() {
		return descricao;
	}

	public int getPontuacaoMinima() {
		return pontuacaoMinima;
	}

	public int getPontuacaoMaxima() {
		return pontuacaoMaxima;
	}

	public List<String> getRecomendacoes() {
		return recomendacoes;
	}

}
