package br.com.diagnosticolider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(String urlPublica, Pdf pdf, Email email) {

	public record Pdf(MotorPdf motor, String gotenbergUrl, String chromePath) {
	}

	public record Email(ProvedorEmail provedor, String remetente, String resendApiKey) {
	}

	public enum MotorPdf {
		CHROME, GOTENBERG
	}

	public enum ProvedorEmail {
		LOG, RESEND
	}

}
