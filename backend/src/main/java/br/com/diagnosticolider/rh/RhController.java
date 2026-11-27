package br.com.diagnosticolider.rh;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.diagnosticolider.acompanhamento.ResultadoDaEmpresa;
import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import br.com.diagnosticolider.convite.ParticipacaoService;
import br.com.diagnosticolider.diagnostico.DiagnosticoRepository;
import br.com.diagnosticolider.diagnostico.StatusDiagnostico;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.empresa.EmpresaRepository;
import br.com.diagnosticolider.empresa.LogomarcaService;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados;
import br.com.diagnosticolider.relatorio.RelatorioPdfService;
import br.com.diagnosticolider.rh.RhDtos.Entrar;
import br.com.diagnosticolider.rh.RhDtos.GestorDto;
import br.com.diagnosticolider.rh.RhDtos.PainelRh;
import br.com.diagnosticolider.rh.RhDtos.PedirCodigo;
import br.com.diagnosticolider.rh.RhDtos.ResultadoRh;
import br.com.diagnosticolider.rh.RhDtos.Sessao;
import jakarta.servlet.http.HttpServletRequest;
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

	RhController(AcessoRhService acessos, ParticipacaoService participacao, ResultadoDaEmpresa resultados,
			RelatorioPdfService pdf, EmpresaRepository empresas, DiagnosticoRepository diagnosticos,
			LogomarcaService logomarcas, Clock clock) {
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
		acessos.pedirCodigo(pedido.email(), request.getRemoteAddr());
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
