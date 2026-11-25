package br.com.diagnosticolider.admin;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.diagnosticolider.admin.AdminDtos.Detalhe;
import br.com.diagnosticolider.admin.AdminDtos.ItemLista;
import br.com.diagnosticolider.admin.AdminDtos.Pagina;
import br.com.diagnosticolider.admin.AdminDtos.Painel;
import br.com.diagnosticolider.convite.ConviteService;
import br.com.diagnosticolider.diagnostico.StatusDiagnostico;
import br.com.diagnosticolider.relatorio.RelatorioDados;
import br.com.diagnosticolider.relatorio.RelatorioDadosService;
import br.com.diagnosticolider.relatorio.RelatorioPdfService;

/**
 * API do painel administrativo.
 *
 * <p>Exige usuário logado (SegurancaConfig): os dados aqui têm nome, e-mail e WhatsApp dos líderes.
 */
@RestController
@RequestMapping("/api/admin")
class AdminController {

	private final AdminService service;

	private final RelatorioDadosService relatorioDados;

	private final RelatorioPdfService relatorioPdf;

	private final ConviteService convites;

	AdminController(AdminService service, RelatorioDadosService relatorioDados, RelatorioPdfService relatorioPdf,
			ConviteService convites) {
		this.convites = convites;
		this.service = service;
		this.relatorioDados = relatorioDados;
		this.relatorioPdf = relatorioPdf;
	}

	@GetMapping("/painel")
	Painel painel(@RequestParam(required = false) String campanha, @RequestParam(required = false) String cargo) {
		return service.painel(campanha, cargo);
	}

	@GetMapping("/diagnosticos")
	Pagina<ItemLista> listar(@RequestParam(required = false) String busca,
			@RequestParam(required = false) StatusDiagnostico status, @RequestParam(required = false) String faixa,
			@RequestParam(required = false) String campanha, @RequestParam(required = false) String cargo,
			@RequestParam(defaultValue = "false") boolean inativos, @RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "20") int tamanho) {
		return service.listar(busca, status, faixa, campanha, cargo, inativos, pagina, tamanho);
	}

	/** Tira o diagnóstico de todos os cálculos (e inativa o convite dele, se houver). */
	@PostMapping("/diagnosticos/{id}/inativar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void inativar(@PathVariable UUID id) {
		convites.inativarDiagnostico(id);
	}

	@PostMapping("/diagnosticos/{id}/reativar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reativar(@PathVariable UUID id) {
		convites.reativarDiagnostico(id);
	}

	@GetMapping("/diagnosticos/{id}")
	Detalhe detalhe(@PathVariable UUID id) {
		return service.detalhe(id);
	}

	@GetMapping("/diagnosticos/{id}/relatorio.pdf")
	ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
		RelatorioDados dados = relatorioDados.montar(id);
		convites.registrarPdfBaixadoNoPainel(id);
		var disposicao = ContentDisposition.attachment().filename(dados.nomeArquivo(), StandardCharsets.UTF_8).build();
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
			.cacheControl(CacheControl.noStore())
			.body(relatorioPdf.gerar(dados));
	}

}
