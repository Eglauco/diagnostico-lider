package br.com.diagnosticolider.acompanhamento;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

/**
 * Uma foto do resultado da empresa: quais diagnósticos entraram e a média geral daquele momento.
 * Tudo o que a empresa vê do resultado sai da última foto (ver {@link RegraDaFoto}).
 */
@Entity
@Table(name = "resultado_foto")
class FotoResultado {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "empresa_id", nullable = false)
	private Long empresaId;

	@Column(name = "tirada_em", nullable = false)
	private Instant tiradaEm;

	/** A avaliação tinha chegado ao fim: o resultado mostra também cargos e perguntas. */
	@Column(name = "final", nullable = false)
	private boolean fim;

	@Column(name = "referencia_media")
	private Double referenciaMedia;

	@Column(name = "referencia_lideres")
	private Integer referenciaLideres;

	@ElementCollection
	@CollectionTable(name = "resultado_foto_diagnostico", joinColumns = @JoinColumn(name = "foto_id"))
	@Column(name = "diagnostico_id", nullable = false)
	private Set<UUID> diagnosticos = new HashSet<>();

	/** Média geral de cada dimensão na hora da foto (código → média). */
	@ElementCollection
	@CollectionTable(name = "resultado_foto_dimensao", joinColumns = @JoinColumn(name = "foto_id"))
	@MapKeyColumn(name = "codigo")
	@Column(name = "media", nullable = false)
	private Map<String, Double> referenciaDimensoes = new HashMap<>();

	protected FotoResultado() {
	}

	FotoResultado(Long empresaId, Instant tiradaEm, boolean fim, Set<UUID> diagnosticos, Double referenciaMedia,
			Integer referenciaLideres, Map<String, Double> referenciaDimensoes) {
		this.empresaId = empresaId;
		this.tiradaEm = tiradaEm;
		this.fim = fim;
		this.diagnosticos.addAll(diagnosticos);
		this.referenciaMedia = referenciaMedia;
		this.referenciaLideres = referenciaLideres;
		this.referenciaDimensoes.putAll(referenciaDimensoes);
	}

	/** A mesma base, agora (ou não mais) como resultado final: só muda o que aparece. */
	FotoResultado comFim(boolean fim, Instant agora) {
		return new FotoResultado(empresaId, agora, fim, diagnosticos, referenciaMedia, referenciaLideres,
				referenciaDimensoes);
	}

	Long getId() {
		return id;
	}

	Instant getTiradaEm() {
		return tiradaEm;
	}

	boolean isFim() {
		return fim;
	}

	Set<UUID> getDiagnosticos() {
		return Set.copyOf(diagnosticos);
	}

	Double getReferenciaMedia() {
		return referenciaMedia;
	}

	Integer getReferenciaLideres() {
		return referenciaLideres;
	}

	Map<String, Double> getReferenciaDimensoes() {
		return Map.copyOf(referenciaDimensoes);
	}

}
