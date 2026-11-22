package br.com.diagnosticolider.relatorio;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/diagnosticos/{id}")
class RelatorioController {

	private final RelatorioDadosService dados;

	private final RelatorioPdfService pdf;

	RelatorioController(RelatorioDadosService dados, RelatorioPdfService pdf) {
		this.dados = dados;
		this.pdf = pdf;
	}

	/** Baixa o PDF; com ?visualizar=true abre no navegador em vez de baixar. */
	@GetMapping("/relatorio.pdf")
	ResponseEntity<byte[]> pdf(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean visualizar) {
		RelatorioDados relatorio = dados.montar(id);
		var disposicao = (visualizar ? ContentDisposition.inline() : ContentDisposition.attachment())
			.filename(relatorio.nomeArquivo(), StandardCharsets.UTF_8)
			.build();
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
			.cacheControl(CacheControl.noStore())
			.body(pdf.gerar(relatorio));
	}

	/** O mesmo relatório em HTML, para ajustar o layout sem gerar PDF. */
	@GetMapping(value = "/relatorio.html", produces = MediaType.TEXT_HTML_VALUE)
	String html(@PathVariable UUID id) {
		return pdf.html(dados.montar(id));
	}

}
