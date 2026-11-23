package br.com.diagnosticolider.relatorio;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import br.com.diagnosticolider.empresa.Empresa;
import br.com.diagnosticolider.empresa.LogomarcaService;
import br.com.diagnosticolider.empresa.MarcaEmpresa;

/**
 * Imagem de prévia do link (Open Graph, 1200×630): é o que o WhatsApp mostra quando o RH manda
 * o link para os líderes. No link de uma empresa, leva a logo e as cores dela; no link geral, a
 * identidade do Diagnóstico.
 *
 * <p>Gerar custa um Chromium (~1 s), mas quem busca a imagem é só o app que monta a prévia
 * (uma vez por envio), não cada líder. Fica em memória por versão: nome, cores ou logo novos
 * geram outra versão, com outro endereço, e o WhatsApp busca a imagem nova.
 */
@Service
public class PreviaLinkService {

	public static final int LARGURA = 1200;

	public static final int ALTURA = 630;

	/** Mude quando o desenho da imagem mudar: todos os endereços mudam junto. */
	private static final String DESENHO = "2";

	/** "Você está aqui?" no platô da curva (índice que cai no platô). */
	private static final int INDICE_NO_PLATO = 47;

	private static final int MAXIMO_EM_MEMORIA = 64;

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private final ITemplateEngine templates;

	private final PdfRenderer renderer;

	private final LogomarcaService logomarcas;

	private final Map<String, byte[]> prontas = new ConcurrentHashMap<>();

	PreviaLinkService(ITemplateEngine templates, PdfRenderer renderer, LogomarcaService logomarcas) {
		this.templates = templates;
		this.renderer = renderer;
		this.logomarcas = logomarcas;
	}

	/** Identifica o desenho atual da imagem (vai no endereço dela). */
	public String versao(Empresa empresa) {
		if (empresa == null) {
			return "d" + DESENHO;
		}
		int marca = Objects.hash(empresa.getNome(), empresa.getCorPrimaria(), empresa.getCorSecundaria(),
				empresa.getLogoVersao());
		return "d" + DESENHO + "-" + empresa.getId() + "-" + Integer.toHexString(marca);
	}

	/**
	 * JPEG 1200×630 da prévia. O Chromium devolve PNG, que com os degradês passa de 250 KB; o
	 * WhatsApp deixa de mostrar imagens grandes (~300 KB), então sai em JPEG (~80 KB).
	 */
	public byte[] imagem(Empresa empresa) {
		String chave = versao(empresa);
		byte[] pronta = prontas.get(chave);
		if (pronta != null) {
			return pronta;
		}
		byte[] nova = jpeg(renderer.capturar(html(empresa), LARGURA, ALTURA));
		if (prontas.size() >= MAXIMO_EM_MEMORIA) {
			prontas.clear();
		}
		prontas.put(chave, nova);
		return nova;
	}

	private static byte[] jpeg(byte[] png) {
		try {
			BufferedImage origem = ImageIO.read(new ByteArrayInputStream(png));
			var rgb = new BufferedImage(origem.getWidth(), origem.getHeight(), BufferedImage.TYPE_INT_RGB);
			var pincel = rgb.createGraphics();
			pincel.drawImage(origem, 0, 0, null);
			pincel.dispose();

			ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
			ImageWriteParam parametros = escritor.getDefaultWriteParam();
			parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			parametros.setCompressionQuality(0.9f);
			var saida = new ByteArrayOutputStream();
			try (var fluxo = new MemoryCacheImageOutputStream(saida)) {
				escritor.setOutput(fluxo);
				escritor.write(null, new IIOImage(rgb, null, null), parametros);
			}
			finally {
				escritor.dispose();
			}
			return saida.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException("Não foi possível converter a prévia em JPEG.", e);
		}
	}

	/** HTML da imagem; útil para ajustar o desenho direto no navegador. */
	public String html(Empresa empresa) {
		var contexto = new Context(PT_BR);
		contexto.setVariable("fontes", FontesEmbutidas.CSS);
		contexto.setVariable("t", MarcaEmpresa.temaDe(empresa));
		contexto.setVariable("c", CurvaCarreira.desenhar(580, 430, INDICE_NO_PLATO));
		if (empresa != null) {
			contexto.setVariable("empresa", empresa.getNome());
			contexto.setVariable("sigla", MarcaEmpresa.siglaDe(empresa.getNome()));
			contexto.setVariable("logo", logomarcas.dataUri(empresa));
			Integer largura = empresa.getLogoLargura();
			Integer altura = empresa.getLogoAltura();
			// Logo "quadrada" (só o símbolo) pede o nome ao lado, como nas telas do líder.
			contexto.setVariable("pedeNome", largura != null && altura != null && largura < altura * 1.8);
		}
		return templates.process("previa/link", contexto);
	}

}
