package br.com.diagnosticolider.notificacao;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import br.com.diagnosticolider.config.AppProperties;

@Configuration(proxyBeanMethods = false)
class EmailConfig {

	@Bean
	EmailSender emailSender(AppProperties props) {
		return switch (props.email().provedor()) {
			case LOG -> new LogEmailSender();
			case RESEND -> {
				var fabrica = new SimpleClientHttpRequestFactory();
				fabrica.setConnectTimeout(Duration.ofSeconds(5));
				fabrica.setReadTimeout(Duration.ofSeconds(30));
				yield new ResendEmailSender(RestClient.builder().requestFactory(fabrica), props.email().resendApiKey(),
						props.email().remetente());
			}
		};
	}

}
