package br.com.diagnosticolider.campanha;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/campanhas")
class CampanhaController {

	private final CampanhaService service;

	CampanhaController(CampanhaService service) {
		this.service = service;
	}

	@GetMapping("/{codigo}")
	CampanhaPublicaDto buscar(@PathVariable String codigo) {
		return service.buscarPublica(codigo);
	}

}
