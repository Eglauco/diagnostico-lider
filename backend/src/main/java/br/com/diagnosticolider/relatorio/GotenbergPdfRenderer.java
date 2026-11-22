package br.com.diagnosticolider.relatorio;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Gera o PDF no Gotenberg 9 (Chromium em container), via rede privada do Railway.
 * Rota: POST /forms/chromium/convert/html com o arquivo index.html no campo "files".
 */
class GotenbergPdfRenderer implements PdfRenderer {

	private final RestClient http;

	GotenbergPdfRenderer(String urlBase) {
		var fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(Duration.ofSeconds(5));
		fabrica.setReadTimeout(Duration.ofSeconds(60));
		this.http = RestClient.builder().baseUrl(urlBase).requestFactory(fabrica).build();
	}

	@Override
	public byte[] renderizar(String html) {
		var corpo = new MultipartBodyBuilder();
		corpo.part("files", new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8)) {
			@Override
			public String getFilename() {
				return "index.html";
			}
		}).contentType(MediaType.TEXT_HTML);
		corpo.part("preferCssPageSize", "true");
		corpo.part("printBackground", "true");
		corpo.part("emulatedMediaType", "print");
		for (String margem : new String[] { "marginTop", "marginBottom", "marginLeft", "marginRight" }) {
			corpo.part(margem, "0");
		}

		return http.post()
			.uri("/forms/chromium/convert/html")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(corpo.build())
			.retrieve()
			.body(byte[].class);
	}

}
