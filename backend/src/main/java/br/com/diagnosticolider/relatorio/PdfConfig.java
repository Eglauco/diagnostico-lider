package br.com.diagnosticolider.relatorio;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import br.com.diagnosticolider.config.AppProperties;

@Configuration(proxyBeanMethods = false)
class PdfConfig {

	@Bean
	PdfRenderer pdfRenderer(AppProperties props) {
		return switch (props.pdf().motor()) {
			case CHROME -> new ChromePdfRenderer(props.pdf().chromePath());
			case GOTENBERG -> new GotenbergPdfRenderer(clienteComTimeout(), props.pdf().gotenbergUrl());
		};
	}

	private static RestClient.Builder clienteComTimeout() {
		var fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(Duration.ofSeconds(5));
		fabrica.setReadTimeout(Duration.ofSeconds(60));
		return RestClient.builder().requestFactory(fabrica);
	}

}
