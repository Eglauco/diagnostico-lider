package br.com.diagnosticolider.diagnostico;

import java.time.Instant;

import br.com.diagnosticolider.questionario.Opcao;
import br.com.diagnosticolider.questionario.Pergunta;
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
@Table(name = "resposta")
public class Resposta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "diagnostico_id")
	private Diagnostico diagnostico;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pergunta_id")
	private Pergunta pergunta;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "opcao_id")
	private Opcao opcao;

	@Column(name = "respondida_em", nullable = false)
	private Instant respondidaEm;

	protected Resposta() {
	}

	Resposta(Diagnostico diagnostico, Pergunta pergunta, Opcao opcao, Instant respondidaEm) {
		this.diagnostico = diagnostico;
		this.pergunta = pergunta;
		this.opcao = opcao;
		this.respondidaEm = respondidaEm;
	}

	public Pergunta getPergunta() {
		return pergunta;
	}

	public Opcao getOpcao() {
		return opcao;
	}

}
