package br.com.diagnosticolider.campanha;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.relatorio.PreviaLinkService;

/**
 * Prévia do link no WhatsApp (e LinkedIn, Telegram, iMessage). O app que monta a prévia não
 * roda JavaScript: lê as tags Open Graph do HTML. Por isso o index.html do Angular sai daqui
 * com as tags do link (título, descrição e a imagem com a logo da empresa), e não direto da
 * pasta estática. O resto do roteamento continua com o Angular (ver SpaWebConfig).
 */
@Controller
class PreviaLinkController {

	private static final Resource INDEX = new ClassPathResource("static/index.html");

	/** Tags sociais do index.html estático: saem e entram as do link pedido. */
	private static final Pattern TAGS_SOCIAIS = Pattern.compile(
			"\\s*<meta\\s+(?:property=\"og:[^\"]*\"|name=\"(?:description|twitter:[^\"]*)\")[^>]*>",
			Pattern.CASE_INSENSITIVE);

	private static final String LINK_GERAL = "geral";

	static final String TITULO = "Em que ponto da curva está a sua carreira?";

	static final String DESCRICAO_GERAL = "Diagnóstico de carreira para líderes. Leva 2 minutos e o resultado chega em PDF no seu e-mail.";

	private final CampanhaRepository campanhas;

	private final PreviaLinkService previas;

	private volatile String indexSemTags;

	PreviaLinkController(CampanhaRepository campanhas, PreviaLinkService previas) {
		this.campanhas = campanhas;
		this.previas = previas;
	}

	@GetMapping(path = { "/", "/c/{codigo}" }, produces = MediaType.TEXT_HTML_VALUE)
	ResponseEntity<String> pagina(@PathVariable(required = false) String codigo) {
		String index = indexSemTags();
		if (index == null) {
			// Desenvolvimento: quem serve o front é o "ng serve" (porta 4200), não o backend.
			return ResponseEntity.notFound().build();
		}
		String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
		String endereco = base + (codigo == null ? "/" : "/c/" + codigo);
		Optional<Campanha> campanha = campanhas.findByCodigo(codigo == null ? LINK_GERAL : codigo)
			.filter(Campanha::isAtiva);
		String tags = campanha
			.map(c -> tags(endereco, c.getEmpresa() == null ? null : c.getEmpresa().getNome(),
					base + caminhoImagem(c.getCodigo(), previas.versao(c.getEmpresa()))))
			.orElseGet(() -> tags(endereco, null, null));
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noCache())
			.contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
			.body(index.replace("</head>", tags + "</head>"));
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
		return "/api/public/campanhas/" + codigo + "/previa-" + versao + ".jpg";
	}

	/** As tags Open Graph e do X/Twitter do link. Sem imagem quando o link não existe. */
	static String tags(String endereco, String empresa, String imagem) {
		String descricao = empresa == null ? DESCRICAO_GERAL
				: empresa + " convida você para o Diagnóstico de Carreira do Líder. Leva 2 minutos e o resultado chega em PDF no seu e-mail.";
		var sb = new StringBuilder("\n");
		meta(sb, "name", "description", descricao);
		meta(sb, "property", "og:site_name", "Diagnóstico do Líder");
		meta(sb, "property", "og:locale", "pt_BR");
		meta(sb, "property", "og:type", "website");
		meta(sb, "property", "og:url", endereco);
		meta(sb, "property", "og:title", TITULO);
		meta(sb, "property", "og:description", descricao);
		meta(sb, "name", "twitter:title", TITULO);
		meta(sb, "name", "twitter:description", descricao);
		if (imagem != null) {
			String alt = "A curva da carreira (subida, platô e queda) com a pergunta: você está aqui?"
					+ (empresa == null ? "" : " Convite de " + empresa + ".");
			meta(sb, "property", "og:image", imagem);
			meta(sb, "property", "og:image:secure_url", imagem);
			meta(sb, "property", "og:image:type", "image/jpeg");
			meta(sb, "property", "og:image:width", String.valueOf(PreviaLinkService.LARGURA));
			meta(sb, "property", "og:image:height", String.valueOf(PreviaLinkService.ALTURA));
			meta(sb, "property", "og:image:alt", alt);
			meta(sb, "name", "twitter:card", "summary_large_image");
			meta(sb, "name", "twitter:image", imagem);
		}
		return sb.toString();
	}

	private static void meta(StringBuilder sb, String atributo, String nome, String conteudo) {
		sb.append("  <meta ")
			.append(atributo)
			.append("=\"")
			.append(nome)
			.append("\" content=\"")
			.append(HtmlUtils.htmlEscape(conteudo, StandardCharsets.UTF_8.name()))
			.append("\">\n");
	}

	private String indexSemTags() {
		String atual = indexSemTags;
		if (atual == null && INDEX.exists()) {
			try {
				atual = TAGS_SOCIAIS.matcher(INDEX.getContentAsString(StandardCharsets.UTF_8)).replaceAll("");
				indexSemTags = atual;
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return atual;
	}

}
