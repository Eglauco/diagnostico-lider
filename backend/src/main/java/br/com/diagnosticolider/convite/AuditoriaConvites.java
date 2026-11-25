package br.com.diagnosticolider.convite;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import br.com.diagnosticolider.compartilhado.Datas;
import br.com.diagnosticolider.diagnostico.PassoDoLider;
import br.com.diagnosticolider.notificacao.Correio;
import br.com.diagnosticolider.notificacao.EmailEnviado;
import br.com.diagnosticolider.notificacao.WebhookResendService;

/**
 * A linha do tempo do líder fora das telas do painel: o e-mail com o resultado, os avisos do
 * Resend (voltou, spam) e os passos dele no diagnóstico (respondeu, viu o resultado, usou o PDF).
 * Passos que se repetem atualizam a mesma linha, com a contagem, para a linha do tempo não virar
 * uma lista de cliques.
 */
@Component
public class AuditoriaConvites {

	private static final Pattern VEZES = Pattern.compile("^(\\d+) vezes");

	private final ConviteRepository convites;

	private final EventoConviteRepository eventos;

	private final Clock clock;

	AuditoriaConvites(ConviteRepository convites, EventoConviteRepository eventos, Clock clock) {
		this.convites = convites;
		this.eventos = eventos;
		this.clock = clock;
	}

	/** O e-mail com o resultado (e o PDF) saiu. */
	@EventListener
	public void aoRegistrarEmail(Correio.EmailRegistrado email) {
		if (email.tipo() == EmailEnviado.Tipo.RESULTADO && email.diagnosticoId() != null) {
			convites.findByDiagnosticoId(email.diagnosticoId())
				.ifPresent(c -> eventos.save(new EventoConvite(c, TipoEvento.RESULTADO_ENVIADO, clock.instant(),
						"Para " + email.para() + ", com o PDF.", null, email.id())));
		}
	}

	/** O e-mail com o resultado não saiu (convite e lembrete já registram a falha onde enviam). */
	@EventListener
	public void aoNaoSair(Correio.EmailNaoSaiu falha) {
		if (falha.tipo() == EmailEnviado.Tipo.RESULTADO && falha.diagnosticoId() != null) {
			convites.findByDiagnosticoId(falha.diagnosticoId())
				.ifPresent(c -> eventos.save(new EventoConvite(c, TipoEvento.FALHA_ENVIO, clock.instant(),
						"O e-mail com o resultado não saiu: " + falha.motivo())));
		}
	}

	/** O Resend avisou que um e-mail não chegou: vira um evento (e o aviso em vermelho na lista). */
	@EventListener
	public void aoAtualizarEmail(WebhookResendService.EmailAtualizado email) {
		if (!email.situacao().naoChegou()) {
			return;
		}
		Optional<Convite> convite = email.conviteId() != null ? convites.findById(email.conviteId())
				: email.diagnosticoId() != null ? convites.findByDiagnosticoId(email.diagnosticoId()) : Optional.empty();
		convite.ifPresent(c -> {
			String qual = switch (email.tipo()) {
				case CONVITE -> "O convite";
				case LEMBRETE -> "O lembrete";
				case RESULTADO -> "O e-mail com o resultado";
				default -> "O e-mail";
			};
			boolean spam = email.situacao() == EmailEnviado.Situacao.SPAM;
			eventos.save(new EventoConvite(c, spam ? TipoEvento.EMAIL_SPAM : TipoEvento.EMAIL_DEVOLVIDO, email.quando(),
					spam ? qual + " foi marcado como spam pelo líder. Novos e-mails para este endereço não saem."
							: qual + " não chegou. " + email.detalhe(),
					null,
					email.id()));
		});
	}

	/** Depois do que o líder fez (e numa transação própria: a do resultado é só de leitura). */
	@TransactionalEventListener(fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void aoPasso(PassoDoLider passo) {
		Convite convite = convites.findByDiagnosticoId(passo.diagnosticoId()).orElse(null);
		if (convite == null) {
			return;
		}
		Instant agora = clock.instant();
		switch (passo) {
			case PassoDoLider.Respondeu r -> atualizar(convite, TipoEvento.COMECOU_RESPONDER, agora,
					"Respondeu " + r.respondidas() + " de " + r.total() + " perguntas"
							+ (r.respondidas() <= 1 ? "." : "; a última em " + Datas.porExtenso(agora, agora) + "."));
			case PassoDoLider.ViuResultado v -> contar(convite, TipoEvento.VIU_RESULTADO, agora);
			case PassoDoLider.UsouPdf u -> contar(convite,
					u.compartilhou() ? TipoEvento.COMPARTILHOU_PDF : TipoEvento.BAIXOU_PDF, agora);
			case PassoDoLider.PdfBaixadoNoPainel p -> eventos.save(new EventoConvite(convite,
					TipoEvento.PDF_BAIXADO_PAINEL, agora, null, p.autor(), null));
		}
	}

	/** A primeira vez cria a linha; as seguintes só trocam o detalhe. */
	private void atualizar(Convite convite, TipoEvento tipo, Instant agora, String detalhe) {
		eventos.findFirstByConviteIdAndTipoOrderByIdDesc(convite.getId(), tipo)
			.ifPresentOrElse(e -> e.atualizarDetalhe(detalhe),
					() -> eventos.save(new EventoConvite(convite, tipo, agora, detalhe)));
	}

	/** "2 vezes; a última em 25 de novembro, às 14h02." (a primeira vez fica sem detalhe). */
	private void contar(Convite convite, TipoEvento tipo, Instant agora) {
		eventos.findFirstByConviteIdAndTipoOrderByIdDesc(convite.getId(), tipo).ifPresentOrElse(e -> {
			int vezes = 2;
			if (e.getDetalhe() != null) {
				Matcher m = VEZES.matcher(e.getDetalhe());
				if (m.find()) {
					vezes = Integer.parseInt(m.group(1)) + 1;
				}
			}
			e.atualizarDetalhe(vezes + " vezes; a última em " + Datas.porExtenso(agora, agora) + ".");
		}, () -> eventos.save(new EventoConvite(convite, tipo, agora, null)));
	}

}
