package br.com.diagnosticolider.convite;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.acesso.QuemEsta;
import br.com.diagnosticolider.campanha.Campanha;
import br.com.diagnosticolider.campanha.CampanhaRepository;
import br.com.diagnosticolider.campanha.CampanhaService;
import br.com.diagnosticolider.compartilhado.Datas;
import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.compartilhado.Emails;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import br.com.diagnosticolider.compartilhado.Telefones;
import br.com.diagnosticolider.config.AppProperties;
import br.com.diagnosticolider.convite.ConviteDtos.CargoOpcao;
import br.com.diagnosticolider.convite.ConviteDtos.ConviteItem;
import br.com.diagnosticolider.convite.ConviteDtos.ConvitePublico;
import br.com.diagnosticolider.convite.ConviteDtos.Convites;
import br.com.diagnosticolider.convite.ConviteDtos.Criados;
import br.com.diagnosticolider.convite.ConviteDtos.CriarConvites;
import br.com.diagnosticolider.convite.ConviteDtos.Entrega;
import br.com.diagnosticolider.convite.ConviteDtos.Envio;
import br.com.diagnosticolider.convite.ConviteDtos.EnvioFeito;
import br.com.diagnosticolider.convite.ConviteDtos.EnvioIniciado;
import br.com.diagnosticolider.convite.ConviteDtos.Evento;
import br.com.diagnosticolider.convite.ConviteDtos.Importacao;
import br.com.diagnosticolider.convite.ConviteDtos.LinhaImportada;
import br.com.diagnosticolider.convite.ConviteDtos.NovaTentativa;
import br.com.diagnosticolider.convite.ConviteDtos.NovoConvite;
import br.com.diagnosticolider.convite.ConviteDtos.Resumo;
import br.com.diagnosticolider.diagnostico.Diagnostico;
import br.com.diagnosticolider.diagnostico.DiagnosticoConcluido;
import br.com.diagnosticolider.diagnostico.DiagnosticoDtos.DiagnosticoDto;
import br.com.diagnosticolider.diagnostico.DiagnosticoIniciadoPeloLink;
import br.com.diagnosticolider.diagnostico.DiagnosticoDtos.IniciarRequest;
import br.com.diagnosticolider.diagnostico.DiagnosticoRepository;
import br.com.diagnosticolider.diagnostico.DiagnosticoService;
import br.com.diagnosticolider.diagnostico.Respondente;
import br.com.diagnosticolider.empresa.Cargo;
import br.com.diagnosticolider.empresa.CargoService;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.empresa.EmpresaRepository;
import br.com.diagnosticolider.notificacao.EmailEnviado;
import br.com.diagnosticolider.notificacao.EmailEnviadoRepository;
import br.com.diagnosticolider.notificacao.EmailSender;

/**
 * Os líderes da avaliação de cada empresa. A planilha do RH (ou o cadastro manual) vira um link
 * pessoal por líder, com os dados para ele só confirmar, e cada passo do link fica na auditoria.
 * Quem começa pelo link aberto da empresa também vira cadastro (origem LINK), ou entra no convite
 * que já tinha com o mesmo e-mail. Os convites vão no link principal da empresa (o mais antigo),
 * então os diagnósticos entram no relatório dela como os do link aberto.
 */
@Service
@Transactional(readOnly = true)
public class ConviteService {

	static final String CARGO_OUTRO = "outro";

	static final Set<TipoEvento> PROBLEMAS_DE_ENTREGA = EnumSet.of(TipoEvento.FALHA_ENVIO, TipoEvento.EMAIL_DEVOLVIDO,
			TipoEvento.EMAIL_SPAM);

	private static final String ALFABETO = "23456789abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";

	private static final int TAMANHO_TOKEN = 10;

	private final SecureRandom aleatorio = new SecureRandom();

	private final ConviteRepository convites;

	private final EventoConviteRepository eventos;

	private final CampanhaRepository campanhas;

	private final CampanhaService campanhaService;

	private final EmpresaRepository empresas;

	private final CargoService cargos;

	private final DiagnosticoService diagnosticos;

	private final DiagnosticoRepository diagnosticoRepositorio;

	private final PlanilhaConvites planilha;

	private final EnvioConvitesService envio;

	private final EmailSender email;

	private final EmailEnviadoRepository emailsEnviados;

	private final QuemEsta quemEsta;

	private final BloqueioDeEnvio bloqueios;

	private final Clock clock;

	private final String urlPublica;

	ConviteService(ConviteRepository convites, EventoConviteRepository eventos, CampanhaRepository campanhas,
			CampanhaService campanhaService, EmpresaRepository empresas, CargoService cargos,
			DiagnosticoService diagnosticos, DiagnosticoRepository diagnosticoRepositorio, PlanilhaConvites planilha,
			EnvioConvitesService envio, EmailSender email, EmailEnviadoRepository emailsEnviados, QuemEsta quemEsta,
			BloqueioDeEnvio bloqueios, Clock clock, AppProperties propriedades) {
		this.bloqueios = bloqueios;
		this.emailsEnviados = emailsEnviados;
		this.quemEsta = quemEsta;
		this.diagnosticoRepositorio = diagnosticoRepositorio;
		this.convites = convites;
		this.eventos = eventos;
		this.campanhas = campanhas;
		this.campanhaService = campanhaService;
		this.empresas = empresas;
		this.cargos = cargos;
		this.diagnosticos = diagnosticos;
		this.planilha = planilha;
		this.envio = envio;
		this.email = email;
		this.clock = clock;
		this.urlPublica = propriedades.urlPublica().replaceAll("/+$", "");
	}

	// ================================================================== painel

	public Convites listar(Long empresaId) {
		Empresa empresa = empresa(empresaId);
		List<Convite> daEmpresa = convites.daEmpresa(empresaId);
		Map<Long, EventoConvite> falhas = problemasDeEntrega(daEmpresa);
		Map<Long, BloqueioDeEnvio.Bloqueio> bloqueados = bloqueios.de(daEmpresa);
		Map<String, Diagnostico> tentativas = novasTentativas(empresaId);
		List<ConviteItem> itens = daEmpresa.stream()
			.map(c -> item(c, falhas.get(c.getId()), bloqueados.get(c.getId()), tentativas))
			.toList();
		return new Convites(email.ativo(), empresa.getEncerraEm(), empresa.encerrada(agora()), resumo(itens), itens,
				opcoes(empresaId));
	}

	/** Quantos líderes ativos a empresa tem: convidados (planilha e cadastro manual) e pelo link aberto. */
	/** concluiram: líderes do cadastro cujo diagnóstico vigente está concluído. */
	public record LideresPorOrigem(int total, int convidados, int peloLink, int concluiram) {
	}

	public LideresPorOrigem lideresPorOrigem(Long empresaId) {
		int convidados = 0;
		int peloLink = 0;
		int concluiram = 0;
		for (Object[] linha : convites.origensDaEmpresa(empresaId)) {
			int n = ((Number) linha[1]).intValue();
			concluiram += linha[2] == null ? 0 : ((Number) linha[2]).intValue();
			if (linha[0] == OrigemLider.LINK) {
				peloLink += n;
			}
			else {
				convidados += n;
			}
		}
		return new LideresPorOrigem(convidados + peloLink, convidados, peloLink, concluiram);
	}

	/** Líderes no cadastro e quantos concluíram, de cada empresa (id → [líderes, concluíram]). */
	public Map<Long, int[]> lideresPorEmpresa() {
		Map<Long, int[]> porEmpresa = new HashMap<>();
		for (Object[] linha : convites.lideresPorEmpresa()) {
			porEmpresa.put((Long) linha[0], new int[] { ((Number) linha[1]).intValue(),
					linha[2] == null ? 0 : ((Number) linha[2]).intValue() });
		}
		return porEmpresa;
	}

	/** A planilha modelo, com os cargos da empresa na lista suspensa. */
	public byte[] modelo(Long empresaId) {
		Empresa empresa = empresa(empresaId);
		return planilha.modelo(empresa.getNome(), cargos.ativos(empresaId).stream().map(Cargo::getNome).toList());
	}

	public String nomeDoModelo(Long empresaId) {
		return "convite-lideres-" + linkPrincipal(empresaId).getCodigo() + ".xlsx";
	}

	/** Lê a planilha e devolve as linhas para revisão. Não grava nada. */
	public Importacao importar(Long empresaId, String nomeArquivo, byte[] conteudo) {
		Campanha link = linkPrincipal(empresaId);
		List<Cargo> lista = cargos.ativos(empresaId);
		Set<String> jaConvidados = new HashSet<>(convites.emailsDoLink(link.getId()));
		List<LinhaImportada> linhas = planilha.ler(nomeArquivo, conteudo).stream().map(l -> {
			String cargoTexto = l.cargo();
			Cargo cargo = cargoExato(lista, cargoTexto);
			boolean outro = cargo == null && outroCargo(cargoTexto);
			Long sugestao = cargo == null && !outro && !cargoTexto.isBlank() ? sugestao(lista, cargoTexto) : null;
			String emailNormalizado = l.email().strip().toLowerCase(Locale.ROOT);
			return new LinhaImportada(l.numero(), nome(l.nome()), emailNormalizado, whatsapp(l.whatsapp()), cargoTexto,
					cargo == null ? null : cargo.getId(), outro, sugestao,
					jaConvidados.contains(emailNormalizado), l.exemplo());
		}).toList();
		return new Importacao(nomeArquivo, linhas, opcoes(empresaId));
	}

	@Transactional
	public Criados criar(Long empresaId, CriarConvites pedido) {
		Campanha link = linkPrincipal(empresaId);
		List<Cargo> lista = cargos.ativos(empresaId);
		Set<String> existentes = new HashSet<>(convites.emailsDoLink(link.getId()));
		Set<String> inativos = new HashSet<>(convites.emailsInativos(link.getId()));
		Set<String> peloLink = new HashSet<>(convites.emailsDoLinkAberto(link.getId()));
		Map<String, String> erros = new LinkedHashMap<>();
		Set<String> nestaLista = new HashSet<>();
		List<Convite.Dados> validos = new ArrayList<>();
		String autor = quemEsta.nome();
		boolean peloRh = QuemEsta.doRh(autor);
		for (int i = 0; i < pedido.convites().size(); i++) {
			try {
				Convite.Dados dados = validar(pedido.convites().get(i), lista);
				if (!nestaLista.add(dados.email())) {
					throw new RegraNegocioException("E-mail repetido nesta lista.");
				}
				if (existentes.contains(dados.email())) {
					throw new RegraNegocioException(inativos.contains(dados.email())
							? (peloRh ? "Esse líder foi retirado da avaliação: devolva à lista em vez de cadastrar de novo."
									: "Esse líder já está na lista, inativo: reative em vez de convidar de novo.")
							: peloLink.contains(dados.email())
									? "Esse líder já está na lista: começou pelo link aberto da empresa."
									: "Esse e-mail já tem convite.");
				}
				validos.add(dados);
			}
			catch (RegraNegocioException e) {
				erros.put(String.valueOf(i), e.getBody().getDetail());
			}
		}
		if (!erros.isEmpty()) {
			var erro = new RegraNegocioException(erros.size() == 1 ? "1 linha precisa de ajuste."
					: "%d linhas precisam de ajuste.".formatted(erros.size()));
			erro.getBody().setProperty("linhas", erros);
			throw erro;
		}
		Instant agora = agora();
		String origem = peloRh
				? (pedido.daPlanilha() ? "Importado da planilha pela página do RH." : "Cadastrado pela página do RH.")
				: (pedido.daPlanilha() ? "Importado da planilha." : "Adicionado no painel.");
		OrigemLider de = pedido.daPlanilha() ? OrigemLider.PLANILHA : OrigemLider.MANUAL;
		for (Convite.Dados dados : validos) {
			Convite convite = convites.save(new Convite(link, novoToken(), dados, de, agora));
			registrar(convite, TipoEvento.CRIADO, agora, origem, autor);
		}
		return new Criados(validos.size());
	}

	@Transactional
	public ConviteItem editar(Long id, NovoConvite pedido) {
		Convite convite = carregar(id);
		if (convite.inativo()) {
			throw RegraNegocioException.conflito("Este líder está inativo. Reative para corrigir os dados.");
		}
		Convite.Dados novos = validar(pedido, cargos.ativos(convite.getCampanha().getEmpresa().getId()));
		if (!novos.email().equals(convite.getEmail().toLowerCase(Locale.ROOT))
				&& convites.emailsDoLink(convite.getCampanha().getId()).contains(novos.email())) {
			throw RegraNegocioException.conflito("Esse e-mail já tem convite.").noCampo("email");
		}
		String mudancas = diferencas(convite.dados(), novos);
		if (!mudancas.isEmpty()) {
			convite.atualizar(novos);
			// Já respondeu: a correção vale também no diagnóstico (o cargo certo nos relatórios).
			Diagnostico diagnostico = convite.getDiagnostico();
			if (diagnostico != null) {
				var atual = diagnostico.getRespondente();
				diagnostico.corrigirPeloPainel(Respondente.de(novos.nome(), novos.email(),
						novos.whatsapp() == null ? atual.getWhatsapp() : novos.whatsapp(), atual.getEmpresaInformada()),
						novos.cargo(), novos.cargoOutro());
			}
			registrar(convite, TipoEvento.EDITADO, agora(),
					diagnostico == null ? mudancas : mudancas + ". Corrigido também no diagnóstico.", quemEsta.nome());
		}
		return item(convite);
	}

	/**
	 * Inativa o líder: o link fecha e ele (com o diagnóstico, se já respondeu) sai de todos os
	 * cálculos: painel, funil, relatório da empresa e média geral. Dá para reativar.
	 */
	@Transactional
	public void inativar(Long id) {
		Convite convite = carregar(id);
		if (!convite.inativo()) {
			convite.inativar(agora());
			if (convite.getDiagnostico() != null) {
				convite.getDiagnostico().inativar(agora());
			}
			registrar(convite, TipoEvento.INATIVADO, agora(), "O link fechou e o líder saiu dos cálculos.", quemEsta.nome());
		}
	}

	@Transactional
	public void reativar(Long id) {
		Convite convite = carregar(id);
		if (convite.inativo()) {
			convite.reativar();
			if (convite.getDiagnostico() != null) {
				convite.getDiagnostico().reativar();
			}
			registrar(convite, TipoEvento.REATIVADO, agora(), "O link voltou a funcionar e o líder voltou aos cálculos.",
					quemEsta.nome());
		}
	}

	/** Inativar pelo detalhe do diagnóstico (vale também para quem respondeu pelo link aberto). */
	@Transactional
	public void inativarDiagnostico(UUID id) {
		Diagnostico diagnostico = diagnosticoRepositorio.findById(id)
			.orElseThrow(() -> new NaoEncontradoException("Não encontramos este diagnóstico."));
		diagnostico.inativar(agora());
		convites.findByDiagnosticoId(id).filter(c -> !c.inativo()).ifPresent(c -> {
			c.inativar(agora());
			registrar(c, TipoEvento.INATIVADO, agora(), "Pelo detalhe do diagnóstico: o líder saiu dos cálculos.",
					quemEsta.nome());
		});
	}

	@Transactional
	public void reativarDiagnostico(UUID id) {
		Diagnostico diagnostico = diagnosticoRepositorio.findById(id)
			.orElseThrow(() -> new NaoEncontradoException("Não encontramos este diagnóstico."));
		if (diagnostico.getSubstituidoPor() != null) {
			throw RegraNegocioException.conflito(
					"O líder respondeu de novo depois desta resposta, e vale a mais recente. Para voltar a esta, inative a outra antes.");
		}
		diagnostico.reativar();
		convites.findByDiagnosticoId(id).filter(Convite::inativo).ifPresent(c -> {
			c.reativar();
			registrar(c, TipoEvento.REATIVADO, agora(), "Pelo detalhe do diagnóstico: o líder voltou aos cálculos.",
					quemEsta.nome());
		});
	}

	public List<Evento> eventos(Long id) {
		carregar(id);
		List<EventoConvite> lista = eventos.findByConviteIdOrderByQuandoAscIdAsc(id);
		Map<Long, EmailEnviado> emails = new HashMap<>();
		emailsEnviados.findAllById(lista.stream().map(EventoConvite::getEmailId).filter(Objects::nonNull).toList())
			.forEach(e -> emails.put(e.getId(), e));
		return lista.stream()
			.map(e -> new Evento(e.getTipo(), e.getQuando(), e.getDetalhe(), e.getAutor(),
					entrega(e.getEmailId() == null ? null : emails.get(e.getEmailId()))))
			.toList();
	}

	// ------------------------------------------------------------------ gestão pela página do RH

	/**
	 * O que a página do RH precisa para gerenciar os líderes: os cargos (cadastrados pelo
	 * administrador), se dá para mandar e-mail, quantos ainda não receberam o convite e quem foi
	 * retirado (podeVoltar: não tinha respondido; quem respondeu só o administrador devolve).
	 */
	public record GestaoRh(List<CargoOpcao> cargos, boolean emailAtivo, boolean encerrada, int naoEnviados,
			List<RetiradoRh> retirados) {
	}

	public record RetiradoRh(Long id, String nome, String cargo, boolean podeVoltar) {
	}

	/** Os dados de um líder para o formulário de edição do RH. */
	public record LiderEditavel(Long id, String nome, String email, String whatsapp, Long cargoId, boolean cargoOutro,
			boolean respondeu) {
	}

	@Transactional(readOnly = true)
	public GestaoRh gestaoRh(Long empresaId) {
		Convites lista = listar(empresaId);
		int naoEnviados = (int) lista.convites().stream().filter(c -> c.status() == StatusConvite.AGUARDANDO_ENVIO).count();
		List<RetiradoRh> retirados = lista.convites()
			.stream()
			.filter(c -> c.status() == StatusConvite.INATIVO)
			.map(c -> new RetiradoRh(c.id(), c.nome(), c.cargo(), c.concluidoEm() == null))
			.toList();
		return new GestaoRh(lista.cargos(), lista.emailAtivo(), lista.encerrada(), naoEnviados, retirados);
	}

	@Transactional(readOnly = true)
	public LiderEditavel liderParaRh(Long empresaId, Long id) {
		Convite c = daEmpresa(empresaId, id);
		return new LiderEditavel(c.getId(), c.getNome(), c.getEmail(), c.getWhatsapp(),
				c.getCargo() == null ? null : c.getCargo().getId(), c.isCargoOutro(), respondeu(c));
	}

	@Transactional
	public ConviteItem editarPeloRh(Long empresaId, Long id, NovoConvite pedido) {
		travarQuemRespondeu(daEmpresa(empresaId, id));
		return editar(id, pedido);
	}

	@Transactional
	public void retirarPeloRh(Long empresaId, Long id) {
		travarQuemRespondeu(daEmpresa(empresaId, id));
		inativar(id);
	}

	@Transactional
	public void devolverPeloRh(Long empresaId, Long id) {
		travarQuemRespondeu(daEmpresa(empresaId, id));
		reativar(id);
	}

	/** A chamada do lembrete que o RH manda na hora para quem começou e parou (título e assunto do e-mail). */
	static final String CHAMADA_RH = "Falta pouco para terminar";

	/** tipo: CONVITE (quem não começou) ou LEMBRETE (quem começou e parou). */
	public record CobrancaFeita(String para, String tipo) {
	}

	/** O que saiu no "Lembrar todos que faltam": convites, lembretes e quem ficou de fora (e-mail que voltou). */
	public record LembreteTodos(int convites, int lembretes, int deFora) {
	}

	/**
	 * O e-mail da plataforma para um líder, com a mensagem da situação dele: quem começou e parou
	 * recebe o lembrete "continue de onde parou"; os outros, o convite (de novo). Na hora: a página
	 * espera e mostra se saiu ou o motivo. Fora de transação, como o envio de um convite.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public CobrancaFeita cobrarPeloRh(Long empresaId, Long id) {
		Convite convite = daEmpresa(empresaId, id);
		if (convite.status() != StatusConvite.COMECOU) {
			return new CobrancaFeita(enviarUm(id).para(), "CONVITE");
		}
		conferirEnvio(convite);
		try {
			envio.lembrarAgora(id, CHAMADA_RH, quemEsta.nome());
		}
		catch (EmailSender.FalhaEnvio e) {
			throw RegraNegocioException.conflito("O lembrete não saiu. " + e.getMessage());
		}
		return new CobrancaFeita(convite.getEmail(), "LEMBRETE");
	}

	/**
	 * "Lembrar todos que faltam" pela plataforma: cada um que não respondeu recebe a mensagem da
	 * sua situação, em segundo plano. Ficam de fora os e-mails que voltaram ou foram para o spam.
	 */
	public LembreteTodos lembrarTodosPeloRh(Long empresaId) {
		Empresa empresa = empresa(empresaId);
		if (!email.ativo()) {
			throw RegraNegocioException
				.conflito("Este servidor está com o envio de e-mail desligado: nada sai até ele ser ligado.");
		}
		if (empresa.encerrada(agora())) {
			throw RegraNegocioException.conflito("A avaliação desta empresa já foi encerrada: os e-mails não saem mais.");
		}
		List<Convite> faltam = convites.daEmpresa(empresaId)
			.stream()
			.filter(c -> !c.inativo() && c.status() != StatusConvite.CONCLUIDO)
			.toList();
		Map<Long, BloqueioDeEnvio.Bloqueio> bloqueados = bloqueios.de(faltam);
		List<Convite> livres = faltam.stream().filter(c -> !bloqueados.containsKey(c.getId())).toList();
		List<Long> paraLembrete = livres.stream().filter(c -> c.status() == StatusConvite.COMECOU).map(Convite::getId).toList();
		List<Long> paraConvite = livres.stream().filter(c -> c.status() != StatusConvite.COMECOU).map(Convite::getId).toList();
		if (livres.isEmpty()) {
			throw RegraNegocioException.conflito(faltam.isEmpty() ? "Todos já responderam: não há quem lembrar."
					: "Os e-mails de quem falta voltaram ou foram para o spam: mande o link pessoal pelo WhatsApp.");
		}
		envio.cobrar(paraConvite, paraLembrete, CHAMADA_RH, quemEsta.nome());
		return new LembreteTodos(paraConvite.size(), paraLembrete.size(), bloqueados.size());
	}

	/** O e-mail que o líder receberia pela plataforma agora (o convite ou o lembrete). */
	@Transactional(readOnly = true)
	public String previaParaRh(Long empresaId, Long id) {
		Convite c = daEmpresa(empresaId, id);
		return c.status() == StatusConvite.COMECOU ? envio.htmlLembrete(c, CHAMADA_RH) : envio.html(c);
	}

	/**
	 * O RH abriu a mensagem no próprio e-mail ou no WhatsApp: fica na linha do tempo de cada líder,
	 * com quem foi (não dá para confirmar que saiu). Repetir em menos de 10 minutos não duplica.
	 */
	@Transactional
	public void registrarContatoPeloRh(Long empresaId, List<Long> ids, String canal, String via) {
		boolean whatsapp = "WHATSAPP".equals(canal);
		TipoEvento tipo = whatsapp ? TipoEvento.CONTATO_WHATSAPP : TipoEvento.CONTATO_EMAIL;
		List<Long> unicos = ids.stream().distinct().limit(1000).toList();
		String detalhe = (unicos.size() > 1 ? "Num e-mail para todos que faltavam, em cópia oculta. " : "")
				+ (whatsapp ? "Aberto no WhatsApp com a mensagem e o link pessoal."
						: switch (via == null ? "" : via) {
							case "OUTLOOK" -> "Aberto no Outlook com a mensagem pronta.";
							case "GMAIL" -> "Aberto no Gmail com a mensagem pronta.";
							case "COPIA" -> "Mensagem copiada para mandar pelo e-mail do RH.";
							default -> "Aberto no programa de e-mail com a mensagem pronta.";
						})
				+ " Sai fora da plataforma: sem confirmação de entrega.";
		String autor = quemEsta.nome();
		Instant agora = agora();
		for (Long id : unicos) {
			Convite c = daEmpresa(empresaId, id);
			if (c.inativo()) {
				continue;
			}
			boolean repetido = eventos.findFirstByConviteIdAndTipoOrderByIdDesc(c.getId(), tipo)
				.filter(e -> Objects.equals(e.getAutor(), autor) && e.getQuando().isAfter(agora.minusSeconds(600)))
				.isPresent();
			if (!repetido) {
				registrar(c, tipo, agora, detalhe, autor);
			}
		}
	}

	/** O líder é desta empresa (senão, para quem pergunta, ele não existe). */
	private Convite daEmpresa(Long empresaId, Long id) {
		return convites.findWithCampanhaById(id)
			.filter(c -> c.getCampanha().vinculadaAEmpresa() && c.getCampanha().getEmpresa().getId().equals(empresaId))
			.orElseThrow(() -> new NaoEncontradoException("Líder não encontrado."));
	}

	private static boolean respondeu(Convite c) {
		return c.getDiagnostico() != null && c.getDiagnostico().concluido();
	}

	/**
	 * Quem já respondeu não muda pela página do RH: retirar tiraria as respostas do resultado (dava
	 * para "melhorar" a nota da empresa), e os dados dele já estão no relatório.
	 */
	private static void travarQuemRespondeu(Convite c) {
		if (respondeu(c)) {
			throw RegraNegocioException.conflito("Quem já respondeu não muda pela página do RH: as respostas estão no "
					+ "resultado do grupo. Para corrigir, fale com quem administra o Diagnóstico do Líder.");
		}
	}

	/**
	 * Um passo da linha do tempo que o RH vê. tom: neutro, envio, alerta ou concluido (a cor do ponto).
	 * entrega: a situação do e-mail daquele passo (ENTREGUE, DEVOLVIDO...), sem o detalhe técnico.
	 */
	public record PassoRh(String tipo, String titulo, String detalhe, Instant quando, String tom, EntregaRh entrega,
			String por) {

		PassoRh(String tipo, String titulo, String detalhe, Instant quando, String tom, EntregaRh entrega) {
			this(tipo, titulo, detalhe, quando, tom, entrega, null);
		}

		/** Feito por um gestor pela página do RH: o e-mail de quem fez. */
		PassoRh feitoPor(String email) {
			return new PassoRh(tipo, titulo, detalhe, quando, tom, entrega, email);
		}

	}

	public record EntregaRh(String situacao, Instant entregueEm, Instant atualizadoEm) {
	}

	/**
	 * A linha do tempo de um líder para a página do RH: só a participação (entrou na lista, convite,
	 * entrega, lembretes, abriu, começou, respondeu). Ficam de fora o que é do líder (ver, baixar e
	 * compartilhar o próprio resultado), o que é do painel (edições, PDF baixado pelo administrador,
	 * quem fez) e os dados que ele corrigiu. Líder de outra empresa ou fora da lista: 404.
	 */
	@Transactional(readOnly = true)
	public List<PassoRh> linhaDoTempoRh(Long empresaId, Long conviteId) {
		Convite c = convites.findWithCampanhaById(conviteId)
			.filter(x -> x.getInativadoEm() == null && x.getCampanha().vinculadaAEmpresa()
					&& x.getCampanha().getEmpresa().getId().equals(empresaId))
			.orElseThrow(() -> new NaoEncontradoException("Líder não encontrado."));
		List<EventoConvite> lista = eventos.findByConviteIdOrderByQuandoAscIdAsc(c.getId());
		Map<Long, EmailEnviado> emails = new HashMap<>();
		emailsEnviados.findAllById(lista.stream().map(EventoConvite::getEmailId).filter(Objects::nonNull).toList())
			.forEach(e -> emails.put(e.getId(), e));
		List<PassoRh> passos = new ArrayList<>();
		for (EventoConvite e : lista) {
			EmailEnviado email = e.getEmailId() == null ? null : emails.get(e.getEmailId());
			EntregaRh entrega = email == null ? null
					: new EntregaRh(email.getSituacao().name(), email.getEntregueEm(), email.getAtualizadoEm());
			PassoRh passo = switch (e.getTipo()) {
				case CRIADO -> new PassoRh("CRIADO", "Entrou na lista",
						QuemEsta.doRh(e.getAutor()) ? e.getDetalhe()
								: c.getOrigem() == OrigemLider.MANUAL ? "Cadastrado no painel." : "Veio da planilha de líderes.",
						e.getQuando(), "neutro", null);
				case COMECOU_PELO_LINK -> new PassoRh("COMECOU_PELO_LINK", "Começou pelo link aberto da empresa",
						"Entrou na lista por conta própria, sem convite.", e.getQuando(), "neutro", null);
				case ENVIADO -> new PassoRh("ENVIADO", "Convite enviado por e-mail", "Para " + c.getEmail() + ".",
						e.getQuando(), "envio", entrega);
				case LEMBRETE -> new PassoRh("LEMBRETE", "Lembrete enviado", primeiraFrase(e.getDetalhe()), e.getQuando(),
						"envio", entrega);
				case FALHA_ENVIO -> new PassoRh("FALHA_ENVIO", "O e-mail não saiu",
						"Mande o link pessoal pelo WhatsApp ou copie e envie por outro canal.", e.getQuando(), "alerta", null);
				case EMAIL_DEVOLVIDO -> new PassoRh("EMAIL_DEVOLVIDO", "O e-mail voltou",
						"O endereço pode estar errado ou a caixa, cheia. Mande o link pelo WhatsApp.", e.getQuando(), "alerta",
						null);
				case EMAIL_SPAM -> new PassoRh("EMAIL_SPAM", "O e-mail foi marcado como spam",
						"Os próximos e-mails podem não chegar. Mande o link pelo WhatsApp.", e.getQuando(), "alerta", null);
				case ABERTO -> new PassoRh("ABERTO", "Abriu o link", e.getDetalhe(), e.getQuando(), "neutro", null);
				case DADOS_CONFIRMADOS -> new PassoRh("DADOS_CONFIRMADOS", "Confirmou os dados", null, e.getQuando(),
						"neutro", null);
				case DADOS_CORRIGIDOS -> new PassoRh("DADOS_CORRIGIDOS", "Atualizou os próprios dados", null,
						e.getQuando(), "neutro", null);
				case COMECOU_RESPONDER -> new PassoRh("COMECOU_RESPONDER", "Começou a responder", e.getDetalhe(),
						e.getQuando(), "neutro", null);
				case CONCLUIDO -> new PassoRh("CONCLUIDO", "Respondeu o diagnóstico",
						"As respostas e o resultado ficam só com o líder.", e.getQuando(), "concluido", null);
				case RESULTADO_ENVIADO -> new PassoRh("RESULTADO_ENVIADO", "Recebeu o próprio resultado por e-mail",
						"Só o líder vê.", e.getQuando(), "concluido", entrega);
				case RESPONDEU_DE_NOVO -> new PassoRh("RESPONDEU_DE_NOVO", "Respondeu de novo",
						"Vale a resposta mais recente.", e.getQuando(), "concluido", null);
				// O RH abriu a mensagem no próprio e-mail ou no WhatsApp: fica registrado, sem confirmação de entrega.
				case CONTATO_EMAIL -> new PassoRh("CONTATO_EMAIL", "Mensagem pelo e-mail do RH", e.getDetalhe(),
						e.getQuando(), "neutro", null);
				case CONTATO_WHATSAPP -> new PassoRh("CONTATO_WHATSAPP", "Mensagem pelo WhatsApp do RH", e.getDetalhe(),
						e.getQuando(), "neutro", null);
				// Do painel do administrador, nada; do próprio RH, sim (com quem fez).
				case EDITADO -> QuemEsta.doRh(e.getAutor())
						? new PassoRh("EDITADO", "Dados editados", e.getDetalhe(), e.getQuando(), "neutro", null) : null;
				case INATIVADO -> QuemEsta.doRh(e.getAutor()) ? new PassoRh("INATIVADO", "Retirado da avaliação",
						"O link pessoal parou de funcionar.", e.getQuando(), "alerta", null) : null;
				case REATIVADO -> QuemEsta.doRh(e.getAutor()) ? new PassoRh("REATIVADO", "Voltou para a lista",
						"O link pessoal voltou a funcionar.", e.getQuando(), "neutro", null) : null;
				case VIU_RESULTADO, BAIXOU_PDF, COMPARTILHOU_PDF, PDF_BAIXADO_PAINEL -> null;
			};
			if (passo != null) {
				passos.add(QuemEsta.doRh(e.getAutor()) ? passo.feitoPor(QuemEsta.emailDoRh(e.getAutor())) : passo);
			}
		}
		return passos;
	}

	/** "Faltam 3 dias para responder. Para ana@x.com." → "Faltam 3 dias para responder." */
	private static String primeiraFrase(String texto) {
		if (texto == null) {
			return null;
		}
		int fim = texto.indexOf(". ");
		return fim < 0 ? texto : texto.substring(0, fim + 1);
	}

	private static Entrega entrega(EmailEnviado e) {
		return e == null ? null
				: new Entrega(e.getSituacao().name(), e.getPara(), e.getEntregueEm(), e.getAtualizadoEm(), e.getDetalhe());
	}

	/** Um administrador baixou o PDF individual: fica na linha do tempo, com quem foi. */
	@Transactional
	public void registrarPdfBaixadoNoPainel(UUID diagnosticoId) {
		convites.findByDiagnosticoId(diagnosticoId)
			.ifPresent(c -> registrar(c, TipoEvento.PDF_BAIXADO_PAINEL, agora(), null, quemEsta.nome()));
	}

	/**
	 * Dispara os convites por e-mail (em segundo plano). Só com provedor de e-mail configurado e
	 * antes do encerramento: sem isso, nada sai e nada é marcado como enviado.
	 */
	public EnvioIniciado enviar(Long empresaId, Envio pedido) {
		Empresa empresa = empresa(empresaId);
		if (!email.ativo()) {
			throw RegraNegocioException
				.conflito("Este servidor está com o envio de e-mail desligado: nada sai até ele ser ligado.");
		}
		if (empresa.encerrada(agora())) {
			throw RegraNegocioException.conflito("A avaliação desta empresa já foi encerrada: os convites não saem mais.");
		}
		Set<Long> pedidos = pedido == null || pedido.ids() == null ? Set.of() : Set.copyOf(pedido.ids());
		List<Convite> escolhidos = convites.daEmpresa(empresaId)
			.stream()
			.filter(c -> pedidos.isEmpty() ? c.status() == StatusConvite.AGUARDANDO_ENVIO
					: pedidos.contains(c.getId()) && podeReceber(c))
			.toList();
		Map<Long, BloqueioDeEnvio.Bloqueio> bloqueados = bloqueios.de(escolhidos);
		List<Long> ids = escolhidos.stream().map(Convite::getId).filter(i -> !bloqueados.containsKey(i)).toList();
		if (ids.isEmpty()) {
			throw RegraNegocioException.conflito(bloqueados.isEmpty() ? "Não há convites para enviar."
					: "Esses convites não saem de novo: o e-mail voltou ou foi marcado como spam. "
							+ "Corrija o endereço de cada líder ou mande o link pessoal por WhatsApp.");
		}
		envio.enviar(ids, quemEsta.nome());
		return new EnvioIniciado(ids.size());
	}

	/**
	 * O convite de um líder, na hora (o botão do detalhe, para quem não recebeu): o painel espera e
	 * mostra se saiu ou o motivo. Fora de transação: o envio grava a sua, e a falha fica na linha do
	 * tempo mesmo quando o pedido responde com erro.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public EnvioFeito enviarUm(Long id) {
		Convite convite = carregar(id);
		conferirEnvio(convite);
		try {
			envio.enviarAgora(id, quemEsta.nome());
		}
		catch (EmailSender.FalhaEnvio e) {
			throw RegraNegocioException.conflito("O convite não saiu. " + e.getMessage());
		}
		return new EnvioFeito(convite.getEmail());
	}

	/** Um e-mail para este líder pode sair agora? (servidor com e-mail, ativo, sem responder, no prazo, sem devolução) */
	private void conferirEnvio(Convite convite) {
		if (!email.ativo()) {
			throw RegraNegocioException
				.conflito("Este servidor está com o envio de e-mail desligado: nada sai até ele ser ligado.");
		}
		if (convite.inativo()) {
			throw RegraNegocioException.conflito("O líder está inativo. Reative para enviar o convite.");
		}
		if (convite.status() == StatusConvite.CONCLUIDO) {
			throw RegraNegocioException.conflito("O líder já respondeu: o convite não é mais necessário.");
		}
		if (convite.getCampanha().encerrada(agora())) {
			throw RegraNegocioException.conflito("A avaliação desta empresa já foi encerrada: o convite não sai mais.");
		}
		var bloqueio = bloqueios.de(convite);
		if (bloqueio.isPresent()) {
			throw RegraNegocioException.conflito(bloqueio.get().texto());
		}
	}

	/** O e-mail de convite como o líder vai receber (com o primeiro convite da lista ou um exemplo). */
	public String previaEmail(Long empresaId) {
		Campanha link = linkPrincipal(empresaId);
		return convites.daEmpresa(empresaId)
			.stream()
			.filter(c -> !c.inativo())
			.findFirst()
			.map(envio::html)
			.orElseGet(() -> envio.htmlExemplo(link));
	}

	/** O lembrete de 3 dias como o líder recebe (com alguém que ainda falta, ou um exemplo). */
	public String previaLembrete(Long empresaId) {
		Campanha link = linkPrincipal(empresaId);
		String chamada = "Faltam 3 dias para responder";
		return convites.daEmpresa(empresaId)
			.stream()
			.filter(c -> !c.inativo() && EnvioConvitesService.LEMBRAVEIS.contains(c.status()))
			.findFirst()
			.map(c -> envio.htmlLembrete(c, chamada))
			.orElseGet(() -> envio.htmlLembreteExemplo(link, chamada));
	}

	// ================================================================== link pessoal

	public ConvitePublico abrir(String token) {
		Convite convite = porToken(token);
		var campanha = campanhaService.publica(convite.getCampanha());
		String cargo = convite.isCargoOutro() ? CARGO_OUTRO
				: convite.getCargo() == null ? null : String.valueOf(convite.getCargo().getId());
		return new ConvitePublico(convite.getNome(), convite.getEmail(), convite.getWhatsapp(), cargo, campanha,
				convite.getDiagnostico() == null ? null : convite.getDiagnostico().getId(), convite.getOrigem());
	}

	/** O líder abriu o link (a página carregou no aparelho dele; a prévia do WhatsApp não conta). */
	@Transactional
	public void registrarAcesso(String token, String dispositivo) {
		Convite convite = porToken(token);
		Instant agora = agora();
		String aparelho = "celular".equals(dispositivo) ? "celular" : "computador".equals(dispositivo) ? "computador" : null;
		if (convite.registrarAcesso(agora)) {
			registrar(convite, TipoEvento.ABERTO, agora, aparelho == null ? null : "Pelo " + aparelho + ".");
		}
		else {
			// Voltou ao link: a mesma linha da linha do tempo conta as visitas.
			eventos.findFirstByConviteIdAndTipoOrderByIdDesc(convite.getId(), TipoEvento.ABERTO)
				.ifPresent(e -> e.atualizarDetalhe(
						visitas(convite.getAcessos(), Datas.porExtenso(agora, agora), aparelho, e.getDetalhe())));
		}
	}

	/**
	 * O líder confirmou (ou corrigiu) os dados: nasce o diagnóstico do convite. Se ele já tinha
	 * começado, os dados do diagnóstico são atualizados. A auditoria guarda o que mudou em
	 * relação ao que veio da planilha.
	 */
	@Transactional
	public DiagnosticoDto iniciar(String token, IniciarRequest req) {
		Convite convite = porToken(token);
		Convite.Dados confirmados = new Convite.Dados(nome(req.nome()), req.email().strip().toLowerCase(Locale.ROOT),
				whatsapp(req.whatsapp()), req.cargoId() == null ? null : cargoPorId(convite, req.cargoId()),
				Boolean.TRUE.equals(req.cargoOutro()));
		String mudancas = diferencas(convite.dados(), confirmados);

		DiagnosticoDto diagnostico;
		if (convite.getDiagnostico() == null) {
			var novo = diagnosticos.criar(convite.getCampanha(), req);
			convite.vincular(novo);
			diagnostico = DiagnosticoDto.de(novo);
			registrar(convite, mudancas.isEmpty() ? TipoEvento.DADOS_CONFIRMADOS : TipoEvento.DADOS_CORRIGIDOS, agora(),
					mudancas.isEmpty() ? null : mudancas);
		}
		else {
			diagnostico = diagnosticos.atualizarRespondente(convite.getDiagnostico().getId(), req);
			if (!mudancas.isEmpty()) {
				registrar(convite, TipoEvento.DADOS_CORRIGIDOS, agora(), mudancas);
			}
		}
		if (!mudancas.isEmpty()) {
			boolean emailLivre = confirmados.email().equals(convite.getEmail().toLowerCase(Locale.ROOT))
					|| !convites.emailsDoLink(convite.getCampanha().getId()).contains(confirmados.email());
			convite.atualizar(emailLivre ? confirmados : new Convite.Dados(confirmados.nome(), convite.getEmail(),
					confirmados.whatsapp(), confirmados.cargo(), confirmados.cargoOutro()));
		}
		return diagnostico;
	}

	/**
	 * Diagnóstico concluído: entra na auditoria do líder (na mesma transação). Pelo link aberto,
	 * o líder entra no cadastro agora, se ainda não entrou (respondeu de novo: vale esta resposta).
	 */
	@EventListener
	@Transactional
	public void aoConcluir(DiagnosticoConcluido evento) {
		Diagnostico d = diagnosticoRepositorio.findById(evento.diagnosticoId()).orElse(null);
		if (d == null) {
			return;
		}
		if (convites.findByDiagnosticoId(d.getId()).isEmpty()) {
			entrarNoCadastro(d, agora(), false);
		}
		convites.findByDiagnosticoId(d.getId()).ifPresent(c -> {
			if (c.getOrigem() == OrigemLider.LINK) {
				atualizarPeloDiagnostico(c, d);
			}
			registrar(c, TipoEvento.CONCLUIDO, agora(), null);
			substituirAnteriores(c, d, agora());
		});
	}

	/** Começou pelo link aberto da empresa: vira líder do cadastro (ou entra no convite do mesmo e-mail). */
	@EventListener
	@Transactional
	public void aoComecarPeloLink(DiagnosticoIniciadoPeloLink evento) {
		diagnosticoRepositorio.findById(evento.diagnosticoId()).ifPresent(d -> entrarNoCadastro(d, agora(), false));
	}

	/**
	 * Põe um diagnóstico de link de empresa no cadastro de líderes. Sem líder com o mesmo e-mail na
	 * empresa, cria um (origem LINK). Com líder, decide qual resposta vale: a concluída vence a que
	 * ficou pela metade, e entre duas iguais vale a mais recente; a outra sai dos cálculos
	 * ({@link Diagnostico#substituirPor}). Quem já tinha concluído e está respondendo de novo fica de
	 * fora até terminar (aí a nova vence, em {@link #aoConcluir}).
	 *
	 * @param quando    a hora de agora (sai na troca de resposta)
	 * @param historico carga de quem começou antes do cadastro existir: a linha do tempo usa as datas do
	 *                  próprio diagnóstico, inclusive a conclusão (no fluxo normal, ela entra em aoConcluir)
	 */
	@Transactional
	public void entrarNoCadastro(Diagnostico d, Instant quando, boolean historico) {
		if (d.getEmpresa() == null || d.getSubstituidoPor() != null
				|| convites.findByDiagnosticoId(d.getId()).isPresent()) {
			return;
		}
		String email = d.getRespondente().getEmail().strip().toLowerCase(Locale.ROOT);
		Convite lider = convites.daEmpresaPorEmail(d.getEmpresa().getId(), email).stream().findFirst().orElse(null);
		if (lider == null) {
			Convite novo = convites.save(new Convite(d.getCampanha(), novoToken(),
					new Convite.Dados(nome(d.getRespondente().getNome()), email, whatsapp(d.getRespondente().getWhatsapp()),
							d.getCargo(), d.isCargoOutro()),
					OrigemLider.LINK, d.getIniciadoEm()));
			novo.vincular(d);
			registrar(novo, TipoEvento.COMECOU_PELO_LINK, d.getIniciadoEm(),
					"Entrou na lista ao começar pelo link aberto da empresa, sem convite.");
			if (historico && d.concluido()) {
				registrar(novo, TipoEvento.CONCLUIDO, d.getConcluidoEm(), null);
			}
			if (d.getInativadoEm() != null) {
				novo.inativar(d.getInativadoEm());
			}
			return;
		}

		Diagnostico atual = lider.getDiagnostico();
		if (atual == null) {
			lider.vincular(d);
			registrar(lider, TipoEvento.COMECOU_PELO_LINK, d.getIniciadoEm(),
					"Respondeu pelo link aberto da empresa, não pelo link pessoal.");
			if (historico && d.concluido()) {
				registrar(lider, TipoEvento.CONCLUIDO, d.getConcluidoEm(), null);
			}
		}
		else {
			boolean novaVence = vence(d, atual);
			Diagnostico vale = novaVence ? d : atual;
			Diagnostico sai = novaVence ? atual : d;
			if (!sai.concluido() && vale.concluido() && sai.getIniciadoEm().isAfter(vale.getIniciadoEm())) {
				// Já tinha concluído e está respondendo de novo: a resposta concluída vale até esta
				// terminar (aí decide, em aoConcluir). O cadastro já mostra os dados que ele digitou agora.
				if (sai == d) {
					if (lider.getOrigem() == OrigemLider.LINK) {
						atualizarPeloDiagnostico(lider, d);
					}
					if (!historico) {
						registrar(lider, TipoEvento.COMECOU_PELO_LINK, quando,
								"Começou a responder de novo pelo link aberto da empresa. A resposta anterior vale até esta terminar.");
					}
				}
				return;
			}
			sai.substituirPor(vale, quando);
			if (novaVence) {
				lider.vincular(d);
				Instant em = !historico ? quando : d.concluido() ? d.getConcluidoEm() : d.getIniciadoEm();
				registrar(lider, d.concluido() ? TipoEvento.RESPONDEU_DE_NOVO : TipoEvento.COMECOU_PELO_LINK, em,
						d.concluido() ? "Respondeu de novo pelo link aberto da empresa: vale a resposta mais recente, e a anterior saiu dos cálculos."
								: "Começou de novo pelo link aberto da empresa.");
			}
		}
		if (lider.inativo()) {
			// Líder inativado: a resposta nova também fica fora dos cálculos até ele ser reativado.
			lider.getDiagnostico().inativar(lider.getInativadoEm());
		}
	}

	/** A concluída vence a que ficou pela metade; entre duas iguais, a mais recente. */
	static boolean vence(Diagnostico a, Diagnostico b) {
		return vence(new Tentativa(a.getIniciadoEm(), a.getConcluidoEm()), new Tentativa(b.getIniciadoEm(), b.getConcluidoEm()));
	}

	/** Uma resposta do líder: quando começou e quando concluiu (null: ficou pela metade). */
	record Tentativa(Instant iniciadoEm, Instant concluidoEm) {

		boolean concluida() {
			return concluidoEm != null;
		}

	}

	static boolean vence(Tentativa a, Tentativa b) {
		if (a.concluida() != b.concluida()) {
			return a.concluida();
		}
		return a.concluida() ? a.concluidoEm().isAfter(b.concluidoEm()) : a.iniciadoEm().isAfter(b.iniciadoEm());
	}

	/**
	 * O líder concluiu: respostas mais antigas dele pelo link aberto que ficaram sem cadastro (de
	 * antes de existir o cadastro, por exemplo) saem dos cálculos, para não contar duas vezes.
	 */
	private void substituirAnteriores(Convite lider, Diagnostico vale, Instant quando) {
		for (Diagnostico outro : diagnosticoRepositorio.daEmpresaPorEmail(vale.getEmpresa().getId(), lider.getEmail())) {
			if (outro != vale && outro.getSubstituidoPor() == null && outro.getInativadoEm() == null
					&& outro.getIniciadoEm().isBefore(vale.getIniciadoEm())
					&& convites.findByDiagnosticoId(outro.getId()).isEmpty()) {
				outro.substituirPor(vale, quando);
			}
		}
	}

	/** Quem entrou pelo link aberto pode ter corrigido os dados antes de terminar: o cadastro acompanha. */
	private void atualizarPeloDiagnostico(Convite lider, Diagnostico d) {
		String email = d.getRespondente().getEmail().strip().toLowerCase(Locale.ROOT);
		boolean emailLivre = email.equals(lider.getEmail().toLowerCase(Locale.ROOT))
				|| convites.daEmpresaPorEmail(d.getEmpresa().getId(), email).isEmpty();
		lider.atualizar(new Convite.Dados(nome(d.getRespondente().getNome()), emailLivre ? email : lider.getEmail(),
				whatsapp(d.getRespondente().getWhatsapp()), d.getCargo(), d.isCargoOutro()));
	}

	// ================================================================== apoio

	private ConviteItem item(Convite c) {
		return item(c, problemasDeEntrega(List.of(c)).get(c.getId()), bloqueios.de(c).orElse(null),
				novasTentativas(c.getCampanha().getEmpresa().getId()));
	}

	/**
	 * Quem concluiu e está respondendo de novo pelo link aberto, por e-mail (em minúsculas): a
	 * tentativa mais recente de cada um.
	 */
	Map<String, Diagnostico> novasTentativas(Long empresaId) {
		Map<String, Diagnostico> porEmail = new HashMap<>();
		for (Diagnostico d : convites.tentativasSemCadastro(empresaId)) {
			porEmail.put(d.getRespondente().getEmail().strip().toLowerCase(Locale.ROOT), d);
		}
		return porEmail;
	}

	/** A tentativa nova do líder, se ele concluiu e está respondendo de novo (null se não). */
	static NovaTentativa novaTentativa(Convite c, Map<String, Diagnostico> tentativas) {
		Diagnostico atual = c.getDiagnostico();
		Diagnostico nova = tentativas.get(c.getEmail().strip().toLowerCase(Locale.ROOT));
		if (atual == null || !atual.concluido() || nova == null || !nova.getIniciadoEm().isAfter(atual.getIniciadoEm())) {
			return null;
		}
		return new NovaTentativa(nova.getRespondente().getNome(), nova.getIniciadoEm(), nova.perguntasRespondidas(),
				nova.getVersao().getPerguntas().size());
	}

	/**
	 * ultimaFalha: a falha de envio mais recente do convite. A lista mostra o motivo enquanto o
	 * e-mail não sair (um envio depois da falha, ou o líder já ter aberto o link, apaga o aviso).
	 */
	private ConviteItem item(Convite c, EventoConvite ultimaFalha, BloqueioDeEnvio.Bloqueio bloqueio,
			Map<String, Diagnostico> tentativas) {
		StatusConvite status = c.status();
		boolean semEnvioDepois = ultimaFalha != null
				&& (c.getEnviadoEm() == null || ultimaFalha.getQuando().isAfter(c.getEnviadoEm()));
		String falhaEnvio = semEnvioDepois
				&& (status == StatusConvite.AGUARDANDO_ENVIO || status == StatusConvite.ENVIADO)
						? ultimaFalha.getDetalhe() : null;
		String cargo = c.isCargoOutro() ? PlanilhaConvites.OUTRO_CARGO : c.getCargo() == null ? null : c.getCargo().getNome();
		Instant concluidoEm = c.getDiagnostico() == null ? null : c.getDiagnostico().getConcluidoEm();
		return new ConviteItem(c.getId(), c.getNome(), c.getEmail(), c.getWhatsapp(),
				c.getCargo() == null ? null : c.getCargo().getId(), c.isCargoOutro(), cargo, c.status(),
				c.getCriadoEm(), c.getEnviadoEm(), c.getEnvios(), c.getUltimoAcessoEm(), c.getAcessos(), concluidoEm,
				link(c), c.getDiagnostico() == null ? null : c.getDiagnostico().getId(), falhaEnvio,
				bloqueio == null || !podeReceber(c) ? null : bloqueio.motivo().name(),
				bloqueio == null || !podeReceber(c) ? null : bloqueio.texto(), c.getOrigem(), novaTentativa(c, tentativas));
	}

	/**
	 * O problema de entrega mais recente de cada convite: o e-mail não saiu, voltou ou foi marcado
	 * como spam. Quem chama decide se ainda vale (um envio depois apaga o aviso).
	 */
	Map<Long, EventoConvite> problemasDeEntrega(List<Convite> lista) {
		if (lista.isEmpty()) {
			return Map.of();
		}
		Map<Long, EventoConvite> ultimas = new HashMap<>();
		eventos.findByConviteIdInAndTipoInOrderByQuandoAscIdAsc(lista.stream().map(Convite::getId).toList(),
				PROBLEMAS_DE_ENTREGA)
			.forEach(e -> ultimas.put(e.conviteId(), e));
		return ultimas;
	}

	/** O aparelho que a linha do tempo já guardou: "Pelo celular.", "sempre pelo celular" ou "A primeira foi pelo celular". */
	private static final Pattern APARELHO_ANTERIOR = Pattern
		.compile("^Pelo (celular|computador)\\.|sempre pelo (celular|computador)|A primeira foi pelo (celular|computador)");

	/**
	 * A linha "Abriu o link", recontada a cada visita sem repetir o aparelho:
	 * "Abriu 2 vezes, sempre pelo celular; a última em 25 de novembro, às 9h." ou, se trocou,
	 * "Abriu 3 vezes; a última em 25 de novembro, às 9h, pelo computador. A primeira foi pelo celular."
	 * anterior: o texto que a linha tinha antes desta visita (é dele que sai o primeiro aparelho).
	 */
	static String visitas(int vezes, String ultimaEm, String aparelho, String anterior) {
		String primeiro = null;
		boolean sempreIgual = false;
		var achou = anterior == null ? null : APARELHO_ANTERIOR.matcher(anterior);
		if (achou != null && achou.find()) {
			sempreIgual = achou.group(3) == null;
			primeiro = achou.group(1) != null ? achou.group(1) : achou.group(2) != null ? achou.group(2) : achou.group(3);
		}
		if (aparelho != null && sempreIgual && aparelho.equals(primeiro)) {
			return "Abriu " + vezes + " vezes, sempre pelo " + aparelho + "; a última em " + ultimaEm + ".";
		}
		String texto = "Abriu " + vezes + " vezes; a última em " + ultimaEm + (aparelho == null ? "" : ", pelo " + aparelho)
				+ ".";
		return primeiro == null ? texto : texto + " A primeira foi pelo " + primeiro + ".";
	}

	String link(Convite convite) {
		return urlPublica + "/i/" + convite.getToken();
	}

	static Resumo resumo(List<ConviteItem> itens) {
		int inativos = 0, aguardando = 0, enviados = 0, abriram = 0, comecaram = 0, concluiram = 0;
		int convidados = 0, peloLink = 0, peloLinkConcluiram = 0;
		for (ConviteItem i : itens) {
			switch (i.status()) {
				case INATIVO -> inativos++;
				case AGUARDANDO_ENVIO -> aguardando++;
				default -> {
				}
			}
			if (i.status() == StatusConvite.INATIVO) {
				continue;
			}
			if (i.origem() == OrigemLider.LINK) {
				peloLink++;
				if (i.status() == StatusConvite.CONCLUIDO) {
					peloLinkConcluiram++;
				}
				continue;
			}
			convidados++;
			if (i.enviadoEm() != null) {
				enviados++;
			}
			if (i.acessos() > 0 || i.status() == StatusConvite.COMECOU || i.status() == StatusConvite.CONCLUIDO) {
				abriram++;
			}
			if (i.status() == StatusConvite.COMECOU || i.status() == StatusConvite.CONCLUIDO) {
				comecaram++;
			}
			if (i.status() == StatusConvite.CONCLUIDO) {
				concluiram++;
			}
		}
		return new Resumo(itens.size() - inativos, aguardando, enviados, abriram, comecaram, concluiram, inativos,
				convidados, peloLink, peloLinkConcluiram);
	}

	private static boolean podeReceber(Convite c) {
		return !c.inativo() && c.status() != StatusConvite.CONCLUIDO;
	}

	private Convite.Dados validar(NovoConvite n, List<Cargo> lista) {
		String nome = nome(n.nome());
		if (nome.length() < 2) {
			throw new RegraNegocioException("Escreva o nome do líder.").noCampo("nome");
		}
		if (nome.length() > 160) {
			throw new RegraNegocioException("Use até 160 caracteres no nome.").noCampo("nome");
		}
		String email = n.email() == null ? "" : n.email().strip().toLowerCase(Locale.ROOT);
		if (email.isEmpty()) {
			throw new RegraNegocioException("Escreva o e-mail do líder.").noCampo("email");
		}
		var problemaEmail = Emails.problema(email);
		if (problemaEmail.isPresent()) {
			throw new RegraNegocioException(problemaEmail.get()).noCampo("email");
		}
		String whatsapp = whatsapp(n.whatsapp());
		var problemaWhatsapp = Telefones.problema(whatsapp);
		if (problemaWhatsapp.isPresent()) {
			throw new RegraNegocioException(problemaWhatsapp.get()).noCampo("whatsapp");
		}
		Cargo cargo = null;
		boolean outro = false;
		if (!lista.isEmpty()) {
			outro = n.cargoOutro();
			if (!outro) {
				if (n.cargoId() == null) {
					throw new RegraNegocioException("Escolha o cargo.").noCampo("cargo");
				}
				cargo = lista.stream()
					.filter(c -> c.getId().equals(n.cargoId()))
					.findFirst()
					.orElseThrow(() -> new RegraNegocioException("Esse cargo saiu da lista.").noCampo("cargo"));
			}
		}
		return new Convite.Dados(nome, email, whatsapp.isEmpty() ? null : whatsapp, cargo, outro);
	}

	/** "E-mail: ana@a.com → ana@b.com; Cargo: Gerente → Diretor" (só o que mudou). */
	private static String diferencas(Convite.Dados antes, Convite.Dados depois) {
		List<String> partes = new ArrayList<>();
		comparar(partes, "Nome", antes.nome(), depois.nome());
		comparar(partes, "E-mail", antes.email() == null ? null : antes.email().toLowerCase(Locale.ROOT),
				depois.email());
		comparar(partes, "WhatsApp", antes.whatsapp(), depois.whatsapp());
		comparar(partes, "Cargo", nomeCargo(antes), nomeCargo(depois));
		return String.join("; ", partes);
	}

	private static void comparar(List<String> partes, String rotulo, String antes, String depois) {
		String a = antes == null || antes.isBlank() ? "(vazio)" : antes;
		String d = depois == null || depois.isBlank() ? "(vazio)" : depois;
		if (!a.equals(d)) {
			partes.add(rotulo + ": " + a + " → " + d);
		}
	}

	private static String nomeCargo(Convite.Dados d) {
		return d.cargoOutro() ? PlanilhaConvites.OUTRO_CARGO : d.cargo() == null ? null : d.cargo().getNome();
	}

	private Cargo cargoPorId(Convite convite, Long id) {
		return cargos.ativos(convite.getCampanha().getEmpresa().getId())
			.stream()
			.filter(c -> c.getId().equals(id))
			.findFirst()
			.orElse(null);
	}

	private static Cargo cargoExato(List<Cargo> lista, String texto) {
		String alvo = PlanilhaConvites.normalizar(texto);
		return alvo.isEmpty() ? null
				: lista.stream().filter(c -> PlanilhaConvites.normalizar(c.getNome()).equals(alvo)).findFirst().orElse(null);
	}

	private static boolean outroCargo(String texto) {
		String t = PlanilhaConvites.normalizar(texto);
		return t.equals("outro cargo") || t.equals("outro") || t.startsWith("outro:");
	}

	/**
	 * O cargo da lista mais parecido com o que veio escrito ("Gerente Comercial" → Gerente):
	 * primeiro o que está contido no texto (o mais longo), depois o de menor distância de edição.
	 */
	static Long sugestao(List<Cargo> lista, String texto) {
		String alvo = PlanilhaConvites.normalizar(texto);
		var contido = lista.stream()
			.filter(c -> {
				String nome = PlanilhaConvites.normalizar(c.getNome());
				return alvo.contains(nome) || nome.contains(alvo);
			})
			.max(Comparator.comparingInt(c -> c.getNome().length()));
		if (contido.isPresent()) {
			return contido.get().getId();
		}
		return lista.stream()
			.filter(c -> distancia(alvo, PlanilhaConvites.normalizar(c.getNome())) <= Math.max(2, alvo.length() / 4))
			.min(Comparator.comparingInt(c -> distancia(alvo, PlanilhaConvites.normalizar(c.getNome()))))
			.map(Cargo::getId)
			.orElse(null);
	}

	private static int distancia(String a, String b) {
		int[] anterior = new int[b.length() + 1];
		int[] atual = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			anterior[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			atual[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int custo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				atual[j] = Math.min(Math.min(atual[j - 1] + 1, anterior[j] + 1), anterior[j - 1] + custo);
			}
			int[] troca = anterior;
			anterior = atual;
			atual = troca;
		}
		return anterior[b.length()];
	}

	static String nome(String texto) {
		return texto == null ? "" : texto.replace("(exemplo)", "").strip().replaceAll("\\s+", " ");
	}

	/** No formato (11) 98765-4321, sem o 55 do país; vazio se não veio nada. */
	static String whatsapp(String texto) {
		return Telefones.formatar(texto);
	}

	private List<CargoOpcao> opcoes(Long empresaId) {
		return cargos.ativos(empresaId).stream().map(c -> new CargoOpcao(c.getId(), c.getNome())).toList();
	}

	private String novoToken() {
		for (int tentativa = 0; tentativa < 5; tentativa++) {
			var sb = new StringBuilder(TAMANHO_TOKEN);
			for (int i = 0; i < TAMANHO_TOKEN; i++) {
				sb.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
			}
			if (!convites.existsByToken(sb.toString())) {
				return sb.toString();
			}
		}
		throw new IllegalStateException("Não foi possível gerar um link único.");
	}

	private void registrar(Convite convite, TipoEvento tipo, Instant quando, String detalhe) {
		registrar(convite, tipo, quando, detalhe, null);
	}

	private void registrar(Convite convite, TipoEvento tipo, Instant quando, String detalhe, String autor) {
		eventos.save(new EventoConvite(convite, tipo, quando, detalhe, autor, null));
	}

	private Empresa empresa(Long id) {
		return empresas.findById(id).orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
	}

	/** O link principal da empresa (o mais antigo): é nele que os convites entram. */
	private Campanha linkPrincipal(Long empresaId) {
		empresa(empresaId);
		return campanhas.findByEmpresaIdInOrderByIdAsc(List.of(empresaId))
			.stream()
			.findFirst()
			.orElseThrow(() -> new NaoEncontradoException("Esta empresa ainda não tem link."));
	}

	private Convite carregar(Long id) {
		return convites.findWithCampanhaById(id).orElseThrow(() -> new NaoEncontradoException("Convite não encontrado."));
	}

	private Convite porToken(String token) {
		Convite convite = convites.findByToken(token)
			.orElseThrow(() -> new NaoEncontradoException("Este convite não existe. Confira o link que você recebeu."));
		if (convite.inativo()) {
			throw new NaoEncontradoException("Este convite não está mais ativo. Fale com o RH da sua empresa.");
		}
		return convite;
	}

	private Instant agora() {
		return Instant.now(clock);
	}

}
