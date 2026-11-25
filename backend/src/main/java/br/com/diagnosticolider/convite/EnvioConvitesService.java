package br.com.diagnosticolider.convite;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import br.com.diagnosticolider.campanha.Campanha;
import br.com.diagnosticolider.compartilhado.Datas;
import br.com.diagnosticolider.config.AppProperties;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.empresa.MarcaEmpresa;
import br.com.diagnosticolider.notificacao.Correio;
import br.com.diagnosticolider.notificacao.EmailEnviado;
import br.com.diagnosticolider.notificacao.EmailSender;

/**
 * O e-mail de convite: monta (com a marca da empresa, o link pessoal e o prazo) e envia em
 * segundo plano, um por vez, registrando na auditoria o envio ou a falha de cada um.
 */
@Service
class EnvioConvitesService {

	private static final Logger log = LoggerFactory.getLogger(EnvioConvitesService.class);

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	/** Quem recebe lembrete: já recebeu o convite e ainda não concluiu. */
	static final Set<StatusConvite> LEMBRAVEIS = EnumSet.of(StatusConvite.ENVIADO, StatusConvite.ABRIU,
			StatusConvite.COMECOU);

	private final ConviteRepository convites;

	private final EventoConviteRepository eventos;

	private final Correio correio;

	private final ITemplateEngine templates;

	private final TransactionTemplate transacao;

	private final Clock clock;

	private final String urlPublica;

	EnvioConvitesService(ConviteRepository convites, EventoConviteRepository eventos, Correio correio,
			ITemplateEngine templates, TransactionTemplate transacao, Clock clock, AppProperties propriedades) {
		this.convites = convites;
		this.eventos = eventos;
		this.correio = correio;
		this.templates = templates;
		this.transacao = transacao;
		this.clock = clock;
		this.urlPublica = propriedades.urlPublica().replaceAll("/+$", "");
	}

	/** autor: quem mandou enviar no painel (a auditoria guarda; o envio roda em segundo plano). */
	@Async
	public void enviar(List<Long> ids, String autor) {
		for (int i = 0; i < ids.size(); i++) {
			Long id = ids.get(i);
			try {
				transacao.executeWithoutResult(status -> enviarUm(id, autor));
			}
			catch (EmailSender.FalhaEnvio e) {
				// Falha esperada (cota, domínio, endereço): uma linha basta; o detalhe técnico fica no debug.
				log.warn("O convite {} não saiu: {}", id, e.getMessage());
				log.debug("Detalhe da falha do convite {}", id, e);
				registrarFalha(id, e.getMessage());
				if (e.barraOsProximos()) {
					// Cota, chave ou domínio: o mesmo erro valeria para todos. Os que faltam ficam
					// com o motivo, sem chamar o provedor de novo, e saem num próximo envio.
					List<Long> resto = ids.subList(i + 1, ids.size());
					log.warn("Envio interrompido: {} convites ficaram para depois", resto.size());
					resto.forEach(r -> registrarFalha(r, e.getMessage()));
					return;
				}
			}
			catch (RuntimeException e) {
				log.error("Falha ao enviar o convite {}", id, e);
				registrarFalha(id, resumo(e));
			}
		}
	}

	/**
	 * Um convite só, na hora: quem chama espera e mostra se saiu. A falha entra na linha do tempo
	 * (como no envio em lote) e sobe com o motivo.
	 */
	void enviarAgora(Long id, String autor) {
		try {
			transacao.executeWithoutResult(status -> enviarUm(id, autor));
		}
		catch (EmailSender.FalhaEnvio e) {
			log.warn("O convite {} não saiu: {}", id, e.getMessage());
			registrarFalha(id, e.getMessage());
			throw e;
		}
		catch (RuntimeException e) {
			log.error("Falha ao enviar o convite {}", id, e);
			registrarFalha(id, resumo(e));
			throw e;
		}
	}

	/**
	 * Lembrete antes do prazo, um por vez, para quem recebeu o convite e não concluiu. Síncrono:
	 * o agendador espera o fim para registrar quantos saíram. Cota, chave ou domínio param o lote.
	 */
	ResultadoLembrete lembrar(List<Long> ids, String chamada) {
		int enviados = 0;
		int falhas = 0;
		String motivo = null;
		for (Long id : ids) {
			try {
				if (Boolean.TRUE.equals(transacao.execute(status -> lembrarUm(id, chamada)))) {
					enviados++;
				}
			}
			catch (EmailSender.FalhaEnvio e) {
				log.warn("O lembrete do convite {} não saiu: {}", id, e.getMessage());
				falhas++;
				motivo = e.getMessage();
				registrarFalha(id, "Lembrete: " + e.getMessage());
				if (e.barraOsProximos()) {
					break;
				}
			}
			catch (RuntimeException e) {
				log.error("Falha ao enviar o lembrete do convite {}", id, e);
				falhas++;
				motivo = resumo(e);
				registrarFalha(id, resumo(e));
			}
		}
		return new ResultadoLembrete(enviados, falhas, motivo);
	}

	private boolean lembrarUm(Long id, String chamada) {
		Convite convite = convites.findWithCampanhaById(id).orElse(null);
		if (convite == null || convite.inativo() || !LEMBRAVEIS.contains(convite.status())
				|| convite.getCampanha().encerrada(agora())) {
			return false;
		}
		Long emailId = correio.enviar(new EmailSender.Mensagem(convite.getEmail(),
				chamada + " o Diagnóstico de Carreira do Líder", htmlLembrete(convite, chamada), List.of()),
				new Correio.Origem(EmailEnviado.Tipo.LEMBRETE, convite.getCampanha().getEmpresa().getId(), convite.getId(),
						null));
		eventos.save(new EventoConvite(convite, TipoEvento.LEMBRETE, agora(),
				chamada + ". Para " + convite.getEmail() + ".", null, emailId));
		return true;
	}

	private void registrarFalha(Long id, String motivo) {
		transacao.executeWithoutResult(status -> convites.findById(id)
			.ifPresent(c -> eventos.save(new EventoConvite(c, TipoEvento.FALHA_ENVIO, agora(), motivo))));
	}

	private void enviarUm(Long id, String autor) {
		Convite convite = convites.findWithCampanhaById(id).orElse(null);
		if (convite == null || convite.inativo() || convite.status() == StatusConvite.CONCLUIDO
				|| convite.getCampanha().encerrada(agora())) {
			return;
		}
		Empresa empresa = convite.getCampanha().getEmpresa();
		Long emailId = correio.enviar(new EmailSender.Mensagem(convite.getEmail(),
				empresa.getNome() + " convida você para o Diagnóstico de Carreira do Líder", html(convite), List.of()),
				new Correio.Origem(EmailEnviado.Tipo.CONVITE, empresa.getId(), convite.getId(), null));
		convite.registrarEnvio(agora());
		eventos.save(new EventoConvite(convite, TipoEvento.ENVIADO, agora(), "Para " + convite.getEmail() + ".", autor,
				emailId));
	}

	String html(Convite convite) {
		return renderizar(convite.getCampanha(), convite.primeiroNome(), urlPublica + "/i/" + convite.getToken(), null,
				false);
	}

	/** O lembrete: o mesmo e-mail do convite, com a chamada do prazo no lugar do título. */
	String htmlLembrete(Convite convite, String chamada) {
		return renderizar(convite.getCampanha(), convite.primeiroNome(), urlPublica + "/i/" + convite.getToken(), chamada,
				convite.status() == StatusConvite.COMECOU);
	}

	String htmlLembreteExemplo(Campanha campanha, String chamada) {
		return renderizar(campanha, "Ana", urlPublica + "/i/exemplo", chamada, false);
	}

	/** Prévia antes de importar alguém: um líder de exemplo. */
	String htmlExemplo(Campanha campanha) {
		return renderizar(campanha, "Ana", urlPublica + "/i/exemplo", null, false);
	}

	private String renderizar(Campanha campanha, String primeiroNome, String link, String lembrete,
			boolean continuar) {
		Empresa empresa = campanha.getEmpresa();
		var tema = MarcaEmpresa.temaDe(empresa);
		String prazo = campanha.encerraEm() == null ? null : Datas.porExtenso(campanha.encerraEm(), agora());
		var dados = new ConviteEmail(primeiroNome, empresa.getNome(), MarcaEmpresa.siglaDe(empresa.getNome()),
				ConviteEmail.logoDe(empresa, urlPublica), tema, link, prazo, lembrete, continuar);
		var contexto = new Context(PT_BR);
		contexto.setVariable("c", dados);
		return templates.process("email/convite", contexto);
	}

	/** Erro fora do provedor (montar o e-mail, banco): vai o técnico mesmo, para dar pista. */
	private static String resumo(RuntimeException e) {
		String mensagem = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
		return "Erro inesperado ao montar ou enviar o e-mail: " + mensagem;
	}

	private Instant agora() {
		return Instant.now(clock);
	}

}
