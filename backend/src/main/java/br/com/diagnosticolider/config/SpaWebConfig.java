package br.com.diagnosticolider.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.util.DigestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serve o build do Angular (copiado para classpath:/static no Dockerfile) e devolve o
 * index.html para qualquer rota do front (/c/demo, /resultado/...), deixando o roteamento
 * com o Angular. Rotas /api e arquivos inexistentes continuam respondendo 404.
 *
 * <p>"/" e "/c/{codigo}" saem do PreviaLinkController: o mesmo index.html, mas com as tags
 * da prévia do link (WhatsApp) daquele link.
 *
 * <p>Cache: o navegador sempre confere (no-cache) e a conferência é pelo conteúdo (ETag), não
 * pela data. Os arquivos do jar saem todos com a mesma data (1980, build reproduzível): pela
 * data, o servidor respondia "não mudou" depois de um deploy, o navegador ficava com o
 * index.html velho, que pede scripts que já não existem, e a tela abria em branco.
 */
@Configuration(proxyBeanMethods = false)
class SpaWebConfig implements WebMvcConfigurer {

	private static final Resource INDEX = new ClassPathResource("static/index.html");

	/** Os arquivos do jar não mudam enquanto o servidor roda: cada um é lido uma vez. */
	private final Map<String, String> etags = new ConcurrentHashMap<>();

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
			.addResourceLocations("classpath:/static/")
			.setCacheControl(CacheControl.noCache())
			.setUseLastModified(false)
			.setEtagGenerator(arquivo -> etags.computeIfAbsent(arquivo.getDescription(), d -> etag(arquivo)))
			.resourceChain(true)
			.addResolver(new PathResourceResolver() {
				@Override
				protected Resource getResource(String caminho, Resource local) throws IOException {
					Resource solicitado = local.createRelative(caminho);
					if (solicitado.exists() && solicitado.isReadable()) {
						return solicitado;
					}
					boolean rotaDoFront = !caminho.startsWith("api/") && !caminho.contains(".");
					return rotaDoFront && INDEX.exists() ? INDEX : null;
				}
			});
	}

	/** Resumo do conteúdo: muda quando o arquivo muda, e só nesse caso. */
	static String etag(Resource arquivo) {
		try (InputStream conteudo = arquivo.getInputStream()) {
			return DigestUtils.md5DigestAsHex(conteudo);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
