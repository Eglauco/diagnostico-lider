package br.com.diagnosticolider.campanha;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * O trecho final do link (/c/{codigo}): minúsculas, números e hífens, sem acento.
 * A mesma regra existe em frontend/src/app/admin/codigo-campanha.ts: mude as duas juntas.
 */
public final class CodigoCampanha {

	public static final int MINIMO = 3;

	public static final int MAXIMO = 60;

	private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");

	private static final Pattern SEPARADORES = Pattern.compile("[^a-z0-9]+");

	private CodigoCampanha() {
	}

	/** "Grupo Ação & Cia." → "grupo-acao-cia". */
	public static String normalizar(String texto) {
		if (texto == null) {
			return "";
		}
		String semAcento = ACENTOS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
		String codigo = SEPARADORES.matcher(semAcento.toLowerCase(Locale.ROOT)).replaceAll("-");
		codigo = aparar(codigo);
		return codigo.length() > MAXIMO ? aparar(codigo.substring(0, MAXIMO)) : codigo;
	}

	public static boolean valido(String codigo) {
		return codigo.length() >= MINIMO && codigo.length() <= MAXIMO && codigo.equals(normalizar(codigo));
	}

	/** "grupo-alfa" com sufixo 2 → "grupo-alfa-2", sem passar do tamanho máximo. */
	public static String comSufixo(String codigo, int numero) {
		String sufixo = "-" + numero;
		String base = codigo.length() + sufixo.length() > MAXIMO ? aparar(codigo.substring(0, MAXIMO - sufixo.length()))
				: codigo;
		return base + sufixo;
	}

	private static String aparar(String codigo) {
		int inicio = 0;
		int fim = codigo.length();
		while (inicio < fim && codigo.charAt(inicio) == '-') {
			inicio++;
		}
		while (fim > inicio && codigo.charAt(fim - 1) == '-') {
			fim--;
		}
		return codigo.substring(inicio, fim);
	}

}
