package br.com.diagnosticolider.compartilhado;

import java.util.Optional;
import java.util.Set;

/**
 * Regra única do WhatsApp, a mesma de {@code frontend/src/app/compartilhado/entrada-telefone.ts}
 * (mude as duas juntas): DDD que existe, celular com o 9 depois do DDD, 10 ou 11 números
 * (o 55 do país é aceito e descartado).
 */
public final class Telefones {

	/** DDDs em uso no Brasil (plano de numeração da Anatel). */
	private static final Set<String> DDDS = Set.of(
			"11", "12", "13", "14", "15", "16", "17", "18", "19",
			"21", "22", "24", "27", "28",
			"31", "32", "33", "34", "35", "37", "38",
			"41", "42", "43", "44", "45", "46", "47", "48", "49",
			"51", "53", "54", "55",
			"61", "62", "63", "64", "65", "66", "67", "68", "69",
			"71", "73", "74", "75", "77", "79",
			"81", "82", "83", "84", "85", "86", "87", "88", "89",
			"91", "92", "93", "94", "95", "96", "97", "98", "99");

	private Telefones() {
	}

	/** Só os dígitos, sem o 55 do país quando ele vem na frente de um número completo. */
	public static String digitos(String texto) {
		if (texto == null) {
			return "";
		}
		String digitos = texto.replaceAll("\\D", "");
		if ((digitos.length() == 12 || digitos.length() == 13) && digitos.startsWith("55")) {
			return digitos.substring(2);
		}
		return digitos;
	}

	/** O que está errado, numa frase para quem digitou; vazio se o número está certo ou não veio. */
	public static Optional<String> problema(String texto) {
		String d = digitos(texto);
		if (d.isEmpty()) {
			return Optional.empty();
		}
		if (d.length() >= 2 && !DDDS.contains(d.substring(0, 2))) {
			return Optional.of("O DDD " + d.substring(0, 2) + " não existe: confira os dois primeiros números.");
		}
		if (d.length() == 11 && d.charAt(2) != '9') {
			return Optional.of("Celular tem o 9 logo depois do DDD: confira o número.");
		}
		if (d.length() < 10) {
			return Optional.of("Faltam números: digite o DDD e o número, como (11) 98765-4321.");
		}
		if (d.length() > 11) {
			return Optional.of("Tem números a mais: deixe só o DDD e o número, como (11) 98765-4321.");
		}
		char terceiro = d.charAt(2);
		if (d.length() == 10 && terceiro >= '6') {
			return Optional.of("Falta o 9 do celular logo depois do DDD.");
		}
		if (d.length() == 10 && terceiro < '2') {
			return Optional.of("Confira o número: celular tem o 9 logo depois do DDD.");
		}
		return Optional.empty();
	}

	/** "(11) 98765-4321" (ou "(11) 3456-7890") quando há 10 ou 11 números; senão o texto como veio. */
	public static String formatar(String texto) {
		if (texto == null) {
			return "";
		}
		String d = digitos(texto);
		if (d.length() == 10) {
			return "(%s) %s-%s".formatted(d.substring(0, 2), d.substring(2, 6), d.substring(6));
		}
		if (d.length() == 11) {
			return "(%s) %s-%s".formatted(d.substring(0, 2), d.substring(2, 7), d.substring(7));
		}
		return texto.strip();
	}

}
