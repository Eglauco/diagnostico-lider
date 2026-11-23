package br.com.diagnosticolider.acesso;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Quem entra no painel administrativo. Sem perfis por enquanto: todos acessam tudo. */
@Entity
@Table(name = "usuario")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String nome;

	@Column(nullable = false)
	private String email;

	@Column(name = "senha_hash", nullable = false)
	private String senhaHash;

	@Column(nullable = false)
	private boolean ativo;

	/** Muda a cada troca de senha ou desativação: sessões com valor antigo deixam de valer. */
	@Column(name = "credenciais_em", nullable = false)
	private Instant credenciaisEm;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;

	@Column(name = "ultimo_acesso_em")
	private Instant ultimoAcessoEm;

	protected Usuario() {
	}

	public Usuario(String nome, String email, String senhaHash, Instant agora) {
		this.nome = normalizarNome(nome);
		this.email = normalizarEmail(email);
		this.senhaHash = senhaHash;
		this.ativo = true;
		this.credenciaisEm = precisao(agora);
		this.criadoEm = precisao(agora);
	}

	public static String normalizarEmail(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

	private static String normalizarNome(String nome) {
		return nome.strip().replaceAll("\\s+", " ");
	}

	/** O banco guarda microssegundos: comparar instantes exige a mesma precisão dos dois lados. */
	private static Instant precisao(Instant instante) {
		return instante.truncatedTo(ChronoUnit.MICROS);
	}

	public void atualizar(String nome, String email) {
		this.nome = normalizarNome(nome);
		this.email = normalizarEmail(email);
	}

	public void trocarSenha(String senhaHash, Instant agora) {
		this.senhaHash = senhaHash;
		this.credenciaisEm = precisao(agora);
	}

	public void desativar(Instant agora) {
		this.ativo = false;
		this.credenciaisEm = precisao(agora);
	}

	public void reativar() {
		this.ativo = true;
	}

	public void registrarAcesso(Instant agora) {
		this.ultimoAcessoEm = precisao(agora);
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getEmail() {
		return email;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public Instant getCredenciaisEm() {
		return credenciaisEm;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getUltimoAcessoEm() {
		return ultimoAcessoEm;
	}

}
