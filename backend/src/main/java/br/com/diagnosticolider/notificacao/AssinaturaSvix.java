package br.com.diagnosticolider.notificacao;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Confere a assinatura dos avisos do Resend (padrão Svix): HMAC-SHA256 de "id.timestamp.corpo"
 * com o segredo do webhook ("whsec_…", em Base64). O cabeçalho svix-signature pode trazer várias
 * assinaturas ("v1,abc v1,def", na troca de segredo). Aviso com mais de 5 minutos é recusado, para
 * ninguém reenviar um aviso antigo capturado.
 */
final class AssinaturaSvix {

	static final Duration TOLERANCIA = Duration.ofMinutes(5);

	private AssinaturaSvix() {
	}

	static boolean valida(String segredo, String id, String timestamp, String assinaturas, String corpo,
			Instant agora) {
		if (segredo == null || segredo.isBlank() || id == null || timestamp == null || assinaturas == null
				|| corpo == null) {
			return false;
		}
		long segundos;
		try {
			segundos = Long.parseLong(timestamp.strip());
		}
		catch (NumberFormatException e) {
			return false;
		}
		if (Math.abs(agora.getEpochSecond() - segundos) > TOLERANCIA.toSeconds()) {
			return false;
		}
		byte[] esperada;
		try {
			String base64 = segredo.strip().startsWith("whsec_") ? segredo.strip().substring(6) : segredo.strip();
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(Base64.getDecoder().decode(base64), "HmacSHA256"));
			esperada = mac.doFinal((id + "." + timestamp.strip() + "." + corpo).getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			return false;
		}
		for (String parte : assinaturas.strip().split("\\s+")) {
			int virgula = parte.indexOf(',');
			if (virgula < 0 || !"v1".equals(parte.substring(0, virgula))) {
				continue;
			}
			try {
				if (MessageDigest.isEqual(esperada, Base64.getDecoder().decode(parte.substring(virgula + 1)))) {
					return true;
				}
			}
			catch (IllegalArgumentException e) {
				// Assinatura fora do Base64: só não confere.
			}
		}
		return false;
	}

	/** A assinatura de um corpo (para os testes montarem avisos como o Resend). */
	static String assinar(String segredo, String id, String timestamp, String corpo) {
		try {
			String base64 = segredo.startsWith("whsec_") ? segredo.substring(6) : segredo;
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(Base64.getDecoder().decode(base64), "HmacSHA256"));
			return "v1," + Base64.getEncoder()
				.encodeToString(mac.doFinal((id + "." + timestamp + "." + corpo).getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

}
