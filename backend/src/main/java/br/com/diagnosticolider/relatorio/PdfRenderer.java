package br.com.diagnosticolider.relatorio;

/** Converte HTML em PDF. As duas implementações usam Chromium, então o resultado é o mesmo. */
public interface PdfRenderer {

	byte[] renderizar(String html);

}
