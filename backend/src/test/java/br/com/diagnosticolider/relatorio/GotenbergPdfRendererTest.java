package br.com.diagnosticolider.relatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Garante o contrato com o Gotenberg 9 sem precisar do container. */
class GotenbergPdfRendererTest {

	@Test
	void enviaIndexHtmlComoMultipartParaARotaDeHtml() {
		var builder = RestClient.builder();
		var gotenberg = MockRestServiceServer.bindTo(builder).build();
		gotenberg.expect(requestTo("http://gotenberg.railway.internal:3000/forms/chromium/convert/html"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
			.andExpect(content().string(allOf(containsString("name=\"files\"; filename=\"index.html\""),
					containsString("<h1>Olá</h1>"), containsString("name=\"preferCssPageSize\""),
					containsString("name=\"printBackground\""))))
			.andRespond(withSuccess(new byte[] { 37, 80, 68, 70 }, MediaType.APPLICATION_PDF));

		byte[] pdf = new GotenbergPdfRenderer(builder, "http://gotenberg.railway.internal:3000")
			.renderizar("<h1>Olá</h1>");

		assertThat(pdf).containsExactly(37, 80, 68, 70);
		gotenberg.verify();
	}

	/** A rota de screenshot recusa campos que não conhece: vão só os de imagem. */
	@Test
	void capturaImagemNaRotaDeScreenshotSoComCamposDeImagem() {
		var builder = RestClient.builder();
		var gotenberg = MockRestServiceServer.bindTo(builder).build();
		gotenberg.expect(requestTo("http://gotenberg.railway.internal:3000/forms/chromium/screenshot/html"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().string(allOf(containsString("name=\"files\"; filename=\"index.html\""),
					containsString("name=\"width\""), containsString("1200"), containsString("name=\"height\""),
					containsString("630"), containsString("name=\"clip\""), containsString("name=\"format\""),
					containsString("png"), not(containsString("preferCssPageSize")),
					not(containsString("printBackground")), not(containsString("marginTop")))))
			.andRespond(withSuccess(new byte[] { -119, 80, 78, 71 }, MediaType.IMAGE_PNG));

		byte[] png = new GotenbergPdfRenderer(builder, "http://gotenberg.railway.internal:3000")
			.capturar("<h1>Prévia</h1>", 1200, 630);

		assertThat(png).containsExactly(-119, 80, 78, 71);
		gotenberg.verify();
	}

}
