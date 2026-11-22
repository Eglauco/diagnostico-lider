package br.com.diagnosticolider.relatorio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gera o PDF com o Chrome/Edge instalado na máquina (modo headless). Pensado para
 * desenvolvimento local sem Docker; em produção use o Gotenberg.
 */
class ChromePdfRenderer implements PdfRenderer {

	private static final Logger log = LoggerFactory.getLogger(ChromePdfRenderer.class);

	private static final List<String> CAMINHOS_CONHECIDOS = List.of(
			"C:/Program Files/Google/Chrome/Application/chrome.exe",
			"C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
			"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
			"C:/Program Files/Microsoft/Edge/Application/msedge.exe",
			"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", "/usr/bin/chromium",
			"/usr/bin/chromium-browser", "/usr/bin/google-chrome");

	/** Cada Chrome headless consome memória; limita quantos rodam ao mesmo tempo. */
	private final Semaphore simultaneos = new Semaphore(2);

	private final Optional<Path> executavel;

	ChromePdfRenderer(String caminhoConfigurado) {
		this.executavel = localizar(caminhoConfigurado);
		executavel.ifPresentOrElse(p -> log.info("PDF via Chrome headless: {}", p),
				() -> log.warn("Chrome/Edge não encontrado. Defina CHROME_PATH ou use PDF_MOTOR=gotenberg."));
	}

	@Override
	public byte[] renderizar(String html) {
		Path chrome = executavel.orElseThrow(() -> new IllegalStateException(
				"Chrome/Edge não encontrado para gerar o PDF. Defina CHROME_PATH ou use PDF_MOTOR=gotenberg."));
		simultaneos.acquireUninterruptibly();
		Path pasta = null;
		try {
			pasta = Files.createTempDirectory("relatorio-");
			Path entrada = pasta.resolve("index.html");
			Path saida = pasta.resolve("relatorio.pdf");
			Files.writeString(entrada, html, StandardCharsets.UTF_8);

			Process processo = new ProcessBuilder(chrome.toString(), "--headless", "--disable-gpu", "--no-sandbox",
					"--no-first-run", "--no-default-browser-check", "--disable-extensions", "--no-pdf-header-footer",
					"--user-data-dir=" + pasta.resolve("perfil"), "--print-to-pdf=" + saida,
					entrada.toUri().toString())
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();

			if (!processo.waitFor(60, TimeUnit.SECONDS)) {
				processo.destroyForcibly();
				throw new IllegalStateException("O Chrome não gerou o PDF em 60 segundos.");
			}
			if (!Files.exists(saida)) {
				throw new IllegalStateException("O Chrome terminou sem gerar o PDF (código " + processo.exitValue() + ").");
			}
			return Files.readAllBytes(saida);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Geração do PDF interrompida.", e);
		}
		finally {
			simultaneos.release();
			apagar(pasta);
		}
	}

	private static Optional<Path> localizar(String configurado) {
		if (configurado != null && !configurado.isBlank()) {
			return Optional.of(Path.of(configurado));
		}
		return CAMINHOS_CONHECIDOS.stream().map(Path::of).filter(Files::isExecutable).findFirst();
	}

	/** Melhor esforço: no Windows o perfil do Chrome pode ficar travado por alguns instantes. */
	private static void apagar(Path pasta) {
		if (pasta == null) {
			return;
		}
		try (Stream<Path> arquivos = Files.walk(pasta)) {
			arquivos.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
		catch (IOException e) {
			log.debug("Não foi possível apagar {}", pasta, e);
		}
	}

}
