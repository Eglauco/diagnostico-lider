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
 * Gera o PDF (e a imagem de prévia do link) no Gotenberg 9 (Chromium em container), via rede
 * privada do Railway. Rotas: POST /forms/chromium/convert/html e /forms/chromium/screenshot/html,
 * com o arquivo index.html no campo "files".
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
		MultiValueMap<String, Object> corpo = formulario(html);
		corpo.add("preferCssPageSize", "true");
		corpo.add("printBackground", "true");
		corpo.add("emulatedMediaType", "print");
		for (String margem : new String[] { "marginTop", "marginBottom", "marginLeft", "marginRight" }) {
			corpo.add(margem, "0");
		}
		return enviar("/forms/chromium/convert/html", corpo);
	}

	/** Rota de screenshot do Gotenberg 9: só campos que ela conhece (ela recusa os outros). */
	@Override
	public byte[] capturar(String html, int largura, int altura) {
		MultiValueMap<String, Object> corpo = formulario(html);
		corpo.add("width", String.valueOf(largura));
		corpo.add("height", String.valueOf(altura));
		corpo.add("clip", "true");
		corpo.add("format", "png");
		return enviar("/forms/chromium/screenshot/html", corpo);
	}

	private static MultiValueMap<String, Object> formulario(String html) {
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
		return corpo;
	}

	private byte[] enviar(String rota, MultiValueMap<String, Object> corpo) {
		return http.post().uri(rota).contentType(MediaType.MULTIPART_FORM_DATA).body(corpo).retrieve().body(byte[].class);
	}

}
