package br.com.diagnosticolider.notificacao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.com.diagnosticolider.config.AppProperties;
import br.com.diagnosticolider.empresa.LogomarcaService;
import br.com.diagnosticolider.notificacao.EmailSender.Anexo;
import br.com.diagnosticolider.notificacao.EmailSender.Mensagem;

/**
 * A logo da empresa vai dentro do e-mail (imagem embutida, {@code cid:}), não por endereço. O
 * Outlook corporativo bloqueia imagem de fora até o leitor clicar em "Baixar imagens", e o Gmail não
 * alcança o localhost do desenvolvimento. Os templates continuam com o endereço público (as prévias
 * do painel abrem no navegador); a troca acontece só na hora de enviar.
 */
@Component
class LogoEmbutida {

	private static final Logger log = LoggerFactory.getLogger(LogoEmbutida.class);

	/** Lê o PNG de uma versão da logo (o {@link LogomarcaService}; nos testes, um mapa). */
	interface Leitor {

		Optional<byte[]> ler(Long empresaId, int versao);

	}

	private final Leitor logomarcas;

	/** O endereço que os templates usam: {urlPublica}/api/public/empresas/{id}/logo-v{versao}.png. */
	private final Pattern endereco;

	@Autowired
	LogoEmbutida(LogomarcaService logomarcas, AppProperties propriedades) {
		this(logomarcas::ler, propriedades.urlPublica());
	}

	LogoEmbutida(Leitor logomarcas, String urlPublica) {
		this.logomarcas = logomarcas;
		String base = urlPublica.replaceAll("/+$", "");
		this.endereco = Pattern.compile(Pattern.quote(base) + "/api/public/empresas/(\\d+)/logo-v(\\d+)\\.png");
	}

	/**
	 * Troca o endereço da logo por {@code cid:} e anexa a imagem como embutida. Se a logo não puder
	 * ser lida (bucket fora do ar), o endereço fica: o e-mail sai do mesmo jeito.
	 */
	Mensagem embutir(Mensagem mensagem) {
		Map<String, Anexo> lidas = new LinkedHashMap<>();
		String html = endereco.matcher(mensagem.html()).replaceAll(achou -> {
			String cid = "logo-" + achou.group(1) + "-v" + achou.group(2);
			Anexo logo = lidas.computeIfAbsent(cid,
					c -> ler(Long.parseLong(achou.group(1)), Integer.parseInt(achou.group(2)), c));
			return Matcher.quoteReplacement(logo == null ? achou.group() : "cid:" + cid);
		});
		List<Anexo> embutidas = lidas.values().stream().filter(Objects::nonNull).toList();
		if (embutidas.isEmpty()) {
			return mensagem;
		}
		List<Anexo> anexos = new ArrayList<>(embutidas);
		anexos.addAll(mensagem.anexos());
		return new Mensagem(mensagem.para(), mensagem.assunto(), html, anexos);
	}

	private Anexo ler(Long empresaId, int versao, String cid) {
		try {
			return logomarcas.ler(empresaId, versao).map(png -> new Anexo("logo.png", png, cid)).orElse(null);
		}
		catch (RuntimeException e) {
			log.warn("A logo {} não entrou no e-mail (vai pelo endereço): {}", cid, e.getMessage());
			return null;
		}
	}

}
