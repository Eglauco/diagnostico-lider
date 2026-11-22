package br.com.diagnosticolider.relatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
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

}
