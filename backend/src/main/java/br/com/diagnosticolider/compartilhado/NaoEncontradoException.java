package br.com.diagnosticolider.compartilhado;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class NaoEncontradoException extends ErrorResponseException {

	public NaoEncontradoException(String detalhe) {
		super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detalhe), null);
	}

}
