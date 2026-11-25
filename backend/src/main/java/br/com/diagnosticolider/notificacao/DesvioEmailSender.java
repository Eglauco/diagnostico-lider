package br.com.diagnosticolider.notificacao;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Só para desenvolvimento ({@code EMAIL_DESVIAR_PARA} no backend/.env): o e-mail sai de verdade pelo
 * provedor, igual ao de produção, mas vai para um endereço só. O assunto diz para quem ele iria.
 * Assim os testes usam o Resend sem escrever para os endereços de teste do banco local, que
 * voltariam e pesariam na reputação do domínio.
 *
 * <p>
 * A auditoria continua registrando o destinatário original (o {@link Correio} guarda a mensagem
 * antes do desvio), como em produção.
 */
final class DesvioEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(DesvioEmailSender.class);

	private final EmailSender provedor;

	private final String para;

	DesvioEmailSender(EmailSender provedor, String para) {
		this.provedor = provedor;
		this.para = para;
	}

	@Override
	public String enviar(Mensagem mensagem) {
		log.info("E-mail para {} desviado para {}.", mensagem.para(), para);
		return provedor.enviar(desviar(mensagem, para));
	}

	@Override
	public boolean ativo() {
		return provedor.ativo();
	}

	static Mensagem desviar(Mensagem mensagem, String para) {
		return new Mensagem(List.of(para), "[Teste → " + destino(mensagem.para()) + "] " + mensagem.assunto(),
				mensagem.html(), mensagem.anexos());
	}

	/** "ana@x.com" ou, para vários gestores, "ana@x.com +2" (o assunto não vira uma lista). */
	private static String destino(List<String> para) {
		if (para.isEmpty()) {
			return "ninguém";
		}
		return para.size() == 1 ? para.getFirst() : para.getFirst() + " +" + (para.size() - 1);
	}

}
