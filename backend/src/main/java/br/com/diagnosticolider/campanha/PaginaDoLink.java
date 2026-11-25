package br.com.diagnosticolider.campanha;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import br.com.diagnosticolider.relatorio.PreviaLinkService;

/**
 * O index.html do Angular com as tags de prévia (Open Graph) de um link: quem monta a prévia no
 * WhatsApp, LinkedIn, Telegram ou iMessage não roda JavaScript, só lê essas tags.
 */
@Component
public class PaginaDoLink {

	private static final Resource INDEX = new ClassPathResource("static/index.html");

	/** Tags sociais do index.html estático: saem e entram as do link pedido. */
	private static final Pattern TAGS_SOCIAIS = Pattern.compile(
			"\\s*<meta\\s+(?:property=\"og:[^\"]*\"|name=\"(?:description|twitter:[^\"]*)\")[^>]*>",
			Pattern.CASE_INSENSITIVE);

	private volatile String indexSemTags;

	/** A página com as tags; 404 em desenvolvimento, quando quem serve o front é o "ng serve". */
	public ResponseEntity<String> responder(String tags) {
		String index = indexSemTags();
		if (index == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noCache())
			.contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
			.body(index.replace("</head>", tags + "</head>"));
	}

	public static String caminhoImagem(String codigo, String versao) {
		return "/api/public/campanhas/" + codigo + "/previa-" + versao + ".jpg";
	}

	/** As tags Open Graph e do X/Twitter. Sem imagem quando o link não existe. */
	public static String tags(String endereco, String titulo, String descricao, String empresa, String imagem) {
		var sb = new StringBuilder("\n");
		meta(sb, "name", "description", descricao);
		meta(sb, "property", "og:site_name", "Diagnóstico do Líder");
		meta(sb, "property", "og:locale", "pt_BR");
		meta(sb, "property", "og:type", "website");
		meta(sb, "property", "og:url", endereco);
		meta(sb, "property", "og:title", titulo);
		meta(sb, "property", "og:description", descricao);
		meta(sb, "name", "twitter:title", titulo);
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
