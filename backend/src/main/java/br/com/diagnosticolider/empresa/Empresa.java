package br.com.diagnosticolider.empresa;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
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

	/** Foto de fundo da página do RH: versão no bucket (null = sem foto), tamanho e ponto de foco (0 a 100). */
	@Column(name = "fundo_versao")
	private Integer fundoVersao;

	@Column(name = "fundo_largura")
	private Integer fundoLargura;

	@Column(name = "fundo_altura")
	private Integer fundoAltura;

	@Column(name = "fundo_foco_x", nullable = false)
	@JdbcTypeCode(SqlTypes.SMALLINT)
	private int fundoFocoX = 50;

	@Column(name = "fundo_foco_y", nullable = false)
	@JdbcTypeCode(SqlTypes.SMALLINT)
	private int fundoFocoY = 50;

	/**
	 * Quando a avaliação da empresa encerra (null: sem data). Depois dela os links fecham, os
	 * convites e lembretes param e o relatório da empresa vira o resultado final.
	 */
	@Column(name = "avaliacao_encerra_em")
	private Instant encerraEm;

	/** Quem recebe o relatório diário de participação e o consolidado final (pode ser ninguém). */
	@ElementCollection
	@CollectionTable(name = "empresa_gestor", joinColumns = @JoinColumn(name = "empresa_id"))
	@OrderColumn(name = "ordem")
	@Column(name = "email", nullable = false)
	private List<String> gestores = new ArrayList<>();

	/** Relatório diário suspenso pelo administrador (o consolidado final continua saindo). */
	@Column(name = "gestor_pausado", nullable = false)
	private boolean gestorPausado;

	@Column(name = "gestor_diario_em")
	private Instant gestorDiarioEm;

	/** Quando o consolidado final saiu; volta a nulo se a avaliação reabrir (prazo novo, líder novo). */
	@Column(name = "gestor_final_em")
	private Instant gestorFinalEm;

	@Column(name = "lembrete_3_dias_em")
	private Instant lembrete3DiasEm;

	@Column(name = "lembrete_1_dia_em")
	private Instant lembrete1DiaEm;

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

	/** Nova foto de fundo; devolve a versão anterior (para apagar o arquivo antigo), se havia. */
	public Integer trocarFundo(int largura, int altura, int focoX, int focoY) {
		Integer anterior = fundoVersao;
		this.fundoVersao = (anterior == null ? 0 : anterior) + 1;
		this.fundoLargura = largura;
		this.fundoAltura = altura;
		focarFundo(focoX, focoY);
		return anterior;
	}

	public Integer removerFundo() {
		Integer anterior = fundoVersao;
		this.fundoVersao = null;
		this.fundoLargura = null;
		this.fundoAltura = null;
		this.fundoFocoX = 50;
		this.fundoFocoY = 50;
		return anterior;
	}

	/** O ponto da foto que fica sempre à vista no corte (0 a 100, da esquerda e do topo). */
	public void focarFundo(int x, int y) {
		this.fundoFocoX = Math.clamp(x, 0, 100);
		this.fundoFocoY = Math.clamp(y, 0, 100);
	}

	public Integer getFundoVersao() {
		return fundoVersao;
	}

	public Integer getFundoLargura() {
		return fundoLargura;
	}

	public Integer getFundoAltura() {
		return fundoAltura;
	}

	public int getFundoFocoX() {
		return fundoFocoX;
	}

	public int getFundoFocoY() {
		return fundoFocoY;
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

	/** Prazo novo: os lembretes aos líderes valem para ele, então voltam a sair. */
	public void definirEncerramento(Instant encerraEm) {
		if (!Objects.equals(this.encerraEm, encerraEm)) {
			this.lembrete3DiasEm = null;
			this.lembrete1DiaEm = null;
		}
		this.encerraEm = encerraEm;
	}

	public List<String> getGestores() {
		return List.copyOf(gestores);
	}

	public void definirGestores(List<String> emails) {
		gestores.clear();
		gestores.addAll(emails);
	}

	public boolean isGestorPausado() {
		return gestorPausado;
	}

	public void pausarGestor(boolean pausado) {
		this.gestorPausado = pausado;
	}

	public Instant getGestorDiarioEm() {
		return gestorDiarioEm;
	}

	public void registrarDiario(Instant quando) {
		this.gestorDiarioEm = quando;
	}

	public Instant getGestorFinalEm() {
		return gestorFinalEm;
	}

	/** quando = null: a avaliação reabriu e o consolidado final volta a ser esperado. */
	public void registrarFinal(Instant quando) {
		this.gestorFinalEm = quando;
	}

	public Instant getLembrete3DiasEm() {
		return lembrete3DiasEm;
	}

	public Instant getLembrete1DiaEm() {
		return lembrete1DiaEm;
	}

	public void registrarLembrete3Dias(Instant quando) {
		this.lembrete3DiasEm = quando;
	}

	/** O de 1 dia cobre o de 3 (prazo curto): o de 3 não sai mais depois dele. */
	public void registrarLembrete1Dia(Instant quando) {
		this.lembrete1DiaEm = quando;
		if (this.lembrete3DiasEm == null) {
			this.lembrete3DiasEm = quando;
		}
	}

	public Instant getEncerraEm() {
		return encerraEm;
	}

	/** A avaliação já encerrou (passou da data marcada)? */
	public boolean encerrada(Instant agora) {
		return encerraEm != null && !agora.isBefore(encerraEm);
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
