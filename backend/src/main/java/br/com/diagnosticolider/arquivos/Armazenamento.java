package br.com.diagnosticolider.arquivos;

import java.util.Optional;

/**
 * Onde ficam os arquivos enviados (hoje, as logomarcas das empresas). Os caminhos são relativos à
 * pasta do ambiente: "empresas/12/logo-v3.png" vira "producao/empresas/12/logo-v3.png" no bucket.
 * Os arquivos são gravados uma vez, com nome versionado: nunca mudam depois de gravados.
 */
public interface Armazenamento {

	void gravar(String caminho, byte[] conteudo, String tipo);

	Optional<byte[]> ler(String caminho);

	void apagar(String caminho);

	/**
	 * Endereço para o navegador baixar o arquivo direto do armazenamento, sem passar pelo backend.
	 * Vazio quando não há como (arquivos no disco local): aí o backend serve o arquivo.
	 */
	default Optional<String> urlDireta(String caminho) {
		return Optional.empty();
	}

	/** Deixa o armazenamento pronto na subida (pasta do ambiente e, no bucket, o CORS). */
	void preparar();

	/** Para os logs: onde os arquivos estão indo. */
	String descricao();

}
