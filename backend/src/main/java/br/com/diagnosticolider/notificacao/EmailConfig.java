package br.com.diagnosticolider.notificacao;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import br.com.diagnosticolider.config.AppProperties;

@Configuration(proxyBeanMethods = false)
class EmailConfig {

	private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

	@Bean
	EmailSender emailSender(AppProperties props) {
		EmailSender provedor = provedor(props);
		String desvio = props.email().desviarPara();
		if (desvio == null || desvio.isBlank()) {
			return provedor;
		}
		log.warn("Desvio de e-mail ligado: todo e-mail vai para {} (só para desenvolvimento).", desvio.strip());
		return new DesvioEmailSender(provedor, desvio.strip());
	}

	private static EmailSender provedor(AppProperties props) {
		return switch (props.email().provedor()) {
			case LOG -> new LogEmailSender();
			case RESEND -> {
				var fabrica = new SimpleClientHttpRequestFactory();
				fabrica.setConnectTimeout(Duration.ofSeconds(5));
				fabrica.setReadTimeout(Duration.ofSeconds(30));
				yield new ResendEmailSender(RestClient.builder().requestFactory(fabrica), props.email().resendApiKey(),
						props.email().remetente(), props.email().responderPara());
			}
		};
	}

}
