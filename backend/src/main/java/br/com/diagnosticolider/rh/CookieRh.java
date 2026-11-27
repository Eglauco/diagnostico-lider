package br.com.diagnosticolider.rh;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * O cookie do acesso do RH: HttpOnly (o JavaScript não lê), SameSite=Lax e só para a API da
 * página do RH. Separado da sessão do painel: quem acompanha não entra no painel.
 */
final class CookieRh {

	static final String NOME = "ACESSO_RH";

	static final String CAMINHO = "/api/acompanhar";

	private CookieRh() {
	}

	static String ler(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie c : cookies) {
			if (NOME.equals(c.getName())) {
				return c.getValue();
			}
		}
		return null;
	}

	static void gravar(HttpServletRequest request, HttpServletResponse response, String token, Duration validade) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, token, validade).toString());
	}

	static void apagar(HttpServletRequest request, HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, "", Duration.ZERO).toString());
	}

	private static ResponseCookie cookie(HttpServletRequest request, String valor, Duration validade) {
		return ResponseCookie.from(NOME, valor)
			.httpOnly(true)
			.secure(request.isSecure())
			.sameSite("Lax")
			.path(CAMINHO)
			.maxAge(validade)
			.build();
	}

}
