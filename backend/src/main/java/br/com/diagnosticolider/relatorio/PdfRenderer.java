package br.com.diagnosticolider.relatorio;

/**
 * Converte HTML em PDF (relatórios) ou em imagem PNG (prévia do link). As duas implementações
 * usam Chromium, então o resultado é o mesmo.
 */
public interface PdfRenderer {

	byte[] renderizar(String html);

	/** "Fotografa" a página numa imagem PNG do tamanho pedido, em pixels. */
	byte[] capturar(String html, int largura, int altura);

}
