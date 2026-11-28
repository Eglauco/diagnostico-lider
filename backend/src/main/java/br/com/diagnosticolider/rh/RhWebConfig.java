package br.com.diagnosticolider.rh;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.diagnosticolider.acesso.QuemEsta;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A página do RH na API: quem está logado chega aos métodos como {@link AcessoRhService.Gestor}
 * (sem cookie válido, 401), e nenhuma resposta fica em cache no navegador.
 */
@Configuration
class RhWebConfig implements WebMvcConfigurer {

	private final AcessoRhService acessos;

	RhWebConfig(AcessoRhService acessos) {
		this.acessos = acessos;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new HandlerMethodArgumentResolver() {

			@Override
			public boolean supportsParameter(MethodParameter parametro) {
				return parametro.getParameterType() == AcessoRhService.Gestor.class;
			}

			@Override
			public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer mav, NativeWebRequest web,
					WebDataBinderFactory binders) {
				var request = web.getNativeRequest(HttpServletRequest.class);
				var response = web.getNativeResponse(HttpServletResponse.class);
				String token = CookieRh.ler(request);
				AcessoRhService.Gestor gestor = acessos.gestor(token).orElse(null);
				if (gestor == null) {
					if (parametro.isOptional()) {
						return null;
					}
					throw RegraNegocioException.comStatus(HttpStatus.UNAUTHORIZED,
							"Entre com o seu e-mail para acompanhar a avaliação.");
				}
				if (gestor.renovado() && response != null) {
					// Usou de novo: mais 30 dias neste aparelho.
					CookieRh.gravar(request, response, token, AcessoRhService.LEMBRAR);
				}
				// O que o gestor fizer nesta requisição (cadastrar, editar, enviar) fica com o e-mail dele.
				request.setAttribute(QuemEsta.AUTOR_RH, QuemEsta.PREFIXO_RH + gestor.email());
				return gestor;
			}

		});
	}

	@Override
	public void addInterceptors(InterceptorRegistry registro) {
		registro.addInterceptor(new HandlerInterceptor() {

			@Override
			public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
				response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
				return true;
			}

		}).addPathPatterns(CookieRh.CAMINHO + "/**");
	}

}
