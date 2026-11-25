package br.com.diagnosticolider.notificacao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Desenvolvimento: não envia nada. Registra no log o que seria enviado e guarda o HTML do último
 * e-mail num arquivo temporário, para abrir no navegador e conferir o layout.
 */
class LogEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

	private static final Path ULTIMO_EMAIL = Path.of(System.getProperty("java.io.tmpdir"), "diagnostico-lider",
			"ultimo-email.html");

	@Override
	public boolean ativo() {
		return false;
	}

	@Override
	public String enviar(Mensagem m) {
		log.info("[e-mail simulado] para={} assunto=\"{}\" anexos={} html={}", m.para(), m.assunto(),
				m.anexos().stream().map(a -> a.nome() + " (" + a.conteudo().length / 1024 + " KB)").toList(),
				guardar(comImagens(m)));
		return null;
	}

	/** No arquivo, a imagem embutida (cid:) vira data URI, para a logo aparecer no navegador. */
	private static String comImagens(Mensagem m) {
		String html = m.html();
		for (Anexo a : m.anexos()) {
			if (a.cid() != null) {
				html = html.replace("cid:" + a.cid(),
						"data:image/png;base64," + Base64.getEncoder().encodeToString(a.conteudo()));
			}
		}
		return html;
	}

	private static String guardar(String html) {
		try {
			Files.createDirectories(ULTIMO_EMAIL.getParent());
			Files.writeString(ULTIMO_EMAIL, html, StandardCharsets.UTF_8);
			return ULTIMO_EMAIL.toUri().toString();
		}
		catch (IOException e) {
			return "(não foi possível guardar: " + e.getMessage() + ")";
		}
	}

}
