package br.com.diagnosticolider.convite;

import java.time.Instant;

import br.com.diagnosticolider.campanha.Campanha;
import br.com.diagnosticolider.diagnostico.Diagnostico;
import br.com.diagnosticolider.empresa.Cargo;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Um líder da avaliação da empresa, com o link pessoal dele (/i/{token}). Vem da planilha do RH,
 * do cadastro manual no painel ou do link aberto da empresa (quem começou sozinho vira cadastro
 * na hora, ver {@link OrigemLider}). No convite, o diagnóstico nasce quando o líder confirma (ou
 * corrige) os dados.
 */
@Entity
@Table(name = "convite")
public class Convite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "campanha_id")
	private Campanha campanha;

	@Column(nullable = false, unique = true)
	private String token;

	@Column(nullable = false)
	private String nome;

	@Column(nullable = false)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrigemLider origem;

	private String whatsapp;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cargo_id")
	private Cargo cargo;

	@Column(name = "cargo_outro", nullable = false)
	private boolean cargoOutro;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;

	@Column(name = "enviado_em")
	private Instant enviadoEm;

	@Column(nullable = false)
	private int envios;

	@Column(name = "primeiro_acesso_em")
	private Instant primeiroAcessoEm;

	@Column(name = "ultimo_acesso_em")
	private Instant ultimoAcessoEm;

	@Column(nullable = false)
	private int acessos;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "diagnostico_id")
	private Diagnostico diagnostico;

	/** Inativado pelo administrador: o link fecha e o líder sai dos cálculos (reversível). */
	@Column(name = "inativado_em")
	private Instant inativadoEm;

	protected Convite() {
	}

	Convite(Campanha campanha, String token, Dados dados, OrigemLider origem, Instant agora) {
		this.campanha = campanha;
		this.token = token;
		this.origem = origem;
		this.criadoEm = agora;
		atualizar(dados);
	}

	/** Os dados do líder como o RH mandou (ou como o administrador corrigiu). */
	record Dados(String nome, String email, String whatsapp, Cargo cargo, boolean cargoOutro) {
	}

	void atualizar(Dados dados) {
		this.nome = dados.nome();
		this.email = dados.email();
		this.whatsapp = dados.whatsapp();
		this.cargo = dados.cargoOutro() ? null : dados.cargo();
		this.cargoOutro = dados.cargoOutro();
	}

	Dados dados() {
		return new Dados(nome, email, whatsapp, cargo, cargoOutro);
	}

	/** Mais um acesso ao link; devolve true se foi o primeiro. */
	boolean registrarAcesso(Instant agora) {
		boolean primeiro = primeiroAcessoEm == null;
		if (primeiro) {
			primeiroAcessoEm = agora;
		}
		ultimoAcessoEm = agora;
		acessos++;
		return primeiro;
	}

	void registrarEnvio(Instant agora) {
		enviadoEm = agora;
		envios++;
	}

	void vincular(Diagnostico diagnostico) {
		this.diagnostico = diagnostico;
	}

	void inativar(Instant agora) {
		inativadoEm = agora;
	}

	void reativar() {
		inativadoEm = null;
	}

	boolean inativo() {
		return inativadoEm != null;
	}

	/** Onde o líder está: do mais avançado para o menos. */
	StatusConvite status() {
		if (inativadoEm != null) {
			return StatusConvite.INATIVO;
		}
		if (diagnostico != null) {
			return diagnostico.concluido() ? StatusConvite.CONCLUIDO : StatusConvite.COMECOU;
		}
		if (acessos > 0) {
			return StatusConvite.ABRIU;
		}
		return enviadoEm != null ? StatusConvite.ENVIADO : StatusConvite.AGUARDANDO_ENVIO;
	}

	public String primeiroNome() {
		return nome.strip().split("\\s+")[0];
	}

	public Long getId() {
		return id;
	}

	public Campanha getCampanha() {
		return campanha;
	}

	public String getToken() {
		return token;
	}

	public String getNome() {
		return nome;
	}

	public String getEmail() {
		return email;
	}

	public String getWhatsapp() {
		return whatsapp;
	}

	public Cargo getCargo() {
		return cargo;
	}

	public boolean isCargoOutro() {
		return cargoOutro;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getEnviadoEm() {
		return enviadoEm;
	}

	public int getEnvios() {
		return envios;
	}

	public Instant getPrimeiroAcessoEm() {
		return primeiroAcessoEm;
	}

	public Instant getUltimoAcessoEm() {
		return ultimoAcessoEm;
	}

	public int getAcessos() {
		return acessos;
	}

	public OrigemLider getOrigem() {
		return origem;
	}

	public Diagnostico getDiagnostico() {
		return diagnostico;
	}

	public Instant getInativadoEm() {
		return inativadoEm;
	}

}
