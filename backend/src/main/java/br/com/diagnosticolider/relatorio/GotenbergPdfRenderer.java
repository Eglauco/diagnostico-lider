package br.com.diagnosticolider.relatorio;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Gera o PDF no Gotenberg 9 (Chromium em container), via rede privada do Railway.
 * Rota: POST /forms/chromium/convert/html com o arquivo index.html no campo "files".
 *
 * <p>O multipart é montado com MultiValueMap + HttpEntity (FormHttpMessageConverter).
 * Não usar MultipartBodyBuilder: ele depende de Reactive Streams, ausente no Web MVC.
 */
class GotenbergPdfRenderer implements PdfRenderer {

	private final RestClient http;

	GotenbergPdfRenderer(RestClient.Builder builder, String urlBase) {
		this.http = builder.baseUrl(urlBase).build();
	}

	@Override
	public byte[] renderizar(String html) {
		var cabecalhoArquivo = new HttpHeaders();
		cabecalhoArquivo.setContentType(MediaType.TEXT_HTML);
		cabecalhoArquivo.setContentDispositionFormData("files", "index.html");
		var arquivo = new ByteArrayResource(html.getBytes(StandardCharsets.UTF_8)) {
			@Override
			public String getFilename() {
				return "index.html";
			}
		};

		MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
		corpo.add("files", new HttpEntity<>(arquivo, cabecalhoArquivo));
		corpo.add("preferCssPageSize", "true");
		corpo.add("printBackground", "true");
		corpo.add("emulatedMediaType", "print");
		for (String margem : new String[] { "marginTop", "marginBottom", "marginLeft", "marginRight" }) {
			corpo.add(margem, "0");
		}

		return http.post()
			.uri("/forms/chromium/convert/html")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(corpo)
			.retrieve()
			.body(byte[].class);
	}

}
