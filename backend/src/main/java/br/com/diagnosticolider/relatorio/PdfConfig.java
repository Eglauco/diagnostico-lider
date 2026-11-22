package br.com.diagnosticolider.relatorio;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.diagnosticolider.config.AppProperties;

@Configuration(proxyBeanMethods = false)
class PdfConfig {

	@Bean
	PdfRenderer pdfRenderer(AppProperties props) {
		return switch (props.pdf().motor()) {
			case CHROME -> new ChromePdfRenderer(props.pdf().chromePath());
			case GOTENBERG -> new GotenbergPdfRenderer(props.pdf().gotenbergUrl());
		};
	}

}
