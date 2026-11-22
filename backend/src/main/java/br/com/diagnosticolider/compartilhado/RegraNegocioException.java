package br.com.diagnosticolider.compartilhado;

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

	/** O recurso está num estado que não permite a operação (HTTP 409). */
	public static RegraNegocioException conflito(String detalhe) {
		return new RegraNegocioException(HttpStatus.CONFLICT, detalhe);
	}

}
