package br.com.diagnosticolider.notificacao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.diagnosticolider.config.AppProperties;

@Configuration(proxyBeanMethods = false)
class EmailConfig {

	@Bean
	EmailSender emailSender(AppProperties props) {
		return switch (props.email().provedor()) {
			case LOG -> new LogEmailSender();
			case RESEND -> new ResendEmailSender(props.email().resendApiKey(), props.email().remetente());
		};
	}

}
