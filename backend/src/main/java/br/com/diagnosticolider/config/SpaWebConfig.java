package br.com.diagnosticolider.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
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
 */
@Configuration(proxyBeanMethods = false)
class SpaWebConfig implements WebMvcConfigurer {

	private static final Resource INDEX = new ClassPathResource("static/index.html");

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
			.addResourceLocations("classpath:/static/")
			.setCacheControl(CacheControl.noCache())
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

}
