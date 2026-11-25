package br.com.diagnosticolider.acompanhamento;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import br.com.diagnosticolider.acompanhamento.Agenda.Acao;
import br.com.diagnosticolider.acompanhamento.Agenda.Estado;
import br.com.diagnosticolider.acompanhamento.EnvioAcompanhamento.Tipo;
import br.com.diagnosticolider.acesso.QuemEsta;
import br.com.diagnosticolider.compartilhado.Datas;
import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import br.com.diagnosticolider.config.AppProperties;
import br.com.diagnosticolider.convite.ConviteEmail;
import br.com.diagnosticolider.convite.LembreteService;
import br.com.diagnosticolider.convite.ParticipacaoService;
import br.com.diagnosticolider.convite.ParticipacaoService.ResumoParticipacao;
import br.com.diagnosticolider.convite.ResultadoLembrete;
import br.com.diagnosticolider.diagnostico.DiagnosticoRepository;
import br.com.diagnosticolider.diagnostico.StatusDiagnostico;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.empresa.EmpresaRepository;
import br.com.diagnosticolider.empresa.MarcaEmpresa;
import br.com.diagnosticolider.empresa.PaletaMarca;
import br.com.diagnosticolider.notificacao.Correio;
import br.com.diagnosticolider.notificacao.EmailEnviado;
import br.com.diagnosticolider.notificacao.EmailEnviadoRepository;
import br.com.diagnosticolider.notificacao.EmailSender;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaService;
import br.com.diagnosticolider.relatorio.RelatorioPdfService;

/**
 * Acompanhamento automático de uma empresa: o relatório de participação ao gestor todo dia útil
 * (PDF e Excel), o consolidado quando a avaliação chega ao fim e os lembretes aos líderes antes do
 * prazo. O {@link AgendadorAcompanhamento} chama {@link #processar} a cada 5 minutos; o painel usa
 * o resto (situação, prévia, "Enviar agora" e pausa).
 */
@Service
public class AcompanhamentoService {

	private static final Logger log = LoggerFactory.getLogger(AcompanhamentoService.class);

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("d 'de' MMMM", PT_BR);

	/** Quantos nomes de quem falta vão no corpo do e-mail; o resto fica no PDF e no Excel. */
	static final int PENDENTES_NO_EMAIL = 15;

	private static final EnumSet<Tipo> AO_GESTOR = EnumSet.of(Tipo.DIARIO, Tipo.FINAL);

	private final EmpresaRepository empresas;

	private final ParticipacaoService participacao;

	private final RelatorioEmpresaService relatorios;

	private final RelatorioPdfService pdf;

	private final LembreteService lembretes;

	private final DiagnosticoRepository diagnosticos;

	private final EnvioAcompanhamentoRepository envios;

	private final EmailSender email;

	private final Correio correio;

	private final EmailEnviadoRepository emailsEnviados;

	private final QuemEsta quemEsta;

	private final ITemplateEngine templates;

	private final TransactionTemplate transacao;

	private final Clock clock;

	private final String urlPublica;

	AcompanhamentoService(EmpresaRepository empresas, ParticipacaoService participacao,
			RelatorioEmpresaService relatorios, RelatorioPdfService pdf, LembreteService lembretes,
			DiagnosticoRepository diagnosticos, EnvioAcompanhamentoRepository envios, EmailSender email, Correio correio,
			EmailEnviadoRepository emailsEnviados, QuemEsta quemEsta, ITemplateEngine templates,
			TransactionTemplate transacao, Clock clock, AppProperties propriedades) {
		this.correio = correio;
		this.emailsEnviados = emailsEnviados;
		this.quemEsta = quemEsta;
		this.empresas = empresas;
		this.participacao = participacao;
		this.relatorios = relatorios;
		this.pdf = pdf;
		this.lembretes = lembretes;
		this.diagnosticos = diagnosticos;
		this.envios = envios;
		this.email = email;
		this.templates = templates;
		this.transacao = transacao;
		this.clock = clock;
		this.urlPublica = propriedades.urlPublica().replaceAll("/+$", "");
	}

	/** Resultado do "Enviar agora": o que saiu (diário ou final) ou por que não saiu. */
	public record Envio(boolean enviado, String tipo, String mensagem) {
	}

	// ================================================================== agendador

	/** Faz nesta empresa o que for a hora de fazer (nada, na maior parte das rodadas). */
	void processar(Long empresaId) {
		Instant agora = clock.instant();
		Estado estado = estado(empresaId, agora);
		for (Acao acao : Agenda.decidir(estado, agora)) {
			switch (acao) {
				case REABRIR_FINAL -> alterar(empresaId, e -> e.registrarFinal(null));
				case FINAL -> enviar(empresaId, true, false, null);
				case DIARIO -> enviar(empresaId, false, false, null);
				case LEMBRETE_3_DIAS, LEMBRETE_1_DIA -> lembrar(empresaId, acao, estado.encerraEm(), agora);
			}
		}
	}

	private Estado estado(Long empresaId, Instant agora) {
		ResumoParticipacao r = participacao.resumo(empresaId);
		Instant inicioDoDia = agora.atZone(Datas.FUSO).toLocalDate().atStartOfDay(Datas.FUSO).toInstant();
		return transacao.execute(status -> {
			Empresa e = empresa(empresaId);
			int concluidos = (int) diagnosticos.countByEmpresaIdAndStatusAndInativadoEmIsNull(empresaId,
					StatusDiagnostico.CONCLUIDO);
			int falhasHoje = (int) envios.countByEmpresaIdAndTipoInAndEnviadoFalseAndQuandoGreaterThanEqual(empresaId,
					AO_GESTOR, inicioDoDia);
			return new Estado(email.ativo(), !e.getGestores().isEmpty(), e.isGestorPausado(), r.total(),
					r.responderam(), r.convidados(), concluidos, e.getEncerraEm(), e.getGestorDiarioEm(),
					e.getGestorFinalEm(), e.getLembrete3DiasEm(), e.getLembrete1DiaEm(), falhasHoje);
		});
	}

	// ================================================================== painel

	/** "Enviar agora": o relatório do dia, ou o final se a avaliação já chegou ao fim. */
	public Envio enviarAgora(Long empresaId) {
		if (!email.ativo()) {
			throw RegraNegocioException
				.conflito("Este servidor está com o envio de e-mail desligado: nada sai até ele ser ligado.");
		}
		Instant agora = clock.instant();
		Estado estado = estado(empresaId, agora);
		if (!estado.temGestores()) {
			throw RegraNegocioException.conflito("Cadastre o e-mail de pelo menos um gestor na edição da empresa.");
		}
		return enviar(empresaId, estado.fim(agora), true, quemEsta.nome());
	}

	/** O e-mail que sairia agora (o final, se a avaliação já chegou ao fim), sem anexos. */
	public String previa(Long empresaId) {
		Instant agora = clock.instant();
		return render(montar(empresaId, estado(empresaId, agora).fim(agora), false, agora).modelo());
	}

	public void pausar(Long empresaId, boolean pausado) {
		alterar(empresaId, e -> e.pausarGestor(pausado));
	}

	public PainelAcompanhamento painel(Long empresaId) {
		Instant agora = clock.instant();
		Estado estado = estado(empresaId, agora);
		return transacao.execute(status -> {
			Empresa e = empresa(empresaId);
			var lista = envios.findTop30ByEmpresaIdOrderByQuandoDescIdDesc(empresaId);
			Map<Long, EmailEnviado> emails = new HashMap<>();
			emailsEnviados
				.findAllById(lista.stream().map(EnvioAcompanhamento::getEmailId).filter(Objects::nonNull).toList())
				.forEach(m -> emails.put(m.getId(), m));
			var historico = lista.stream()
				.map(h -> PainelAcompanhamento.Item.de(h, h.getEmailId() == null ? null : emails.get(h.getEmailId())))
				.toList();
			return PainelAcompanhamento.montar(e, estado, agora, lembretes(e, estado, agora), historico);
		});
	}

	private PainelAcompanhamento.Lembretes lembretes(Empresa e, Estado estado, Instant agora) {
		if (e.getEncerraEm() == null) {
			return null;
		}
		return new PainelAcompanhamento.Lembretes(etapa(e.getLembrete3DiasEm(), e.getEncerraEm(), 3, estado, agora),
				etapa(e.getLembrete1DiaEm(), e.getEncerraEm(), 1, estado, agora));
	}

	/** Como está cada lembrete, numa frase para o painel. */
	private static String etapa(Instant enviadoEm, Instant prazo, int dias, Estado estado, Instant agora) {
		if (enviadoEm != null) {
			return "Enviado em " + Datas.porExtenso(enviadoEm, agora) + ".";
		}
		if (estado.encerrada(agora)) {
			return "Não saiu: a avaliação já encerrou.";
		}
		LocalDate dia = prazo.atZone(Datas.FUSO).toLocalDate().minusDays(dias);
		Instant previsto = dia.atTime(Agenda.INICIO).atZone(Datas.FUSO).toInstant();
		if (dias == 3 && Agenda.diasAte(prazo, agora) <= 1) {
			return "Não vai sair: o prazo já está a 1 dia ou menos, e vale o lembrete de 1 dia.";
		}
		String quando = previsto.isAfter(agora) ? "Sai em " + Datas.porExtenso(previsto, agora)
				: "Sai na próxima rodada, a partir das 8h";
		return quando + (estado.comecou() ? "." : ", se os convites já tiverem saído.");
	}

	// ================================================================== envio ao gestor

	private Envio enviar(Long empresaId, boolean fim, boolean manual, String autor) {
		Instant agora = clock.instant();
		Tipo tipo = fim ? Tipo.FINAL : Tipo.DIARIO;
		List<String> para = transacao.execute(status -> empresa(empresaId).getGestores());
		Montado m = null;
		try {
			m = montar(empresaId, fim, true, agora);
			Long emailId = correio.enviar(new EmailSender.Mensagem(para, m.assunto(), render(m.modelo()), m.anexos()),
					new Correio.Origem(fim ? EmailEnviado.Tipo.GESTOR_FINAL : EmailEnviado.Tipo.GESTOR_DIARIO, empresaId, null,
							null));
			Montado enviado = m;
			transacao.executeWithoutResult(status -> {
				Empresa e = empresa(empresaId);
				if (fim) {
					e.registrarFinal(agora);
				}
				else {
					e.registrarDiario(agora);
				}
				envios.save(new EnvioAcompanhamento(empresaId, tipo, manual, agora, String.join(", ", para),
						enviado.responderam(), enviado.total(), true, enviado.assunto(), autor, emailId));
			});
			log.info("Relatório {} da empresa {} enviado para {} gestor(es)", tipo, empresaId, para.size());
			String aQuem = para.size() == 1 ? "ao gestor" : "aos " + para.size() + " gestores";
			return new Envio(true, tipo.name(), fim ? "O relatório final foi enviado " + aQuem + "."
					: "O relatório de participação foi enviado " + aQuem + ".");
		}
		catch (EmailSender.FalhaEnvio e) {
			log.warn("O relatório {} da empresa {} não saiu: {}", tipo, empresaId, e.getMessage());
			return falhou(empresaId, tipo, manual, agora, para, m, e.getMessage(), autor);
		}
		catch (RuntimeException e) {
			log.error("Falha ao montar ou enviar o relatório {} da empresa {}", tipo, empresaId, e);
			return falhou(empresaId, tipo, manual, agora, para, m,
					"Erro inesperado ao montar o relatório: " + (e.getMessage() == null ? e.getClass().getSimpleName()
							: e.getMessage()), autor);
		}
	}

	private Envio falhou(Long empresaId, Tipo tipo, boolean manual, Instant agora, List<String> para, Montado m,
			String motivo, String autor) {
		envios.save(new EnvioAcompanhamento(empresaId, tipo, manual, agora, String.join(", ", para),
				m == null ? null : m.responderam(), m == null ? null : m.total(), false, motivo, autor, null));
		return new Envio(false, tipo.name(), motivo);
	}

	/** O e-mail pronto: assunto, modelo do template e anexos (só quando vai sair de verdade). */
	private record Montado(String assunto, GestorEmail modelo, List<EmailSender.Anexo> anexos, int responderam,
			int total) {
	}

	private Montado montar(Long empresaId, boolean fim, boolean comAnexos, Instant agora) {
		ResumoParticipacao r = participacao.resumo(empresaId);
		record Marca(String nome, Instant encerraEm, boolean encerrada, ConviteEmail.Logo logo, PaletaMarca.Tema tema) {
		}
		Marca marca = transacao.execute(status -> {
			Empresa e = empresa(empresaId);
			return new Marca(e.getNome(), e.getEncerraEm(), e.encerrada(agora), ConviteEmail.logoDe(e, urlPublica),
					MarcaEmpresa.temaDe(e));
		});
		List<EmailSender.Anexo> anexos = new ArrayList<>();
		List<String> nomesAnexos = new ArrayList<>();

		GestorEmail.Consolidado consolidado = null;
		String aviso = null;
		if (fim) {
			try {
				RelatorioEmpresaDados dados = consolidado(empresaId, r, marca.encerrada());
				consolidado = new GestorEmail.Consolidado(dados.indice().media(),
						dados.faixaDaMedia() == null ? null : dados.faixaDaMedia().titulo(), corDaFaixa(dados.faixaDaMedia()),
						dados.lideres());
				if (comAnexos) {
					anexos.add(new EmailSender.Anexo(dados.nomeArquivo(), pdf.gerar(dados)));
				}
				nomesAnexos.add("Relatório consolidado (PDF)");
			}
			catch (RegraNegocioException e) {
				// Menos líderes que o mínimo: o consolidado identificaria alguém. Vai só a participação.
				aviso = e.getBody().getDetail();
			}
		}
		if (r.total() > 0) {
			if (comAnexos) {
				var relatorioPdf = participacao.pdf(empresaId);
				var planilha = participacao.planilha(empresaId);
				anexos.add(new EmailSender.Anexo(relatorioPdf.nome(), relatorioPdf.conteudo()));
				anexos.add(new EmailSender.Anexo(planilha.nome(), planilha.conteudo()));
			}
			nomesAnexos.add("Relatório de participação (PDF)");
			nomesAnexos.add("Planilha de participação (Excel)");
		}

		Integer anterior = fim ? null
				: envios.findFirstByEmpresaIdAndTipoInAndEnviadoTrueOrderByQuandoDescIdDesc(empresaId, AO_GESTOR)
					.map(EnvioAcompanhamento::getResponderam)
					.orElse(null);
		Textos t = fim ? textosFinais(marca.nome(), r, marca.encerraEm(), marca.encerrada(), agora)
				: textosDiarios(marca.nome(), r, anterior, marca.encerraEm(), agora);

		List<GestorEmail.Parte> placar = new ArrayList<>();
		parte(placar, "Responderam", r.responderam(), r.total(), "#1F8A5B");
		parte(placar, "Abriram e não terminaram", r.andamento(), r.total(), "#C27C0B");
		parte(placar, "Não abriram o link", r.naoAbriram(), r.total(), "#A9B8B2");

		var pendentes = fim ? List.<ParticipacaoService.Pendente>of()
				: r.pendentes().stream().limit(PENDENTES_NO_EMAIL).toList();
		int outros = fim ? 0 : Math.max(0, r.pendentes().size() - PENDENTES_NO_EMAIL);
		String rodape = (fim ? "Este é o último relatório automático desta avaliação. "
				: "Você recebe este relatório todo dia útil às 8h, até a avaliação terminar. ")
				+ "Ele vai para quem está cadastrado como gestor da " + marca.nome()
				+ " no Diagnóstico do Líder. As respostas de cada líder nunca são mostradas à empresa.";

		var modelo = new GestorEmail(marca.nome(), MarcaEmpresa.siglaDe(marca.nome()), marca.logo(), marca.tema(), fim,
				t.rotulo(), t.titulo(), t.subtitulo(), t.novidade(), t.prazo(), r.total() > 0 ? placar : List.of(),
				pendentes, outros, fim ? List.of() : r.devolvidos(), consolidado, aviso, nomesAnexos, rodape);
		return new Montado(t.assunto(), modelo, anexos, r.responderam(), r.total());
	}

	/** O consolidado, com "todos responderam" no lugar de "resultado parcial" quando for o caso. */
	private RelatorioEmpresaDados consolidado(Long empresaId, ResumoParticipacao r, boolean encerrada) {
		return r.todosResponderam() && !encerrada ? relatorios.montarComTodosRespondidos(empresaId, r.total())
				: relatorios.montar(empresaId);
	}

	private static String corDaFaixa(RelatorioEmpresaDados.FaixaResumo faixa) {
		if (faixa == null) {
			return "#0F2B26";
		}
		return switch (faixa.codigo()) {
			case "TRAVADA" -> "#C2364B";
			case "PLATO" -> "#A86A00";
			case "ASCENSAO" -> "#1F8A5B";
			default -> "#0F2B26";
		};
	}

	private static void parte(List<GestorEmail.Parte> placar, String rotulo, int quantidade, int total, String cor) {
		if (quantidade > 0) {
			placar.add(new GestorEmail.Parte(rotulo, quantidade, total == 0 ? 0 : (int) Math.round(quantidade * 100.0 / total),
					cor));
		}
	}

	record Textos(String assunto, String rotulo, String titulo, String subtitulo, String novidade, String prazo) {
	}

	static Textos textosDiarios(String empresa, ResumoParticipacao r, Integer anterior, Instant encerraEm,
			Instant agora) {
		int novos = anterior == null ? -1 : Math.max(0, r.responderam() - anterior);
		String novidade = novos < 0 ? null
				: novos == 0 ? "Nenhuma resposta nova desde o último relatório."
						: novos == 1 ? "+1 resposta desde o último relatório." : "+" + novos + " respostas desde o último relatório.";
		String curta = novos < 0 ? r.percentual() + "%" : novos == 0 ? "nenhuma resposta nova" : "+" + novos + " desde o último";
		String titulo = "%d de %d %s".formatted(r.responderam(), r.total(), r.total() == 1 ? "líder respondeu" : "líderes responderam");
		int faltam = r.faltam();
		String subtitulo = faltam == 1 ? "Falta 1 líder. A lista de quem falta, com o link pessoal de cada um, está no PDF."
				: "Faltam %d líderes. A lista de quem falta, com o link pessoal de cada um, está no PDF.".formatted(faltam);
		return new Textos("%s: %d de %d líderes responderam (%s)".formatted(empresa, r.responderam(), r.total(), curta),
				"Acompanhamento da participação · " + DIA.format(agora.atZone(Datas.FUSO)), titulo, subtitulo, novidade,
				prazo(encerraEm, agora));
	}

	static Textos textosFinais(String empresa, ResumoParticipacao r, Instant encerraEm, boolean encerrada,
			Instant agora) {
		String rotulo = "Resultado final · " + DIA.format(agora.atZone(Datas.FUSO));
		if (r.todosResponderam() && !encerrada) {
			String titulo = r.total() == 1 ? "O líder convidado respondeu" : "Todos os %d líderes responderam".formatted(r.total());
			return new Textos("%s: todos os líderes responderam. Veja o resultado".formatted(empresa), rotulo, titulo,
					"A participação está completa. Em anexo, o resultado da " + empresa + ".", null,
					encerraEm == null ? null : "Todos responderam antes do prazo (" + Datas.porExtenso(encerraEm, agora) + ").");
		}
		String titulo = r.total() == 0 ? "A avaliação terminou"
				: "A avaliação terminou: %d de %d %s".formatted(r.responderam(), r.total(),
						r.total() == 1 ? "líder respondeu" : "líderes responderam");
		return new Textos("%s: avaliação encerrada. Veja o resultado".formatted(empresa), rotulo, titulo,
				"Em anexo, o resultado final da " + empresa + ".", null,
				encerraEm == null ? null : "O prazo terminou em " + Datas.porExtenso(encerraEm, agora) + ".");
	}

	private static String prazo(Instant encerraEm, Instant agora) {
		if (encerraEm == null) {
			return null;
		}
		long dias = Agenda.diasAte(encerraEm, agora);
		String falta = dias <= 0 ? "encerra hoje" : dias == 1 ? "falta 1 dia" : "faltam " + dias + " dias";
		return "A avaliação vai até " + Datas.porExtenso(encerraEm, agora) + ": " + falta + ".";
	}

	private String render(GestorEmail modelo) {
		var contexto = new Context(PT_BR);
		contexto.setVariable("g", modelo);
		return templates.process("email/gestor", contexto);
	}

	// ================================================================== lembretes

	private void lembrar(Long empresaId, Acao acao, Instant prazo, Instant agora) {
		String chamada = Agenda.chamada(Agenda.diasAte(prazo, agora));
		ResultadoLembrete resultado = lembretes.lembrar(empresaId, chamada);
		transacao.executeWithoutResult(status -> {
			Empresa e = empresa(empresaId);
			if (acao == Acao.LEMBRETE_1_DIA) {
				e.registrarLembrete1Dia(agora);
			}
			else {
				e.registrarLembrete3Dias(agora);
			}
			int tentados = resultado.enviados() + resultado.falhas();
			if (tentados > 0) {
				String detalhe = chamada + (resultado.falhas() == 0 ? "."
						: ". %d não %s: %s".formatted(resultado.falhas(), resultado.falhas() == 1 ? "saiu" : "saíram",
								resultado.motivo()));
				envios.save(new EnvioAcompanhamento(empresaId, Tipo.LEMBRETE, false, agora,
						resultado.enviados() == 1 ? "1 líder" : resultado.enviados() + " líderes", null, null,
						resultado.enviados() > 0, detalhe));
			}
		});
		log.info("Lembrete \"{}\" da empresa {}: {} enviados, {} falhas", chamada, empresaId, resultado.enviados(),
				resultado.falhas());
	}

	// ================================================================== apoio

	private void alterar(Long empresaId, Consumer<Empresa> mudanca) {
		transacao.executeWithoutResult(status -> mudanca.accept(empresa(empresaId)));
	}

	private Empresa empresa(Long id) {
		return empresas.findById(id).orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
	}

}
