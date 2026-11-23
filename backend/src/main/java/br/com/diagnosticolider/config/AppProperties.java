package br.com.diagnosticolider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(String urlPublica, Pdf pdf, Email email, AcessoInicial acessoInicial,
		Armazenamento armazenamento) {

	/**
	 * Arquivos (logomarcas). Com bucket configurado, vai para o S3 do Railway dentro da pasta do
	 * ambiente ("desenvolvimento" ou "producao"); sem bucket, fica em disco (pastaLocal).
	 */
	public record Armazenamento(String prefixo, String endpoint, String bucket, String regiao, String chave,
			String segredo, boolean estiloCaminho, String pastaLocal) {

		public boolean s3() {
			return preenchido(endpoint) && preenchido(bucket) && preenchido(chave) && preenchido(segredo);
		}

		private static boolean preenchido(String valor) {
			return valor != null && !valor.isBlank();
		}

	}

	public record Pdf(MotorPdf motor, String gotenbergUrl, String chromePath) {
	}

	/** Primeiro usuário do painel, criado na subida só se ainda não houver nenhum. */
	public record AcessoInicial(String nome, String email, String senha) {

		public boolean definido() {
			return email != null && !email.isBlank() && senha != null && !senha.isBlank();
		}

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
