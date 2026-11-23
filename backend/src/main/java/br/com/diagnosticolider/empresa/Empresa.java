package br.com.diagnosticolider.empresa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "empresa")
public class Empresa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String nome;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm = Instant.now();

	/** Cores da marca em "#RRGGBB" (null: identidade do Diagnóstico do Líder). */
	@Column(name = "cor_primaria")
	private String corPrimaria;

	@Column(name = "cor_secundaria")
	private String corSecundaria;

	/** Logomarca: versão do arquivo no bucket (null = sem logo) e tamanho em pixels. */
	@Column(name = "logo_versao")
	private Integer logoVersao;

	@Column(name = "logo_largura")
	private Integer logoLargura;

	@Column(name = "logo_altura")
	private Integer logoAltura;

	protected Empresa() {
	}

	/** Nova logomarca; devolve a versão anterior (para apagar o arquivo antigo), se havia. */
	public Integer trocarLogo(int largura, int altura) {
		Integer anterior = logoVersao;
		this.logoVersao = (anterior == null ? 0 : anterior) + 1;
		this.logoLargura = largura;
		this.logoAltura = altura;
		return anterior;
	}

	public Integer removerLogo() {
		Integer anterior = logoVersao;
		this.logoVersao = null;
		this.logoLargura = null;
		this.logoAltura = null;
		return anterior;
	}

	public Integer getLogoVersao() {
		return logoVersao;
	}

	public Integer getLogoLargura() {
		return logoLargura;
	}

	public Integer getLogoAltura() {
		return logoAltura;
	}

	public Empresa(String nome) {
		this.nome = nome;
	}

	public void renomear(String nome) {
		this.nome = nome;
	}

	/** Secundária sem principal não faz sentido: a principal define a paleta. */
	public void definirCores(String primaria, String secundaria) {
		this.corPrimaria = PaletaMarca.normalizar(primaria);
		this.corSecundaria = this.corPrimaria == null ? null : PaletaMarca.normalizar(secundaria);
	}

	/** A paleta pronta para telas, PDFs e e-mail. */
	public PaletaMarca.Tema tema() {
		return PaletaMarca.tema(corPrimaria, corSecundaria);
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public String getCorPrimaria() {
		return corPrimaria;
	}

	public String getCorSecundaria() {
		return corSecundaria;
	}

}
