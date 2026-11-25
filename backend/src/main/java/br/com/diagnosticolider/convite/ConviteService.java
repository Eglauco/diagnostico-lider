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
import br.com.diagnosticolider.convite.ConviteDtos.NovoConvite;
import br.com.diagnosticolider.convite.ConviteDtos.Resumo;
import br.com.diagnosticolider.diagnostico.Diagnostico;
import br.com.diagnosticolider.diagnostico.DiagnosticoConcluido;
import br.com.diagnosticolider.diagnostico.DiagnosticoDtos.DiagnosticoDto;
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
 * Convites nominais: a planilha do RH vira um link pessoal por líder, com os dados para ele só
 * confirmar, e cada passo do link fica na auditoria. Os convites vão no link principal da empresa
 * (o mais antigo), então os diagnósticos entram no relatório dela como os do link aberto.
 */
@Service
@Transactional(readOnly = true)
public class ConviteService {

	static final String CARGO_OUTRO = "outro";

	static final Set<TipoEvento> PROBLEMAS_DE_ENTREGA = EnumSet.of(TipoEvento.FALHA_ENVIO, TipoEvento.EMAIL_DEVOLVIDO,
			TipoEvento.EMAIL_SPAM);

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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

	private final Clock clock;

	private final String urlPublica;

	ConviteService(ConviteRepository convites, EventoConviteRepository eventos, CampanhaRepository campanhas,
			CampanhaService campanhaService, EmpresaRepository empresas, CargoService cargos,
			DiagnosticoService diagnosticos, DiagnosticoRepository diagnosticoRepositorio, PlanilhaConvites planilha,
			EnvioConvitesService envio, EmailSender email, EmailEnviadoRepository emailsEnviados, QuemEsta quemEsta,
			Clock clock, AppProperties propriedades) {
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
		List<ConviteItem> itens = daEmpresa.stream().map(c -> item(c, falhas.get(c.getId()))).toList();
		return new Convites(email.ativo(), empresa.getEncerraEm(), empresa.encerrada(agora()), resumo(itens), itens,
				opcoes(empresaId));
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
		Map<String, String> erros = new LinkedHashMap<>();
		Set<String> nestaLista = new HashSet<>();
		List<Convite.Dados> validos = new ArrayList<>();
		for (int i = 0; i < pedido.convites().size(); i++) {
			try {
				Convite.Dados dados = validar(pedido.convites().get(i), lista);
				if (!nestaLista.add(dados.email())) {
					throw new RegraNegocioException("E-mail repetido nesta lista.");
				}
				if (existentes.contains(dados.email())) {
					throw new RegraNegocioException(inativos.contains(dados.email())
							? "Esse líder já está na lista, inativo: reative em vez de convidar de novo."
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
		String autor = quemEsta.nome();
		String origem = pedido.daPlanilha() ? "Importado da planilha." : "Adicionado no painel.";
		for (Convite.Dados dados : validos) {
			Convite convite = convites.save(new Convite(link, novoToken(), dados, agora));
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
		List<Long> ids = convites.daEmpresa(empresaId)
			.stream()
			.filter(c -> pedidos.isEmpty() ? c.status() == StatusConvite.AGUARDANDO_ENVIO
					: pedidos.contains(c.getId()) && podeReceber(c))
			.map(Convite::getId)
			.toList();
		if (ids.isEmpty()) {
			throw RegraNegocioException.conflito("Não há convites para enviar.");
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
		try {
			envio.enviarAgora(id, quemEsta.nome());
		}
		catch (EmailSender.FalhaEnvio e) {
			throw RegraNegocioException.conflito("O convite não saiu. " + e.getMessage());
		}
		return new EnvioFeito(convite.getEmail());
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
				convite.getDiagnostico() == null ? null : convite.getDiagnostico().getId());
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
				.ifPresent(e -> e.atualizarDetalhe(primeiraFrase(e.getDetalhe()) + "Abriu " + convite.getAcessos()
						+ " vezes; a última em " + Datas.porExtenso(agora, agora)
						+ (aparelho == null ? "" : ", pelo " + aparelho) + "."));
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

	/** Diagnóstico de um convite concluído: entra na auditoria (na mesma transação). */
	@EventListener
	@Transactional
	public void aoConcluir(DiagnosticoConcluido evento) {
		convites.findByDiagnosticoId(evento.diagnosticoId())
			.ifPresent(c -> registrar(c, TipoEvento.CONCLUIDO, agora(), null));
	}

	// ================================================================== apoio

	private ConviteItem item(Convite c) {
		return item(c, problemasDeEntrega(List.of(c)).get(c.getId()));
	}

	/**
	 * ultimaFalha: a falha de envio mais recente do convite. A lista mostra o motivo enquanto o
	 * e-mail não sair (um envio depois da falha, ou o líder já ter aberto o link, apaga o aviso).
	 */
	private ConviteItem item(Convite c, EventoConvite ultimaFalha) {
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
				link(c), c.getDiagnostico() == null ? null : c.getDiagnostico().getId(), falhaEnvio);
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

	/** "Pelo celular. Abriu 3 vezes…" vira "Pelo celular. " (o resto é recontado a cada visita). */
	private static String primeiraFrase(String detalhe) {
		if (detalhe == null || detalhe.isBlank() || detalhe.startsWith("Abriu ")) {
			return "";
		}
		int fim = detalhe.indexOf(". ");
		return (fim < 0 ? detalhe : detalhe.substring(0, fim + 1)) + " ";
	}

	String link(Convite convite) {
		return urlPublica + "/i/" + convite.getToken();
	}

	private static Resumo resumo(List<ConviteItem> itens) {
		int inativos = 0, aguardando = 0, enviados = 0, abriram = 0, comecaram = 0, concluiram = 0;
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
		return new Resumo(itens.size() - inativos, aguardando, enviados, abriram, comecaram, concluiram, inativos);
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
		if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
			throw new RegraNegocioException("Confira o e-mail: parece faltar o @ ou o domínio.").noCampo("email");
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
