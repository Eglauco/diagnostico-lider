package br.com.diagnosticolider.compartilhado;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Requisição bem formada que viola uma regra do domínio (HTTP 422). */
public class RegraNegocioException extends ErrorResponseException {

	public RegraNegocioException(String detalhe) {
		this(HttpStatus.UNPROCESSABLE_CONTENT, detalhe);
	}

	protected RegraNegocioException(HttpStatus status, String detalhe) {
		super(status, ProblemDetail.forStatusAndDetail(status, detalhe), null);
	}

	/** Aponta o campo do formulário que causou o erro: o front mostra a mensagem nele. */
	public RegraNegocioException noCampo(String campo) {
		getBody().setProperty("campos", Map.of(campo, getBody().getDetail()));
		return this;
	}

	/** Erro com outro status HTTP (ex.: 401 no login, 429 por excesso de tentativas). */
	public static RegraNegocioException comStatus(HttpStatus status, String detalhe) {
		return new RegraNegocioException(status, detalhe);
	}

	/** O recurso está num estado que não permite a operação (HTTP 409). */
	public static RegraNegocioException conflito(String detalhe) {
		return new RegraNegocioException(HttpStatus.CONFLICT, detalhe);
	}

}
