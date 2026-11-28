package br.com.diagnosticolider.rh;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.diagnosticolider.campanha.LinksDaEmpresa;
import jakarta.validation.constraints.NotBlank;

/** No painel: quem da empresa acompanha pela página do RH, quando entrou, e o "Mandar acesso". */
@RestController
@RequestMapping("/api/admin/empresas/{id}/acesso-rh")
class AdminAcessoRhController {

	private final AcessoRhService acessos;

	private final LinksDaEmpresa links;

	AdminAcessoRhController(AcessoRhService acessos, LinksDaEmpresa links) {
		this.acessos = acessos;
		this.links = links;
	}

	record AcessosRh(String pagina, List<AcessoRhService.AcessoDoGestor> gestores) {
	}

	record MandarAcesso(@NotBlank(message = "Escolha o gestor.") String email) {
	}

	@GetMapping
	AcessosRh listar(@PathVariable Long id) {
		return new AcessosRh(links.paginaDoRh(id), acessos.acessosDaEmpresa(id));
	}

	@PostMapping("/convite")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void mandar(@PathVariable Long id, @RequestBody @Validated MandarAcesso pedido) {
		acessos.convidar(id, pedido.email());
	}

	@GetMapping(value = "/convite-previa", produces = MediaType.TEXT_HTML_VALUE)
	String previa(@PathVariable Long id) {
		return acessos.previaConvite(id);
	}

}
