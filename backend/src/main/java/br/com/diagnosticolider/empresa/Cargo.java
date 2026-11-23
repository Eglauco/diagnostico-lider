package br.com.diagnosticolider.empresa;

import java.time.Instant;

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
 * Um cargo de liderança da empresa. A ordem é a hierarquia (0 = primeiro nível): o relatório lê
 * os cargos como uma escada. Arquivado, sai da lista do líder mas continua nos relatórios.
 */
@Entity
@Table(name = "cargo")
public class Cargo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "empresa_id")
	private Empresa empresa;

	@Column(nullable = false, length = 80)
	private String nome;

	@Column(nullable = false)
	private int ordem;

	@Column(nullable = false)
	private boolean ativo = true;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm = Instant.now();

	protected Cargo() {
	}

	Cargo(Empresa empresa, String nome, int ordem) {
		this.empresa = empresa;
		this.nome = nome;
		this.ordem = ordem;
	}

	/** Renomeia, reposiciona e (se estava arquivado) devolve o cargo à lista. */
	void atualizar(String nome, int ordem) {
		this.nome = nome;
		this.ordem = ordem;
		this.ativo = true;
	}

	void arquivar() {
		this.ativo = false;
	}

	public Long getId() {
		return id;
	}

	public Empresa getEmpresa() {
		return empresa;
	}

	public String getNome() {
		return nome;
	}

	public int getOrdem() {
		return ordem;
	}

	public boolean isAtivo() {
		return ativo;
	}

}
