package br.com.diagnosticolider.notificacao;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.notificacao.EmailEnviado.Situacao;
import tools.jackson.databind.json.JsonMapper;

/**
 * Os avisos do Resend sobre cada e-mail: entregue, atrasado, devolvido, spam, bloqueado ou falhou.
 * Atualiza o histórico ({@link EmailEnviado}) e avisa a linha do tempo do líder
 * ({@link EmailAtualizado}). Abertura e clique não chegam: o rastreio está desligado de propósito.
 */
@Service
public class WebhookResendService {

	private static final Logger log = LoggerFactory.getLogger(WebhookResendService.class);

	private final EmailEnviadoRepository emails;

	private final WebhookRecebidoRepository recebidos;

	private final ApplicationEventPublisher eventos;

	private final JsonMapper json;

	private final Clock clock;

	WebhookResendService(EmailEnviadoRepository emails, WebhookRecebidoRepository recebidos,
			ApplicationEventPublisher eventos, JsonMapper json, Clock clock) {
		this.emails = emails;
		this.recebidos = recebidos;
		this.eventos = eventos;
		this.json = json;
		this.clock = clock;
	}

	/** A situação de um e-mail mudou por um aviso do Resend. */
	public record EmailAtualizado(Long id, EmailEnviado.Tipo tipo, Long empresaId, Long conviteId, UUID diagnosticoId,
			Situacao situacao, String detalhe, Instant quando) {
	}

	/** svixId: o id do aviso (o mesmo nas repetições). */
	@Transactional
	public void processar(String svixId, String corpo) {
		if (recebidos.existsById(svixId)) {
			return;
		}
		recebidos.save(new WebhookRecebido(svixId, clock.instant()));
		AvisoResend aviso = json.readValue(corpo, AvisoResend.class);
		if (aviso.data() == null || aviso.data().email_id() == null) {
			return;
		}
		Situacao situacao = situacao(aviso.type());
		if (situacao == null) {
			return;
		}
		EmailEnviado email = emails.findByProvedorId(aviso.data().email_id()).orElse(null);
		if (email == null) {
			// E-mail de antes do histórico, de outro ambiente ou do teste do Resend: nada a fazer.
			log.debug("Aviso {} de um e-mail desconhecido ({})", aviso.type(), aviso.data().email_id());
			return;
		}
		Instant quando = instante(aviso.created_at());
		String detalhe = detalhe(situacao, aviso.data());
		if (email.atualizar(situacao, quando, detalhe)) {
			log.info("E-mail {} ({}) agora {}", email.getId(), email.getTipo(), situacao);
			eventos.publishEvent(new EmailAtualizado(email.getId(), email.getTipo(), email.getEmpresaId(),
					email.getConviteId(), email.getDiagnosticoId(), situacao, detalhe, quando));
		}
	}

	static Situacao situacao(String tipo) {
		return switch (tipo == null ? "" : tipo) {
			case "email.delivered" -> Situacao.ENTREGUE;
			case "email.delivery_delayed" -> Situacao.ATRASADO;
			case "email.bounced" -> Situacao.DEVOLVIDO;
			case "email.complained" -> Situacao.SPAM;
			case "email.suppressed" -> Situacao.BLOQUEADO;
			case "email.failed" -> Situacao.FALHOU;
			default -> null;
		};
	}

	/** O que aconteceu, para quem vai agir (o administrador e o RH), em português. */
	static String detalhe(Situacao situacao, AvisoResend.Dados dados) {
		return switch (situacao) {
			case ENTREGUE, ENVIADO -> null;
			case ATRASADO -> "A entrega está atrasando (caixa cheia ou servidor instável do destinatário). "
					+ "O Resend continua tentando.";
			case SPAM -> "O destinatário marcou o e-mail como spam. Novos e-mails para este endereço ficam bloqueados.";
			case BLOQUEADO -> "O Resend não enviou: este endereço já devolveu e-mail ou marcou spam antes. "
					+ "Confira o e-mail com o RH.";
			case FALHOU -> "O Resend não conseguiu enviar"
					+ (dados.failed() == null || dados.failed().reason() == null ? "." : ": " + dados.failed().reason());
			case DEVOLVIDO -> devolucao(dados.bounce());
		};
	}

	static String devolucao(AvisoResend.Devolucao bounce) {
		if (bounce == null) {
			return "O e-mail voltou. Confira o endereço com o RH.";
		}
		String sub = bounce.subType() == null ? "" : bounce.subType();
		if ("Transient".equalsIgnoreCase(bounce.type()) || "Temporary".equalsIgnoreCase(bounce.type())) {
			return switch (sub) {
				case "MailboxFull" -> "A caixa de entrada do líder está cheia. Vale tentar de novo depois.";
				case "MessageTooLarge" -> "O e-mail ficou grande demais para o servidor do destinatário.";
				case "ContentRejected", "AttachmentRejected" -> "O servidor da empresa recusou o conteúdo do e-mail. "
						+ "Peça à TI para liberar o remetente diagnostico@terasoftware.com.br.";
				default -> "O servidor do destinatário recusou por enquanto. Vale tentar de novo mais tarde.";
			};
		}
		return switch (sub) {
			case "Suppressed", "OnAccountSuppressionList" -> "O endereço está bloqueado no Resend por devoluções "
					+ "anteriores. Confira o e-mail com o RH.";
			case "NoEmail", "General" -> "O endereço não existe ou o servidor recusou. Confira o e-mail com o RH.";
			case "MessageRejected", "ContentRejected" -> "O servidor da empresa recusou o e-mail (filtro de spam ou "
					+ "bloqueio). Peça à TI para liberar o remetente diagnostico@terasoftware.com.br.";
			default -> "O e-mail voltou" + (bounce.message() == null ? "." : ": " + bounce.message());
		};
	}

	private Instant instante(String texto) {
		try {
			return texto == null ? clock.instant() : Instant.parse(texto);
		}
		catch (DateTimeParseException e) {
			return clock.instant();
		}
	}

}
