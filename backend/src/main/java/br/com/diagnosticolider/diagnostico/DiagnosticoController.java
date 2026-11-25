package br.com.diagnosticolider.diagnostico;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.diagnosticolider.diagnostico.DiagnosticoDtos.DiagnosticoDto;
import br.com.diagnosticolider.diagnostico.DiagnosticoDtos.IniciarRequest;
import br.com.diagnosticolider.diagnostico.DiagnosticoDtos.ResponderRequest;
import br.com.diagnosticolider.diagnostico.DiagnosticoDtos.ResultadoDto;

/** API pública: o líder não tem login, o UUID do diagnóstico é a chave de acesso. */
@RestController
@RequestMapping("/api/public")
class DiagnosticoController {

	private final DiagnosticoService service;

	DiagnosticoController(DiagnosticoService service) {
		this.service = service;
	}

	@PostMapping("/campanhas/{codigo}/diagnosticos")
	ResponseEntity<DiagnosticoDto> iniciar(@PathVariable String codigo, @Validated @RequestBody IniciarRequest req) {
		DiagnosticoDto criado = service.iniciar(codigo, req);
		return ResponseEntity.created(URI.create("/api/public/diagnosticos/" + criado.id())).body(criado);
	}

	@GetMapping("/diagnosticos/{id}")
	DiagnosticoDto buscar(@PathVariable UUID id) {
		return service.buscar(id);
	}

	@PutMapping("/diagnosticos/{id}/respondente")
	DiagnosticoDto atualizarRespondente(@PathVariable UUID id, @Validated @RequestBody IniciarRequest req) {
		return service.atualizarRespondente(id, req);
	}

	@PutMapping("/diagnosticos/{id}/respostas/{perguntaId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void responder(@PathVariable UUID id, @PathVariable Long perguntaId,
			@Validated @RequestBody ResponderRequest req) {
		service.responder(id, perguntaId, req.opcoes());
	}

	@PostMapping("/diagnosticos/{id}/finalizar")
	ResultadoDto finalizar(@PathVariable UUID id) {
		return service.finalizar(id);
	}

	@GetMapping("/diagnosticos/{id}/resultado")
	ResultadoDto resultado(@PathVariable UUID id) {
		return service.resultado(id);
	}

	record UsoDoPdf(String acao) {
	}

	/** Auditoria: o líder compartilhou ({"acao":"compartilhou"}) ou baixou ({"acao":"baixou"}) o PDF. */
	@PostMapping("/diagnosticos/{id}/pdf-usado")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void pdfUsado(@PathVariable UUID id, @RequestBody UsoDoPdf uso) {
		if (uso != null && ("compartilhou".equals(uso.acao()) || "baixou".equals(uso.acao()))) {
			service.registrarUsoDoPdf(id, "compartilhou".equals(uso.acao()));
		}
	}

}
