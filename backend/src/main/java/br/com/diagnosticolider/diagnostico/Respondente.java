package br.com.diagnosticolider.diagnostico;

import java.util.Locale;

import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Quem respondeu, como declarou no momento do diagnóstico. */
@Embeddable
public class Respondente {

	@Column(name = "nome", nullable = false)
	private String nome;

	@Column(name = "email", nullable = false)
	private String email;

	/** Só dígitos, com DDI (ex.: 5511987654321), pronto para integrações de WhatsApp. */
	@Column(name = "whatsapp", nullable = false)
	private String whatsapp;

	@Column(name = "empresa_informada")
	private String empresaInformada;

	protected Respondente() {
	}

	private Respondente(String nome, String email, String whatsapp, String empresaInformada) {
		this.nome = nome;
		this.email = email;
		this.whatsapp = whatsapp;
		this.empresaInformada = empresaInformada;
	}

	public static Respondente de(String nome, String email, String whatsapp, String empresa) {
		return new Respondente(normalizarTexto(nome), email.strip().toLowerCase(Locale.ROOT),
				normalizarWhatsapp(whatsapp), empresa == null || empresa.isBlank() ? null : normalizarTexto(empresa));
	}

	static String normalizarWhatsapp(String valor) {
		String digitos = valor.replaceAll("\\D", "");
		if (digitos.length() == 10 || digitos.length() == 11) {
			return "55" + digitos;
		}
		if ((digitos.length() == 12 || digitos.length() == 13) && digitos.startsWith("55")) {
			return digitos;
		}
		throw new RegraNegocioException("Informe o WhatsApp com DDD, por exemplo (11) 98765-4321.");
	}

	private static String normalizarTexto(String valor) {
		return valor.strip().replaceAll("\\s+", " ");
	}

	public String primeiroNome() {
		return nome.split(" ")[0];
	}

	/** "ana.souza@empresa.com" → "an•••@empresa.com": o resultado pode ser compartilhado. */
	public String emailMascarado() {
		int arroba = email.indexOf('@');
		String usuario = email.substring(0, arroba);
		String visivel = usuario.substring(0, Math.min(2, usuario.length()));
		return visivel + "•••" + email.substring(arroba);
	}

	public String getNome() {
		return nome;
	}

	public String getEmail() {
		return email;
	}

	public String getWhatsapp() {
		return whatsapp;
	}

	public String getEmpresaInformada() {
		return empresaInformada;
	}

}
