package br.com.diagnosticolider.notificacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Desenvolvimento: não envia nada, só registra no log o que seria enviado. */
class LogEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

	@Override
	public void enviar(Mensagem m) {
		log.info("[e-mail simulado] para={} assunto=\"{}\" anexos={}", m.para(), m.assunto(),
				m.anexos().stream().map(a -> a.nome() + " (" + a.conteudo().length / 1024 + " KB)").toList());
	}

}
