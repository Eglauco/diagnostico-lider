package br.com.diagnosticolider.relatorio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

import org.springframework.core.io.ClassPathResource;

/**
 * As fontes da identidade (Bricolage Grotesque e Instrument Sans) em base64 dentro do HTML:
 * o PDF e a imagem de prévia saem idênticos no Chrome local e no Gotenberg, sem depender de rede.
 */
final class FontesEmbutidas {

	static final String CSS = fonte("Bricolage Grotesque", "bricolage-grotesque.woff2", "200 800", "75% 100%")
			+ fonte("Instrument Sans", "instrument-sans.woff2", "400 700", "100%");

	private FontesEmbutidas() {
	}

	private static String fonte(String familia, String arquivo, String pesos, String larguras) {
		try {
			byte[] bytes = new ClassPathResource("relatorio/fontes/" + arquivo).getContentAsByteArray();
			return """
					@font-face { font-family: '%s'; font-weight: %s; font-stretch: %s; font-style: normal;
					  src: url(data:font/woff2;base64,%s) format('woff2'); }
					""".formatted(familia, pesos, larguras, Base64.getEncoder().encodeToString(bytes));
		}
		catch (IOException e) {
			throw new UncheckedIOException("Fonte não encontrada: " + arquivo, e);
		}
	}

}
