package br.com.diagnosticolider.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.diagnosticolider.acompanhamento.ResultadoDaEmpresa;
import br.com.diagnosticolider.admin.EmpresaDtos.Disponibilidade;
import br.com.diagnosticolider.admin.EmpresaDtos.EditarEmpresa;
import br.com.diagnosticolider.admin.EmpresaDtos.EmpresaDetalhe;
import br.com.diagnosticolider.admin.EmpresaDtos.EmpresaResumo;
import br.com.diagnosticolider.admin.EmpresaDtos.NovaEmpresa;
import br.com.diagnosticolider.admin.EmpresaDtos.PreviaTema;
import br.com.diagnosticolider.empresa.LogomarcaService;
import br.com.diagnosticolider.relatorio.RelatorioEmpresaDados;
import br.com.diagnosticolider.relatorio.RelatorioPdfService;

/** Empresas e os seus links. Exige usuário logado, como todo /api/admin. */
@RestController
@RequestMapping("/api/admin/empresas")
class AdminEmpresaController {

	private final AdminEmpresaService service;

	private final ResultadoDaEmpresa resultados;

	private final RelatorioPdfService pdf;

	private final LogomarcaService logomarcas;

	AdminEmpresaController(AdminEmpresaService service, ResultadoDaEmpresa resultados, RelatorioPdfService pdf,
			LogomarcaService logomarcas) {
		this.service = service;
		this.resultados = resultados;
		this.pdf = pdf;
		this.logomarcas = logomarcas;
	}

	@GetMapping
	List<EmpresaResumo> listar() {
		return service.listar();
	}

	@GetMapping("/{id}")
	EmpresaDetalhe detalhe(@PathVariable Long id) {
		return service.detalhe(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	EmpresaDetalhe cadastrar(@RequestBody @Validated NovaEmpresa pedido) {
		return service.cadastrar(pedido);
	}

	/** Nome e cores. O link não muda. */
	@PutMapping("/{id}")
	EmpresaDetalhe editar(@PathVariable Long id, @RequestBody @Validated EditarEmpresa pedido) {
		return service.editar(id, pedido);
	}

	/** Logomarca: o painel envia um PNG já preparado (reduzido e sem bordas sobrando). */
	@PutMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	EmpresaDetalhe enviarLogo(@PathVariable Long id, @RequestPart("arquivo") MultipartFile arquivo) throws IOException {
		logomarcas.gravar(id, arquivo.getBytes());
		return service.detalhe(id);
	}

	@DeleteMapping("/{id}/logo")
	EmpresaDetalhe removerLogo(@PathVariable Long id) {
		logomarcas.remover(id);
		return service.detalhe(id);
	}

	/** Confere o endereço do link enquanto o administrador digita. */
	@GetMapping("/disponibilidade")
	Disponibilidade disponibilidade(@RequestParam String codigo) {
		return service.disponibilidade(codigo);
	}

	/** Paleta gerada a partir das cores, para a prévia do cadastro (não grava nada). */
	@GetMapping("/tema")
	PreviaTema tema(@RequestParam(required = false) String primaria, @RequestParam(required = false) String secundaria) {
		return service.previa(primaria, secundaria);
	}

	/**
	 * Relatório consolidado em PDF, sem nomes; com ?visualizar=true abre no navegador. É o mesmo
	 * resultado que o RH vê (a última foto: muda a cada 3 respostas novas).
	 */
	@GetMapping("/{id}/relatorio.pdf")
	ResponseEntity<byte[]> relatorio(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean visualizar) {
		RelatorioEmpresaDados dados = resultados.relatorio(id);
		var disposicao = (visualizar ? ContentDisposition.inline() : ContentDisposition.attachment())
			.filename(dados.nomeArquivo(), StandardCharsets.UTF_8)
			.build();
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
			.cacheControl(CacheControl.noStore())
			.body(pdf.gerar(dados));
	}

	/** O mesmo relatório em HTML, para ajustar o layout no navegador. */
	@GetMapping(value = "/{id}/relatorio.html", produces = MediaType.TEXT_HTML_VALUE)
	String relatorioHtml(@PathVariable Long id) {
		return pdf.html(resultados.relatorio(id));
	}

}
