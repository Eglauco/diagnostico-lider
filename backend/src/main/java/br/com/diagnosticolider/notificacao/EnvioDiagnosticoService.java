package br.com.diagnosticolider.notificacao;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import br.com.diagnosticolider.diagnostico.DiagnosticoConcluido;
import br.com.diagnosticolider.diagnostico.DiagnosticoRepository;
import br.com.diagnosticolider.diagnostico.StatusEmail;
import br.com.diagnosticolider.relatorio.RelatorioDados;
import br.com.diagnosticolider.relatorio.RelatorioDadosService;
import br.com.diagnosticolider.relatorio.RelatorioPdfService;

/**
 * Quando o líder conclui, gera o PDF e envia por e-mail. Roda depois do commit e fora da
 * requisição (virtual thread): o líder vê o resultado na hora, sem esperar o e-mail.
 */
@Service
class EnvioDiagnosticoService {

	private static final Logger log = LoggerFactory.getLogger(EnvioDiagnosticoService.class);

	private final RelatorioDadosService dados;

	private final RelatorioPdfService pdf;

	private final ITemplateEngine templates;

	private final EmailSender email;

	private final DiagnosticoRepository diagnosticos;

	private final Clock clock;

	EnvioDiagnosticoService(RelatorioDadosService dados, RelatorioPdfService pdf, ITemplateEngine templates,
			EmailSender email, DiagnosticoRepository diagnosticos, Clock clock) {
		this.dados = dados;
		this.pdf = pdf;
		this.templates = templates;
		this.email = email;
		this.diagnosticos = diagnosticos;
		this.clock = clock;
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void aoConcluir(DiagnosticoConcluido evento) {
		enviar(evento.diagnosticoId());
	}

	void enviar(UUID id) {
		try {
			RelatorioDados relatorio = dados.montar(id);
			byte[] anexo = pdf.gerar(relatorio);

			var contexto = new Context(Locale.forLanguageTag("pt-BR"));
			contexto.setVariable("r", relatorio);
			String html = templates.process("email/diagnostico-pronto", contexto);

			email.enviar(new EmailSender.Mensagem(relatorio.email(),
					relatorio.primeiroNome() + ", seu diagnóstico de carreira está pronto", html,
					List.of(new EmailSender.Anexo(relatorio.nomeArquivo(), anexo))));
			diagnosticos.atualizarStatusEmail(id, StatusEmail.ENVIADO, Instant.now(clock));
		}
		catch (RuntimeException | Error e) {
			// Error também: uma falha de classpath não pode deixar o status preso em PENDENTE.
			log.error("Falha ao enviar o diagnóstico {} por e-mail", id, e);
			diagnosticos.atualizarStatusEmail(id, StatusEmail.FALHOU, null);
		}
	}

}
