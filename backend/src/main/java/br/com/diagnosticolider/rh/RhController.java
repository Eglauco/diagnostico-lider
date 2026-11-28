package br.com.diagnosticolider.rh;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.diagnosticolider.acompanhamento.ResultadoDaEmpresa;
import br.com.diagnosticolider.campanha.Campanha;
import br.com.diagnosticolider.campanha.CampanhaRepository;
import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import br.com.diagnosticolider.convite.ConviteDtos.CriarConvites;
import br.com.diagnosticolider.convite.ConviteDtos.Criados;
import br.com.diagnosticolider.convite.ConviteDtos.Envio;
import br.com.diagnosticolider.convite.ConviteDtos.EnvioIniciado;
import br.com.diagnosticolider.convite.ConviteDtos.Importacao;
import br.com.diagnosticolider.convite.ConviteDtos.NovoConvite;
import br.com.diagnosticolider.convite.ConviteService;
import br.com.diagnosticolider.convite.ParticipacaoService;
import br.com.diagnosticolider.diagnostico.DiagnosticoRepository;
import br.com.diagnosticolider.diagnostico.StatusDiagnostico;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.empresa.EmpresaRepository;
import br.com.diagnosticolider.empresa.LogomarcaService;
import br.com.diagnosticolider.empresa.MarcaEmpresa;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados;
import br.com.diagnosticolider.relatorio.RelatorioPdfService;
import br.com.diagnosticolider.rh.RhDtos.Entrar;
import br.com.diagnosticolider.rh.RhDtos.GestorDto;
import br.com.diagnosticolider.rh.RhDtos.PainelRh;
import br.com.diagnosticolider.rh.RhDtos.PedirCodigo;
import br.com.diagnosticolider.rh.RhDtos.ResultadoRh;
import br.com.diagnosticolider.rh.RhDtos.Sessao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A página do RH (/acompanhar): os gestores da empresa entram com um código por e-mail e veem a
 * participação ao vivo e o resultado do grupo (sempre a foto anônima, ver ResultadoDaEmpresa).
 * Só leitura: nada aqui muda convites, prazos ou diagnósticos.
 */
@RestController
@RequestMapping(CookieRh.CAMINHO)
class RhController {

	private static final MediaType XLSX = MediaType
		.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final AcessoRhService acessos;

	private final ParticipacaoService participacao;

	private final ResultadoDaEmpresa resultados;

	private final RelatorioPdfService pdf;

	private final EmpresaRepository empresas;

	private final DiagnosticoRepository diagnosticos;

	private final LogomarcaService logomarcas;

	private final Clock clock;

	private final CampanhaRepository campanhas;

	private final ConviteService convites;

	RhController(AcessoRhService acessos, ParticipacaoService participacao, ResultadoDaEmpresa resultados,
			RelatorioPdfService pdf, EmpresaRepository empresas, DiagnosticoRepository diagnosticos,
			LogomarcaService logomarcas, Clock clock, CampanhaRepository campanhas, ConviteService convites) {
		this.campanhas = campanhas;
		this.convites = convites;
		this.acessos = acessos;
		this.participacao = participacao;
		this.resultados = resultados;
		this.pdf = pdf;
		this.empresas = empresas;
		this.diagnosticos = diagnosticos;
		this.logomarcas = logomarcas;
		this.clock = clock;
	}

	// ------------------------------------------------------------------ entrar e sair

	/** Quem está neste aparelho (null: ninguém). Também entrega o cookie XSRF-TOKEN do login. */
	@GetMapping("/sessao")
	Sessao sessao(HttpServletRequest request, HttpServletResponse response, CsrfToken csrf) {
		csrf.getToken();
		String token = CookieRh.ler(request);
		var gestor = acessos.gestor(token).orElse(null);
		if (gestor == null) {
			if (token != null) {
				CookieRh.apagar(request, response);
			}
			return new Sessao(null);
		}
		if (gestor.renovado()) {
			CookieRh.gravar(request, response, token, AcessoRhService.LEMBRAR);
		}
		return new Sessao(GestorDto.de(gestor));
	}

	/** Manda o código. Responde igual para quem não é gestor: a tela não revela quem acompanha. */
	@PostMapping("/codigo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void pedirCodigo(@RequestBody @Validated PedirCodigo pedido, HttpServletRequest request) {
		acessos.pedirCodigo(pedido.email(), request.getRemoteAddr(), pedido.empresa());
	}

	/**
	 * A marca da empresa para a tela de entrada de /acompanhar/{codigo}: o gestor reconhece que está no
	 * lugar certo antes de pedir o código. É a mesma marca pública do link dos líderes (/c/{codigo});
	 * nada da avaliação sai daqui.
	 */
	@GetMapping("/marca/{codigo}")
	MarcaEmpresa marca(@PathVariable String codigo) {
		Long empresaId = campanhas.findByCodigo(codigo.strip().toLowerCase(java.util.Locale.ROOT))
			.filter(Campanha::vinculadaAEmpresa)
			.map(c -> c.getEmpresa().getId())
			.orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
		return logomarcas.marca(empresas.findById(empresaId)
			.orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada.")));
	}

	@PostMapping("/entrar")
	Sessao entrar(@RequestBody @Validated Entrar pedido, HttpServletRequest request, HttpServletResponse response) {
		String aparelho = request.getHeader(HttpHeaders.USER_AGENT);
		String ip = request.getRemoteAddr();
		AcessoRhService.Entrada entrada = pedido.link() != null && !pedido.link().isBlank()
				? acessos.entrarComLink(pedido.link(), ip, aparelho)
				: acessos.entrarComCodigo(pedido.email(), pedido.codigo(), ip, aparelho);
		CookieRh.gravar(request, response, entrada.token(), AcessoRhService.LEMBRAR);
		return new Sessao(GestorDto.de(entrada.gestor()));
	}

	@PostMapping("/sair")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void sair(HttpServletRequest request, HttpServletResponse response) {
		acessos.sair(CookieRh.ler(request));
		CookieRh.apagar(request, response);
	}

	// ------------------------------------------------------------------ a empresa

	/** A marca, o prazo e a participação ao vivo. */
	@GetMapping("/empresas/{id}")
	PainelRh painel(@PathVariable Long id, AcessoRhService.Gestor gestor) {
		Empresa empresa = empresaDo(gestor, id);
		var agora = clock.instant();
		int concluidos = (int) diagnosticos.countByEmpresaIdAndStatusAndInativadoEmIsNull(id, StatusDiagnostico.CONCLUIDO);
		return new PainelRh(logomarcas.marca(empresa), empresa.getEncerraEm(), empresa.encerrada(agora),
				participacao.aoVivo(id), concluidos, agora);
	}

	/** A página abriu (o painel do administrador mostra o último acesso de cada gestor). */
	@PostMapping("/empresas/{id}/visita")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void visita(@PathVariable Long id, AcessoRhService.Gestor gestor, HttpServletRequest request) {
		empresaDo(gestor, id);
		acessos.registrar(gestor, id, AtividadeRh.Tipo.ABRIU, request.getHeader(HttpHeaders.USER_AGENT));
	}

	/** O resultado do grupo: a última foto anônima (abre com 3 respostas e muda a cada 3 novas). */
	@GetMapping("/empresas/{id}/resultado")
	ResultadoRh resultado(@PathVariable Long id, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		return ResultadoRh.de(resultados.atual(id));
	}

	@GetMapping("/empresas/{id}/resultado.pdf")
	ResponseEntity<byte[]> resultadoPdf(@PathVariable Long id, AcessoRhService.Gestor gestor,
			HttpServletRequest request) {
		empresaDo(gestor, id);
		RelatorioEmpresaDados dados = resultados.relatorio(id);
		byte[] conteudo = pdf.gerar(dados);
		acessos.registrar(gestor, id, AtividadeRh.Tipo.BAIXOU_RESULTADO_PDF, request.getHeader(HttpHeaders.USER_AGENT));
		return arquivo(dados.nomeArquivo(), MediaType.APPLICATION_PDF, conteudo);
	}

	/** A linha do tempo de um líder: só a participação (nada das respostas nem do resultado). */
	@GetMapping("/empresas/{id}/lideres/{lider}/linha-do-tempo")
	List<ConviteService.PassoRh> linhaDoTempo(@PathVariable Long id, @PathVariable Long lider,
			AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		return convites.linhaDoTempoRh(id, lider);
	}

	@GetMapping("/empresas/{id}/participacao.pdf")
	ResponseEntity<byte[]> participacaoPdf(@PathVariable Long id, AcessoRhService.Gestor gestor,
			HttpServletRequest request) {
		empresaDo(gestor, id);
		var arquivo = participacao.pdf(id);
		acessos.registrar(gestor, id, AtividadeRh.Tipo.BAIXOU_PARTICIPACAO_PDF,
				request.getHeader(HttpHeaders.USER_AGENT));
		return arquivo(arquivo.nome(), MediaType.APPLICATION_PDF, arquivo.conteudo());
	}

	@GetMapping("/empresas/{id}/participacao.xlsx")
	ResponseEntity<byte[]> participacaoXlsx(@PathVariable Long id, AcessoRhService.Gestor gestor,
			HttpServletRequest request) {
		empresaDo(gestor, id);
		var arquivo = participacao.planilha(id);
		acessos.registrar(gestor, id, AtividadeRh.Tipo.BAIXOU_PARTICIPACAO_XLSX,
				request.getHeader(HttpHeaders.USER_AGENT));
		return arquivo(arquivo.nome(), XLSX, arquivo.conteudo());
	}

	// ------------------------------------------------------------------ gerenciar os líderes
	// Todo gestor pode. Quem já respondeu fica travado (ConviteService.travarQuemRespondeu) e cada
	// ação fica na auditoria com o e-mail do gestor (QuemEsta, pelo cookie do RH).

	/** Cargos, se dá para enviar e-mail, quantos não receberam o convite e quem foi retirado. */
	@GetMapping("/empresas/{id}/gestao")
	ConviteService.GestaoRh gestao(@PathVariable Long id, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		return convites.gestaoRh(id);
	}

	@GetMapping("/empresas/{id}/lideres/{lider}")
	ConviteService.LiderEditavel lider(@PathVariable Long id, @PathVariable Long lider, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		return convites.liderParaRh(id, lider);
	}

	/** Cadastra um líder ({ origem: "painel" }) ou os revisados da planilha. */
	@PostMapping("/empresas/{id}/lideres")
	@ResponseStatus(HttpStatus.CREATED)
	Criados criar(@PathVariable Long id, @Valid @RequestBody CriarConvites pedido, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		return convites.criar(id, pedido);
	}

	/** Lê a planilha para a revisão (não grava nada). */
	@PostMapping(path = "/empresas/{id}/lideres/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	Importacao importar(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo,
			AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		try {
			return convites.importar(id, arquivo.getOriginalFilename(), arquivo.getBytes());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@GetMapping("/empresas/{id}/lideres/modelo.xlsx")
	ResponseEntity<byte[]> modelo(@PathVariable Long id, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		return arquivo(convites.nomeDoModelo(id), XLSX, convites.modelo(id));
	}

	/** O e-mail de convite como o líder recebe, para conferir antes de enviar. */
	@GetMapping(path = "/empresas/{id}/lideres/email-previa", produces = MediaType.TEXT_HTML_VALUE)
	String previaEmail(@PathVariable Long id, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		return convites.previaEmail(id);
	}

	/** Envia o convite a todos que ainda não receberam (em segundo plano). */
	@PostMapping("/empresas/{id}/lideres/enviar")
	EnvioIniciado enviar(@PathVariable Long id, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		return convites.enviar(id, new Envio(List.of()));
	}

	@PutMapping("/empresas/{id}/lideres/{lider}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void editar(@PathVariable Long id, @PathVariable Long lider, @RequestBody NovoConvite pedido,
			AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		convites.editarPeloRh(id, lider, pedido);
	}

	/**
	 * O e-mail da plataforma para um líder, agora, com a mensagem da situação dele (convite ou
	 * lembrete "continue de onde parou"). Responde depois que o e-mail saiu.
	 */
	@PostMapping("/empresas/{id}/lideres/{lider}/enviar")
	ConviteService.CobrancaFeita enviarUm(@PathVariable Long id, @PathVariable Long lider, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		return convites.cobrarPeloRh(id, lider);
	}

	/** O e-mail que este líder receberia pela plataforma agora (convite ou lembrete), para conferir. */
	@GetMapping(path = "/empresas/{id}/lideres/{lider}/email-previa", produces = MediaType.TEXT_HTML_VALUE)
	String previaDoLider(@PathVariable Long id, @PathVariable Long lider, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		return convites.previaParaRh(id, lider);
	}

	/** "Lembrar todos que faltam" pela plataforma: cada um recebe a mensagem da sua situação. */
	@PostMapping("/empresas/{id}/lideres/lembrar")
	ConviteService.LembreteTodos lembrarTodos(@PathVariable Long id, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		return convites.lembrarTodosPeloRh(id);
	}

	/** O RH abriu a mensagem no próprio e-mail ou no WhatsApp: fica na linha do tempo dos líderes. */
	@PostMapping("/empresas/{id}/lideres/contato")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void contato(@PathVariable Long id, @Valid @RequestBody RhDtos.Contato pedido, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		convites.registrarContatoPeloRh(id, pedido.ids(), pedido.canal(), pedido.via());
	}

	@PostMapping("/empresas/{id}/lideres/{lider}/retirar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void retirar(@PathVariable Long id, @PathVariable Long lider, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		convites.retirarPeloRh(id, lider);
	}

	@PostMapping("/empresas/{id}/lideres/{lider}/devolver")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void devolver(@PathVariable Long id, @PathVariable Long lider, AcessoRhService.Gestor gestor) {
		empresaDo(gestor, id);
		convites.devolverPeloRh(id, lider);
	}

	// ------------------------------------------------------------------ apoio

	/** Só as empresas da lista de gestores deste e-mail; as outras não existem para ele. */
	private Empresa empresaDo(AcessoRhService.Gestor gestor, Long id) {
		if (!gestor.acompanha(id)) {
			throw new NaoEncontradoException("Empresa não encontrada.");
		}
		return empresas.findById(id).orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
	}

	private static ResponseEntity<byte[]> arquivo(String nome, MediaType tipo, byte[] conteudo) {
		if (conteudo == null) {
			throw RegraNegocioException.conflito("O arquivo não ficou pronto. Tente de novo.");
		}
		return ResponseEntity.ok()
			.contentType(tipo)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(nome, StandardCharsets.UTF_8).build().toString())
			.cacheControl(CacheControl.noStore())
			.body(conteudo);
	}

}
