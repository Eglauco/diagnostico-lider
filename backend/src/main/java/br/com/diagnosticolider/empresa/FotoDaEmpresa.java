package br.com.diagnosticolider.empresa;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Uma foto da empresa na página do RH (a da entrada ou a do topo): a versão do arquivo no bucket
 * (null = sem foto; muda a cada envio, então o endereço também muda), o tamanho e o ponto de foco
 * (0 a 100, da esquerda e do topo), que fica sempre à vista quando a foto é cortada.
 */
@Embeddable
public class FotoDaEmpresa {

	private Integer versao;

	private Integer largura;

	private Integer altura;

	@Column(nullable = false)
	@JdbcTypeCode(SqlTypes.SMALLINT)
	private int focoX = 50;

	@Column(nullable = false)
	@JdbcTypeCode(SqlTypes.SMALLINT)
	private int focoY = 50;

	/** Foto nova; devolve a versão anterior (para apagar o arquivo antigo), se havia. */
	public Integer trocar(int largura, int altura, int focoX, int focoY) {
		Integer anterior = versao;
		this.versao = (anterior == null ? 0 : anterior) + 1;
		this.largura = largura;
		this.altura = altura;
		focar(focoX, focoY);
		return anterior;
	}

	public Integer remover() {
		Integer anterior = versao;
		this.versao = null;
		this.largura = null;
		this.altura = null;
		this.focoX = 50;
		this.focoY = 50;
		return anterior;
	}

	public void focar(int x, int y) {
		this.focoX = Math.clamp(x, 0, 100);
		this.focoY = Math.clamp(y, 0, 100);
	}

	public boolean existe() {
		return versao != null;
	}

	public Integer getVersao() {
		return versao;
	}

	public Integer getLargura() {
		return largura;
	}

	public Integer getAltura() {
		return altura;
	}

	public int getFocoX() {
		return focoX;
	}

	public int getFocoY() {
		return focoY;
	}

}
