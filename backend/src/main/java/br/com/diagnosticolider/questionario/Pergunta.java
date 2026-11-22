package br.com.diagnosticolider.questionario;

import java.util.ArrayList;
import java.util.List;

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

@Entity
@Table(name = "pergunta")
public class Pergunta {

	public enum Tipo {
		ESCOLHA_UNICA, ESCOLHA_MULTIPLA
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "versao_id")
	private QuestionarioVersao versao;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "dimensao_id")
	private Dimensao dimensao;

	@Column(nullable = false)
	private String codigo;

	@Column(nullable = false)
	private String enunciado;

	private String ajuda;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Tipo tipo;

	@Column(nullable = false)
	private boolean obrigatoria;

	@Column(nullable = false)
	private int ordem;

	@OneToMany(mappedBy = "pergunta")
	@OrderBy("ordem")
	private List<Opcao> opcoes = new ArrayList<>();

	protected Pergunta() {
	}

	/** Menor pontuação possível nesta pergunta, usada para normalizar o resultado em 0–100. */
	public int pontuacaoMinima() {
		return switch (tipo) {
			case ESCOLHA_UNICA -> opcoes.stream().mapToInt(Opcao::getPontos).min().orElse(0);
			case ESCOLHA_MULTIPLA -> opcoes.stream().mapToInt(Opcao::getPontos).filter(p -> p < 0).sum();
		};
	}

	/** Maior pontuação possível nesta pergunta. */
	public int pontuacaoMaxima() {
		return switch (tipo) {
			case ESCOLHA_UNICA -> opcoes.stream().mapToInt(Opcao::getPontos).max().orElse(0);
			case ESCOLHA_MULTIPLA -> opcoes.stream().mapToInt(Opcao::getPontos).filter(p -> p > 0).sum();
		};
	}

	public Long getId() {
		return id;
	}

	public Dimensao getDimensao() {
		return dimensao;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getEnunciado() {
		return enunciado;
	}

	public String getAjuda() {
		return ajuda;
	}

	public Tipo getTipo() {
		return tipo;
	}

	public boolean isObrigatoria() {
		return obrigatoria;
	}

	public int getOrdem() {
		return ordem;
	}

	public List<Opcao> getOpcoes() {
		return opcoes;
	}

}
