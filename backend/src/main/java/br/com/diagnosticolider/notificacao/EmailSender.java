package br.com.diagnosticolider.notificacao;

import java.util.List;

public interface EmailSender {

	void enviar(Mensagem mensagem);

	record Mensagem(String para, String assunto, String html, List<Anexo> anexos) {
	}

	record Anexo(String nome, byte[] conteudo) {
	}

}
