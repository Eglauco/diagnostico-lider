package br.com.diagnosticolider.convite;

import java.text.Collator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.notificacao.EmailEnviado;
import br.com.diagnosticolider.notificacao.EmailEnviadoRepository;
import br.com.diagnosticolider.campanha.CodigoCampanha;
import br.com.diagnosticolider.compartilhado.Datas;
import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.convite.RelatorioParticipacao.Bloco;
import br.com.diagnosticolider.convite.RelatorioParticipacao.Grupo;
import br.com.diagnosticolider.convite.RelatorioParticipacao.Lider;
import br.com.diagnosticolider.convite.RelatorioParticipacao.Linha;
import br.com.diagnosticolider.convite.RelatorioParticipacao.Pagina;
import br.com.diagnosticolider.convite.RelatorioParticipacao.Parte;
import br.com.diagnosticolider.convite.RelatorioParticipacao.Placar;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.empresa.EmpresaRepository;
import br.com.diagnosticolider.empresa.LogomarcaService;
import br.com.diagnosticolider.empresa.MarcaEmpresa;
import br.com.diagnosticolider.relatorio.RelatorioPdfService;

/**
 * Relatório de participação (PDF e Excel) para mandar à empresa: quem falta responder, com o
 * link pessoal de cada um, e quem já respondeu. Os inativos ficam de fora, como nos cálculos.
 */
@Service
public class ParticipacaoService {

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private static final DateTimeFormatter DATA_HORA = DateTimeFormatter
		.ofPattern("d 'de' MMMM 'de' yyyy, 'às' H'h'mm", PT_BR);

	private static final DateTimeFormatter DATA_ARQUIVO = DateTimeFormatter.ISO_LOCAL_DATE;

	// Paginação do PDF, em milímetros: cada peça tem altura fixa no template (participacao.html).
	static final double ALTURA_PRIMEIRA = 162;

	static final double ALTURA_PAGINA = 244;

	static final double ALTURA_GRUPO = 19;

	static final double ALTURA_LINHA = 12.5;

	private final ConviteRepository convites;

	private final EmpresaRepository empresas;

	private final ConviteService conviteService;

	private final LogomarcaService logomarcas;

	private final RelatorioPdfService pdf;

	private final PlanilhaParticipacao planilha;

	private final Clock clock;

	private final EventoConviteRepository eventos;

	private final EmailEnviadoRepository emailsEnviados;

	private final BloqueioDeEnvio bloqueios;

	ParticipacaoService(ConviteRepository convites, EmpresaRepository empresas, ConviteService conviteService,
			LogomarcaService logomarcas, RelatorioPdfService pdf, PlanilhaParticipacao planilha, Clock clock,
			EventoConviteRepository eventos, EmailEnviadoRepository emailsEnviados, BloqueioDeEnvio bloqueios) {
		this.bloqueios = bloqueios;
		this.eventos = eventos;
		this.emailsEnviados = emailsEnviados;
		this.convites = convites;
		this.empresas = empresas;
		this.conviteService = conviteService;
		this.logomarcas = logomarcas;
		this.pdf = pdf;
		this.planilha = planilha;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Arquivo pdf(Long empresaId) {
		RelatorioParticipacao r = montar(empresaId);
		return new Arquivo(r.nomeArquivo() + ".pdf", pdf.gerar("relatorio/participacao", r));
	}

	@Transactional(readOnly = true)
	public String html(Long empresaId) {
		return pdf.html("relatorio/participacao", montar(empresaId));
	}

	@Transactional(readOnly = true)
	public Arquivo planilha(Long empresaId) {
		Empresa empresa = empresa(empresaId);
		Instant agora = clock.instant();
		List<Convite> todos = convites.daEmpresa(empresaId);
		int inativos = (int) todos.stream().filter(Convite::inativo).count();
		List<Lider> lideres = lideres(todos);
		byte[] conteudo = planilha.gerar(empresa.getNome(), atualizadoEm(agora), prazo(empresa, agora), lideres,
				inativos, auditoria(empresaId));
		return new Arquivo(nomeArquivo(empresa, agora) + ".xlsx", conteudo);
	}

	public record Arquivo(String nome, byte[] conteudo) {
	}

	/**
	 * Os números do relatório para o corpo do e-mail ao gestor. total: líderes ativos.
	 * convidados: quantos já receberam o convite (a avaliação "começou" quando passa de zero).
	 * pendentes: quem falta, na ordem do relatório (mais perto de terminar primeiro).
	 */
	public record ResumoParticipacao(int total, int responderam, int andamento, int naoAbriram, int convidados,
			List<Pendente> pendentes, List<Devolvido> devolvidos) {

		public boolean todosResponderam() {
			return total > 0 && responderam == total;
		}

		public int faltam() {
			return total - responderam;
		}

		public int percentual() {
			return pct(responderam, total);
		}

	}

	public record Pendente(String nome, String cargo, String situacao) {
	}

	/** O e-mail do líder voltou ou foi para o spam: o RH precisa conferir o endereço. */
	public record Devolvido(String nome, String email, String motivo) {
	}

	@Transactional(readOnly = true)
	public ResumoParticipacao resumo(Long empresaId) {
		List<Convite> todos = convites.daEmpresa(empresaId);
		List<Lider> lideres = lideres(todos);
		Map<SituacaoParticipacao, Long> conta = lideres.stream()
			.collect(Collectors.groupingBy(Lider::situacao, Collectors.counting()));
		int convidados = (int) todos.stream().filter(c -> !c.inativo() && c.getEnviadoEm() != null).count();
		List<Pendente> pendentes = lideres.stream()
			.filter(l -> l.situacao().pendente())
			.map(l -> new Pendente(l.nome(), l.cargo(), l.situacao().rotulo()))
			.toList();
		Map<Long, EventoConvite> problemas = conviteService.problemasDeEntrega(todos);
		List<Devolvido> devolvidos = todos.stream().filter(c -> {
			EventoConvite p = problemas.get(c.getId());
			return p != null && p.getTipo() != TipoEvento.FALHA_ENVIO && !c.inativo()
					&& c.status() != StatusConvite.CONCLUIDO
					&& (c.getEnviadoEm() == null || p.getQuando().isAfter(c.getEnviadoEm()));
		}).map(c -> new Devolvido(c.getNome(), c.getEmail(), motivo(problemas.get(c.getId()).getDetalhe()))).toList();
		return new ResumoParticipacao(lideres.size(), conta.getOrDefault(SituacaoParticipacao.RESPONDEU, 0L).intValue(),
				conta.getOrDefault(SituacaoParticipacao.COMECOU, 0L).intValue()
						+ conta.getOrDefault(SituacaoParticipacao.ABRIU, 0L).intValue(),
				conta.getOrDefault(SituacaoParticipacao.NAO_ABRIU, 0L).intValue(), convidados, pendentes, devolvidos);
	}

	/** "O convite não chegou. O endereço não existe…" vira "O endereço não existe…". */
	private static String motivo(String detalhe) {
		if (detalhe == null) {
			return "O e-mail não chegou.";
		}
		int ponto = detalhe.indexOf(". ");
		return ponto < 0 ? detalhe : detalhe.substring(ponto + 2);
	}

	/** A linha do tempo de todos os líderes da empresa, com a entrega de cada e-mail. */
	private List<PlanilhaParticipacao.LinhaAuditoria> auditoria(Long empresaId) {
		List<EventoConvite> lista = eventos.daEmpresa(empresaId);
		Map<Long, EmailEnviado> emails = new HashMap<>();
		emailsEnviados.findAllById(lista.stream().map(EventoConvite::getEmailId).filter(Objects::nonNull).toList())
			.forEach(e -> emails.put(e.getId(), e));
		return lista.stream().map(e -> {
			Convite c = e.getConvite();
			EmailEnviado email = e.getEmailId() == null ? null : emails.get(e.getEmailId());
			return new PlanilhaParticipacao.LinhaAuditoria(c.getNome(), c.getEmail(), e.getQuando(), e.getTipo().rotulo(),
					e.getDetalhe(), e.getAutor(), email == null ? null : entrega(email));
		}).toList();
	}

	private static String entrega(EmailEnviado email) {
		return switch (email.getSituacao()) {
			case ENVIADO -> "Enviado (sem aviso de entrega)";
			case ENTREGUE -> "Entregue";
			case ATRASADO -> "Atrasado";
			case DEVOLVIDO -> "Voltou";
			case SPAM -> "Marcado como spam";
			case BLOQUEADO -> "Bloqueado";
			case FALHOU -> "Não saiu";
		};
	}

	// ------------------------------------------------------------------ montagem

	RelatorioParticipacao montar(Long empresaId) {
		Empresa empresa = empresa(empresaId);
		Instant agora = clock.instant();
		List<Convite> todos = convites.daEmpresa(empresaId);
		int inativos = (int) todos.stream().filter(Convite::inativo).count();
		List<Lider> lideres = lideres(todos);

		Map<SituacaoParticipacao, List<Lider>> porSituacao = lideres.stream()
			.collect(Collectors.groupingBy(Lider::situacao));
		int total = lideres.size();
		int responderam = porSituacao.getOrDefault(SituacaoParticipacao.RESPONDEU, List.of()).size();
		int andamento = porSituacao.getOrDefault(SituacaoParticipacao.COMECOU, List.of()).size()
				+ porSituacao.getOrDefault(SituacaoParticipacao.ABRIU, List.of()).size();
		int naoAbriram = porSituacao.getOrDefault(SituacaoParticipacao.NAO_ABRIU, List.of()).size();
		Placar placar = new Placar(total, List.of(
				new Parte("respondeu", "Responderam", responderam, pct(responderam, total)),
				new Parte("andamento", "Abriram e não terminaram", andamento, pct(andamento, total)),
				new Parte("nao-abriu", "Não abriram o link", naoAbriram, pct(naoAbriram, total))));

		boolean encerrada = empresa.encerrada(agora);
		int faltam = total - responderam;
		String rotulo = encerrada ? "Participação final" : "Acompanhamento da participação";
		String titulo;
		String subtitulo;
		if (total == 0) {
			titulo = "Nenhum líder na lista ainda";
			subtitulo = "Importe a planilha ou adicione os líderes no painel.";
		}
		else if (encerrada) {
			titulo = "%d de %d %s".formatted(responderam, total, total == 1 ? "líder respondeu" : "líderes responderam");
			subtitulo = faltam == 0 ? "Participação completa." : faltam + (faltam == 1 ? " não respondeu." : " não responderam.");
		}
		else if (faltam == 0) {
			titulo = total == 1 ? "O líder convidado respondeu" : "Todos os %d líderes responderam".formatted(total);
			subtitulo = "Participação completa: ninguém para cobrar.";
		}
		else {
			titulo = faltam == 1 ? "1 líder ainda não respondeu" : "%d líderes ainda não responderam".formatted(faltam);
			subtitulo = "%d de %d já %s (%d%%).".formatted(responderam, total, responderam == 1 ? "respondeu" : "responderam",
					pct(responderam, total));
		}

		List<Grupo> grupos = new ArrayList<>();
		List<List<Linha>> linhas = new ArrayList<>();
		for (SituacaoParticipacao s : SituacaoParticipacao.values()) {
			List<Lider> doGrupo = porSituacao.getOrDefault(s, List.of());
			if (!doGrupo.isEmpty()) {
				grupos.add(new Grupo(s.name().toLowerCase(Locale.ROOT).replace('_', '-'), s.titulo(), s.dica(),
						doGrupo.size()));
				linhas.add(doGrupo.stream().map(l -> linha(l, agora)).toList());
			}
		}

		MarcaEmpresa.Logo logo = logomarcas.logo(empresa);
		return new RelatorioParticipacao(empresa.getNome(), MarcaEmpresa.siglaDe(empresa.getNome()),
				logomarcas.dataUri(empresa), logo != null && logo.pedeNome(), empresa.tema(), rotulo, titulo, subtitulo,
				atualizadoEm(agora), prazo(empresa, agora), encerrada, placar, paginar(grupos, linhas), inativos,
				nomeArquivo(empresa, agora));
	}

	/** Os líderes ativos, na ordem do relatório: situação (quem está mais perto de terminar primeiro) e nome. */
	private List<Lider> lideres(List<Convite> todos) {
		Collator alfabetica = Collator.getInstance(PT_BR);
		alfabetica.setStrength(Collator.PRIMARY);
		var bloqueados = bloqueios.de(todos);
		return todos.stream()
			.map(c -> lider(c, bloqueados.get(c.getId())))
			.filter(Objects::nonNull)
			.sorted(Comparator.comparing(Lider::situacao).thenComparing(Lider::nome, alfabetica))
			.toList();
	}

	private Lider lider(Convite c, BloqueioDeEnvio.Bloqueio bloqueio) {
		SituacaoParticipacao situacao = SituacaoParticipacao.de(c.status());
		if (situacao == null) {
			return null;
		}
		String cargo = c.isCargoOutro() ? PlanilhaConvites.OUTRO_CARGO : c.getCargo() == null ? null : c.getCargo().getNome();
		Instant respondeuEm = c.getDiagnostico() == null ? null : c.getDiagnostico().getConcluidoEm();
		return new Lider(c.getNome(), cargo, c.getEmail(), c.getWhatsapp(), situacao, c.getEnviadoEm(), c.getEnvios(),
				c.getPrimeiroAcessoEm(), c.getUltimoAcessoEm(), c.getAcessos(), respondeuEm,
				situacao.pendente() ? conviteService.link(c) : null,
				bloqueio == null || !situacao.pendente() ? null : switch (bloqueio.motivo()) {
					case DEVOLVIDO -> "O e-mail voltou";
					case SPAM -> "Marcou o convite como spam";
					case BLOQUEADO -> "E-mail bloqueado";
				});
	}

	private static Linha linha(Lider l, Instant agora) {
		String contato = l.whatsapp() == null || l.whatsapp().isBlank() ? l.email() : l.email() + "  ·  " + l.whatsapp();
		String detalhe = switch (l.situacao()) {
			case RESPONDEU -> l.respondeuEm() == null ? null : "Respondeu em " + Datas.porExtenso(l.respondeuEm(), agora);
			case COMECOU -> l.ultimoAcessoEm() == null ? null
					: "Último acesso em " + Datas.porExtenso(l.ultimoAcessoEm(), agora);
			case ABRIU -> l.primeiroAcessoEm() == null ? null
					: "Abriu em " + Datas.porExtenso(l.primeiroAcessoEm(), agora)
							+ (l.acessos() > 1 ? " · " + l.acessos() + " vezes" : "");
			case NAO_ABRIU -> l.enviadoEm() == null ? null
					: "Convite enviado em " + Datas.porExtenso(l.enviadoEm(), agora)
							+ (l.envios() > 1 ? " · " + l.envios() + " envios" : "");
		};
		if (l.alerta() != null) {
			// Sem e-mail que chegue: o RH precisa mandar o link por outro canal.
			detalhe = l.alerta() + ": mande o link por WhatsApp";
		}
		String visivel = l.link() == null ? null : l.link().replaceFirst("^https?://", "");
		return new Linha(l.nome(), l.cargo(), contato, detalhe, l.link(), visivel, l.alerta() != null);
	}

	/**
	 * Distribui os grupos nas páginas A4. Cada peça tem altura fixa, então a conta é exata: um
	 * grupo que não cabe continua na página seguinte, com o título repetido, e nunca fica só o
	 * título no pé da página.
	 */
	static List<Pagina> paginar(List<Grupo> grupos, List<List<Linha>> linhas) {
		List<Pagina> paginas = new ArrayList<>();
		List<Bloco> atual = new ArrayList<>();
		double restante = ALTURA_PRIMEIRA;
		for (int g = 0; g < grupos.size(); g++) {
			List<Linha> fila = linhas.get(g);
			boolean continuacao = false;
			while (!fila.isEmpty()) {
				if (restante < ALTURA_GRUPO + ALTURA_LINHA) {
					paginas.add(new Pagina(paginas.size() + 1, atual));
					atual = new ArrayList<>();
					restante = ALTURA_PAGINA;
				}
				int cabem = (int) Math.floor((restante - ALTURA_GRUPO) / ALTURA_LINHA);
				int pega = Math.min(cabem, fila.size());
				atual.add(new Bloco(grupos.get(g), continuacao, fila.subList(0, pega)));
				restante -= ALTURA_GRUPO + pega * ALTURA_LINHA;
				fila = fila.subList(pega, fila.size());
				continuacao = true;
			}
		}
		paginas.add(new Pagina(paginas.size() + 1, atual));
		return paginas;
	}

	// ------------------------------------------------------------------ textos

	private static String atualizadoEm(Instant agora) {
		return "Atualizado em " + DATA_HORA.format(agora.atZone(Datas.FUSO)).replace("h00", "h");
	}

	private static String prazo(Empresa empresa, Instant agora) {
		Instant encerraEm = empresa.getEncerraEm();
		if (encerraEm == null) {
			return null;
		}
		String quando = Datas.porExtenso(encerraEm, agora);
		if (empresa.encerrada(agora)) {
			return "Avaliação encerrada em " + quando + ". Os links pessoais estão fechados.";
		}
		long dias = ChronoUnit.DAYS.between(LocalDate.ofInstant(agora, Datas.FUSO), LocalDate.ofInstant(encerraEm, Datas.FUSO));
		String falta = dias <= 0 ? "encerra hoje" : dias == 1 ? "falta 1 dia" : "faltam " + dias + " dias";
		return "A avaliação vai até " + quando + ": " + falta + ".";
	}

	private static String nomeArquivo(Empresa empresa, Instant agora) {
		return "participacao-" + CodigoCampanha.normalizar(empresa.getNome()) + "-"
				+ DATA_ARQUIVO.format(agora.atZone(Datas.FUSO));
	}

	private static int pct(int parte, int total) {
		return total == 0 ? 0 : (int) Math.round(parte * 100.0 / total);
	}

	private Empresa empresa(Long id) {
		return empresas.findById(id).orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
	}

}
