package br.com.diagnosticolider.acompanhamento;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Os relatórios automáticos ao gestor e os lembretes aos líderes, no painel da empresa. */
@RestController
@RequestMapping("/api/admin/empresas/{id}/acompanhamento")
class AcompanhamentoController {

	private final AcompanhamentoService service;

	AcompanhamentoController(AcompanhamentoService service) {
		this.service = service;
	}

	record Pausa(boolean pausado) {
	}

	@GetMapping
	PainelAcompanhamento painel(@PathVariable Long id) {
		return service.painel(id);
	}

	/** Envia já o relatório do dia (ou o final, se a avaliação terminou). 409 sem gestor ou sem e-mail. */
	@PostMapping("/enviar")
	AcompanhamentoService.Envio enviar(@PathVariable Long id) {
		return service.enviarAgora(id);
	}

	@PutMapping("/pausa")
	PainelAcompanhamento pausar(@PathVariable Long id, @RequestBody Pausa pedido) {
		service.pausar(id, pedido.pausado());
		return service.painel(id);
	}

	@GetMapping(value = "/email-previa", produces = MediaType.TEXT_HTML_VALUE)
	String previa(@PathVariable Long id) {
		return service.previa(id);
	}

}
