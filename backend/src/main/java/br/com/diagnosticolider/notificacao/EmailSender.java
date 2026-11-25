package br.com.diagnosticolider.notificacao;

import java.util.List;

public interface EmailSender {

	/**
	 * Envia ou lança {@link FalhaEnvio} (com o motivo em português). Devolve o id do e-mail no
	 * provedor (o Resend avisa entrega e devolução por ele); null quando não há provedor.
	 */
	String enviar(Mensagem mensagem);

	/**
	 * Envia de verdade? Falso no modo de log (sem provedor configurado): aí o que depende de
	 * e-mail, como o disparo de convites, fica desligado em vez de fingir que enviou.
	 */
	default boolean ativo() {
		return true;
	}

	/** para: um ou mais destinatários no mesmo e-mail (ex.: os gestores da empresa). */
	record Mensagem(List<String> para, String assunto, String html, List<Anexo> anexos) {

		public Mensagem(String para, String assunto, String html, List<Anexo> anexos) {
			this(List.of(para), assunto, html, anexos);
		}

	}

	/**
	 * cid: imagem embutida no corpo (o HTML usa {@code <img src="cid:...">}, como a logo); null para
	 * um anexo comum (o PDF, a planilha).
	 */
	record Anexo(String nome, byte[] conteudo, String cid) {

		public Anexo(String nome, byte[] conteudo) {
			this(nome, conteudo, null);
		}

	}

	/**
	 * O e-mail não saiu. A mensagem já vem pronta para o painel ("A cota diária acabou…").
	 * barraOsProximos: o mesmo erro vale para qualquer destinatário (cota, chave, domínio, provedor
	 * fora do ar), então não adianta seguir com o resto do lote.
	 */
	final class FalhaEnvio extends RuntimeException {

		private final boolean barraOsProximos;

		public FalhaEnvio(String mensagem, boolean barraOsProximos, Throwable causa) {
			super(mensagem, causa);
			this.barraOsProximos = barraOsProximos;
		}

		public boolean barraOsProximos() {
			return barraOsProximos;
		}

	}

}
