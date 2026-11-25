package br.com.diagnosticolider.campanha;

import java.time.Duration;
import java.util.Optional;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.relatorio.PreviaLinkService;

/**
 * Prévia do link no WhatsApp (e LinkedIn, Telegram, iMessage): o index.html de "/" e
 * "/c/{codigo}" sai daqui com as tags do link (título, convite com o nome da empresa e a imagem
 * com a logo dela), e a imagem também. O resto do roteamento continua com o Angular (SpaWebConfig).
 */
@Controller
class PreviaLinkController {

	private static final String LINK_GERAL = "geral";

	static final String TITULO = "Em que ponto da curva está a sua carreira?";

	static final String DESCRICAO_GERAL = "Diagnóstico de carreira para líderes. Leva 2 minutos e o resultado chega em PDF no seu e-mail.";

	private final CampanhaRepository campanhas;

	private final PreviaLinkService previas;

	private final PaginaDoLink pagina;

	PreviaLinkController(CampanhaRepository campanhas, PreviaLinkService previas, PaginaDoLink pagina) {
		this.campanhas = campanhas;
		this.previas = previas;
		this.pagina = pagina;
	}

	@GetMapping(path = { "/", "/c/{codigo}" }, produces = MediaType.TEXT_HTML_VALUE)
	ResponseEntity<String> pagina(@PathVariable(required = false) String codigo) {
		String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
		String endereco = base + (codigo == null ? "/" : "/c/" + codigo);
		Optional<Campanha> campanha = campanhas.findByCodigo(codigo == null ? LINK_GERAL : codigo)
			.filter(Campanha::isAtiva);
		return pagina.responder(campanha
			.map(c -> tags(endereco, c.getEmpresa() == null ? null : c.getEmpresa().getNome(),
					base + PaginaDoLink.caminhoImagem(c.getCodigo(), previas.versao(c.getEmpresa()))))
			.orElseGet(() -> tags(endereco, null, null)));
	}

	@GetMapping("/api/public/campanhas/{codigo}/previa-{versao}.jpg")
	ResponseEntity<byte[]> imagem(@PathVariable String codigo, @PathVariable String versao) {
		Empresa empresa = campanhas.findByCodigo(codigo)
			.filter(Campanha::isAtiva)
			.orElseThrow(() -> new NaoEncontradoException("Link não encontrado."))
			.getEmpresa();
		// Endereço da versão atual: imutável (nome, cores ou logo novos mudam o endereço).
		boolean atual = versao.equals(previas.versao(empresa));
		return ResponseEntity.ok()
			.cacheControl(atual ? CacheControl.maxAge(Duration.ofDays(30)).cachePublic() : CacheControl.noCache())
			.contentType(MediaType.IMAGE_JPEG)
			.body(previas.imagem(empresa));
	}

	static String caminhoImagem(String codigo, String versao) {
		return PaginaDoLink.caminhoImagem(codigo, versao);
	}

	/** Tags do link aberto: convite da empresa, ou o texto geral. Sem imagem quando o link não existe. */
	static String tags(String endereco, String empresa, String imagem) {
		String descricao = empresa == null ? DESCRICAO_GERAL
				: empresa + " convida você para o Diagnóstico de Carreira do Líder. Leva 2 minutos e o resultado chega em PDF no seu e-mail.";
		return PaginaDoLink.tags(endereco, TITULO, descricao, empresa, imagem);
	}

}
