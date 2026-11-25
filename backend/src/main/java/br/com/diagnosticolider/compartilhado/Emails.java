package br.com.diagnosticolider.compartilhado;

import java.util.Optional;

/**
 * O que está errado num e-mail, dito do jeito que a pessoa conserta ("Falta o final do domínio",
 * não "e-mail inválido"). A mesma regra existe no front ({@code compartilhado/email.ts}): mude as
 * duas juntas.
 */
public final class Emails {

	public static final int MAXIMO = 254;

	private Emails() {
	}

	/** Vazio não é problema aqui: "obrigatório" é outra regra, de quem chama. */
	public static Optional<String> problema(String email) {
		if (email == null || email.isBlank()) {
			return Optional.empty();
		}
		String e = email.strip();
		if (e.length() > MAXIMO) {
			return Optional.of("E-mail muito longo.");
		}
		if (e.chars().anyMatch(Character::isWhitespace)) {
			return Optional.of("Tire os espaços do e-mail.");
		}
		int arroba = e.indexOf('@');
		if (arroba < 0) {
			return Optional.of("Falta o @ no e-mail (ex.: nome@empresa.com.br).");
		}
		if (e.indexOf('@', arroba + 1) >= 0) {
			return Optional.of("O e-mail tem mais de um @.");
		}
		String dominio = e.substring(arroba + 1);
		if (arroba == 0) {
			return Optional.of("Falta o nome antes do @ (ex.: nome@empresa.com.br).");
		}
		if (dominio.isEmpty()) {
			return Optional.of("Falta o domínio depois do @ (ex.: @empresa.com.br).");
		}
		if (!dominio.contains(".")) {
			return Optional.of("Falta o final do domínio (ex.: .com ou .com.br).");
		}
		if (dominio.startsWith(".") || dominio.endsWith(".") || dominio.contains("..")) {
			return Optional.of("Confira o domínio do e-mail: sobra ou falta um ponto.");
		}
		return Optional.empty();
	}

}
