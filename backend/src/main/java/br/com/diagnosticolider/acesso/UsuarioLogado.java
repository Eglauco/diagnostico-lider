package br.com.diagnosticolider.acesso;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * O usuário guardado na sessão. Sem perfis (authorities vazias): estar autenticado basta.
 * "credenciaisEm" é comparado com o banco a cada requisição (ver SessaoValidaFilter).
 */
public final class UsuarioLogado implements UserDetails, CredentialsContainer, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final Long id;

	private final String email;

	private final boolean ativo;

	private final Instant credenciaisEm;

	private String senhaHash;

	private UsuarioLogado(Long id, String email, boolean ativo, Instant credenciaisEm, String senhaHash) {
		this.id = id;
		this.email = email;
		this.ativo = ativo;
		this.credenciaisEm = credenciaisEm;
		this.senhaHash = senhaHash;
	}

	static UsuarioLogado de(Usuario usuario) {
		return new UsuarioLogado(usuario.getId(), usuario.getEmail(), usuario.isAtivo(), usuario.getCredenciaisEm(),
				usuario.getSenhaHash());
	}

	public Long getId() {
		return id;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public Instant getCredenciaisEm() {
		return credenciaisEm;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public String getPassword() {
		return senhaHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	/**
	 * Sempre habilitado aqui: o Spring checaria "desativado" antes da senha e revelaria que o
	 * e-mail existe. A desativação é conferida depois que a senha confere (AcessoController).
	 */
	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public void eraseCredentials() {
		this.senhaHash = null;
	}

}
