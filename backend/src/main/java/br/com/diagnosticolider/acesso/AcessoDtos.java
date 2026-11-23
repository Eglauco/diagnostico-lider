package br.com.diagnosticolider.acesso;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos do login e da gestão de usuários do painel. */
public final class AcessoDtos {

	/** Tamanho mínimo da senha (recomendação do NIST: comprimento vale mais que regras de símbolo). */
	public static final int SENHA_MINIMA = 8;

	private AcessoDtos() {
	}

	public record Entrar(@NotBlank(message = "Escreva o seu e-mail.") String email,
			@NotBlank(message = "Escreva a sua senha.") String senha) {
	}

	public record UsuarioSessao(Long id, String nome, String email) {

		static UsuarioSessao de(Usuario u) {
			return new UsuarioSessao(u.getId(), u.getNome(), u.getEmail());
		}

	}

	/** Quem está logado (ou null). Sempre 200, para o front não registrar erro a cada visita. */
	public record Sessao(UsuarioSessao usuario) {
	}

	public record TrocarSenha(@NotBlank(message = "Escreva a sua senha atual.") String senhaAtual,
			@NotBlank(message = "Escreva a nova senha.")
			@Size(min = SENHA_MINIMA, max = 128, message = "Use de 8 a 128 caracteres.") String novaSenha) {
	}

	public record NovoUsuario(
			@NotBlank(message = "Escreva o nome.") @Size(max = 120, message = "Use até 120 caracteres.") String nome,
			@NotBlank(message = "Escreva o e-mail.") @Email(message = "Confira o e-mail.")
			@Size(max = 254, message = "E-mail muito longo.") String email,
			@NotBlank(message = "Defina uma senha.")
			@Size(min = SENHA_MINIMA, max = 128, message = "Use de 8 a 128 caracteres.") String senha) {
	}

	public record EditarUsuario(
			@NotBlank(message = "Escreva o nome.") @Size(max = 120, message = "Use até 120 caracteres.") String nome,
			@NotBlank(message = "Escreva o e-mail.") @Email(message = "Confira o e-mail.")
			@Size(max = 254, message = "E-mail muito longo.") String email) {
	}

	public record NovaSenha(@NotBlank(message = "Defina uma senha.")
	@Size(min = SENHA_MINIMA, max = 128, message = "Use de 8 a 128 caracteres.") String senha) {
	}

	public record Situacao(@NotNull Boolean ativo) {
	}

	public record UsuarioItem(Long id, String nome, String email, boolean ativo, Instant criadoEm,
			Instant ultimoAcessoEm, boolean voce) {

		static UsuarioItem de(Usuario u, Long idAtual) {
			return new UsuarioItem(u.getId(), u.getNome(), u.getEmail(), u.isAtivo(), u.getCriadoEm(),
					u.getUltimoAcessoEm(), u.getId().equals(idAtual));
		}

	}

}
