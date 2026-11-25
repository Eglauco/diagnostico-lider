package br.com.diagnosticolider.notificacao;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Por onde sai todo e-mail do sistema: envia pelo {@link EmailSender} e guarda no histórico
 * ({@link EmailEnviado}), com o id do Resend para os avisos de entrega e devolução. Avisa o resto do
 * sistema ({@link EmailRegistrado}, {@link EmailNaoSaiu}) para a linha do tempo do líder.
 */
@Service
public class Correio {

	private final EmailSender sender;

	private final EmailEnviadoRepository emails;

	private final ApplicationEventPublisher eventos;

	private final Clock clock;

	private final LogoEmbutida logo;

	Correio(EmailSender sender, EmailEnviadoRepository emails, ApplicationEventPublisher eventos, Clock clock,
			LogoEmbutida logo) {
		this.sender = sender;
		this.emails = emails;
		this.eventos = eventos;
		this.clock = clock;
		this.logo = logo;
	}

	/** De onde veio o e-mail: o tipo e a quem ele se refere (empresa, convite, diagnóstico). */
	public record Origem(EmailEnviado.Tipo tipo, Long empresaId, Long conviteId, UUID diagnosticoId) {
	}

	/** Um e-mail saiu (o provedor aceitou). id: o registro no histórico. */
	public record EmailRegistrado(Long id, EmailEnviado.Tipo tipo, Long empresaId, Long conviteId,
			UUID diagnosticoId, String para) {
	}

	/** Um e-mail não saiu (o provedor recusou ou deu erro antes de enviar). */
	public record EmailNaoSaiu(EmailEnviado.Tipo tipo, Long conviteId, UUID diagnosticoId, String motivo) {
	}

	public boolean ativo() {
		return sender.ativo();
	}

	/**
	 * Envia (com a logo embutida, ver {@link LogoEmbutida}) e guarda. Devolve o id no histórico;
	 * lança {@link EmailSender.FalhaEnvio} se não saiu.
	 */
	@Transactional
	public Long enviar(EmailSender.Mensagem mensagem, Origem origem) {
		String provedorId = sender.enviar(logo.embutir(mensagem));
		String para = String.join(", ", mensagem.para());
		EmailEnviado salvo = emails.save(new EmailEnviado(provedorId, origem.tipo(), origem.empresaId(),
				origem.conviteId(), origem.diagnosticoId(), para, mensagem.assunto(), clock.instant()));
		eventos.publishEvent(new EmailRegistrado(salvo.getId(), origem.tipo(), origem.empresaId(), origem.conviteId(),
				origem.diagnosticoId(), para));
		return salvo.getId();
	}

	/** Registra que um e-mail não saiu (o motivo vai para a linha do tempo do líder). */
	@Transactional
	public void naoSaiu(Origem origem, String motivo) {
		eventos.publishEvent(new EmailNaoSaiu(origem.tipo(), origem.conviteId(), origem.diagnosticoId(), motivo));
	}

}
