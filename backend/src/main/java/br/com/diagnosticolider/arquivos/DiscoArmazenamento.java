package br.com.diagnosticolider.arquivos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/** Sem bucket configurado (desenvolvimento sem as credenciais): arquivos numa pasta local. */
class DiscoArmazenamento implements Armazenamento {

	private final Path raiz;

	DiscoArmazenamento(String pastaLocal, String prefixo) {
		this.raiz = Path.of(pastaLocal).toAbsolutePath().normalize().resolve(prefixo);
	}

	@Override
	public void gravar(String caminho, byte[] conteudo, String tipo) {
		try {
			Path arquivo = resolver(caminho);
			Files.createDirectories(arquivo.getParent());
			Files.write(arquivo, conteudo);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public Optional<byte[]> ler(String caminho) {
		try {
			return Optional.of(Files.readAllBytes(resolver(caminho)));
		}
		catch (NoSuchFileException e) {
			return Optional.empty();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void apagar(String caminho) {
		try {
			Files.deleteIfExists(resolver(caminho));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void preparar() {
		try {
			Files.createDirectories(raiz);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public String descricao() {
		return "disco em " + raiz;
	}

	/** Nunca sai da pasta raiz (caminho vem do sistema, mas por garantia). */
	private Path resolver(String caminho) {
		Path arquivo = raiz.resolve(caminho).normalize();
		if (!arquivo.startsWith(raiz)) {
			throw new IllegalArgumentException("Caminho fora da pasta de arquivos: " + caminho);
		}
		return arquivo;
	}

}
