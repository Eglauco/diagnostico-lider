package br.com.diagnosticolider.convite;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.diagnosticolider.convite.ConviteDtos.Acesso;
import br.com.diagnosticolider.convite.ConviteDtos.ConvitePublico;
import br.com.diagnosticolider.diagnostico.DiagnosticoDtos.DiagnosticoDto;
import br.com.diagnosticolider.diagnostico.DiagnosticoDtos.IniciarRequest;
import jakarta.validation.Valid;

/** O link pessoal do líder (/i/{token}): os dados para confirmar, o acesso e o início do diagnóstico. */
@RestController
@RequestMapping("/api/public/convites/{token}")
class ConvitePublicoController {

	private final ConviteService service;

	ConvitePublicoController(ConviteService service) {
		this.service = service;
	}

	@GetMapping
	ConvitePublico abrir(@PathVariable String token) {
		return service.abrir(token);
	}

	/** Chamado pela página quando ela carrega no aparelho do líder (a prévia do WhatsApp não roda isso). */
	@PostMapping("/acesso")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void acesso(@PathVariable String token, @RequestBody(required = false) Acesso acesso) {
		service.registrarAcesso(token, acesso == null ? null : acesso.dispositivo());
	}

	@PostMapping("/diagnosticos")
	@ResponseStatus(HttpStatus.CREATED)
	DiagnosticoDto iniciar(@PathVariable String token, @Valid @RequestBody IniciarRequest req) {
		return service.iniciar(token, req);
	}

}
