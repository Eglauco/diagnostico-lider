package br.com.diagnosticolider.convite;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.com.diagnosticolider.campanha.PaginaDoLink;
import br.com.diagnosticolider.relatorio.PreviaLinkService;

/**
 * A página do link pessoal com a prévia personalizada ("Ana, em que ponto da curva…"). Abrir a
 * página não conta como acesso: quem conta é a própria página, no aparelho do líder, porque o
 * WhatsApp também abre o link para montar a prévia.
 */
@Controller
class ConvitePaginaController {

	private final ConviteRepository convites;

	private final PreviaLinkService previas;

	private final PaginaDoLink pagina;

	ConvitePaginaController(ConviteRepository convites, PreviaLinkService previas, PaginaDoLink pagina) {
		this.convites = convites;
		this.previas = previas;
		this.pagina = pagina;
	}

	@GetMapping(path = "/i/{token}", produces = MediaType.TEXT_HTML_VALUE)
	ResponseEntity<String> pagina(@PathVariable String token) {
		String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
		String endereco = base + "/i/" + token;
		return pagina.responder(convites.findByToken(token).filter(c -> !c.inativo()).map(c -> {
			var empresa = c.getCampanha().getEmpresa();
			String imagem = base + PaginaDoLink.caminhoImagem(c.getCampanha().getCodigo(), previas.versao(empresa));
			return PaginaDoLink.tags(endereco, c.primeiroNome() + ", em que ponto da curva está a sua carreira?",
					empresa.getNome()
							+ " convida você para o Diagnóstico de Carreira do Líder. O link é só seu e já vem com os seus dados.",
					empresa.getNome(), imagem);
		}).orElseGet(() -> PaginaDoLink.tags(endereco, "Diagnóstico de carreira do líder",
				"Este convite não está mais disponível.", null, null)));
	}

}
