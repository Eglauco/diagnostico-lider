package br.com.diagnosticolider.empresa;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.diagnosticolider.arquivos.Armazenamento;
import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;

/**
 * Logomarca das empresas, guardada no bucket. O painel já envia um PNG pronto (reduzido, sem
 * bordas sobrando e com fundo transparente preservado): aqui só se confere que é mesmo um PNG
 * de tamanho razoável. SVG vira PNG no navegador, então nenhum script chega a ser servido.
 */
@Service
public class LogomarcaService {

	private static final Logger log = LoggerFactory.getLogger(LogomarcaService.class);

	static final int MAXIMO_BYTES = 2_500_000;

	static final int LADO_MAXIMO = 2400;

	private static final byte[] ASSINATURA_PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	/** Os PDFs embutem a logo a cada geração: guarda as mais recentes em memória. */
	private final Map<String, byte[]> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, byte[]> mais) {
			return size() > 40;
		}
	});

	private final EmpresaRepository empresas;

	private final Armazenamento armazenamento;

	private final FotoDeFundoService fundos;

	LogomarcaService(EmpresaRepository empresas, Armazenamento armazenamento, FotoDeFundoService fundos) {
		this.empresas = empresas;
		this.armazenamento = armazenamento;
		this.fundos = fundos;
	}

	static String caminho(Long empresaId, int versao) {
		return "empresas/" + empresaId + "/logo-v" + versao + ".png";
	}

	/**
	 * Endereço permanente (relativo) da logo atual, servido pelo backend. Usado no e-mail, que
	 * precisa funcionar meses depois, e quando os arquivos estão no disco (desenvolvimento).
	 */
	public static String urlNoBackend(Empresa empresa) {
		return empresa.getLogoVersao() == null ? null
				: "/api/public/empresas/" + empresa.getId() + "/logo-v" + empresa.getLogoVersao() + ".png";
	}

	/**
	 * A logo atual para as telas. Com bucket, o endereço é assinado e o navegador baixa direto
	 * dele: a imagem não passa pelo backend.
	 */
	public MarcaEmpresa.Logo logo(Empresa empresa) {
		if (empresa == null || empresa.getLogoVersao() == null) {
			return null;
		}
		String url = armazenamento.urlDireta(caminho(empresa.getId(), empresa.getLogoVersao()))
			.orElseGet(() -> urlNoBackend(empresa));
		return new MarcaEmpresa.Logo(url, empresa.getLogoLargura(), empresa.getLogoAltura());
	}

	/** Nome, paleta, logo e foto de fundo da empresa, para as telas (null no link geral). */
	public MarcaEmpresa marca(Empresa empresa) {
		return empresa == null ? null : MarcaEmpresa.de(empresa, logo(empresa), fundos.fundo(empresa), fundos.capa(empresa));
	}

	@Transactional
	public void gravar(Long empresaId, byte[] png) {
		if (png == null || png.length == 0) {
			throw new RegraNegocioException("Envie a imagem da logomarca.");
		}
		if (png.length > MAXIMO_BYTES) {
			throw new RegraNegocioException("A logomarca ficou grande demais. Use uma imagem menor.");
		}
		if (!Arrays.equals(Arrays.copyOf(png, ASSINATURA_PNG.length), ASSINATURA_PNG)) {
			throw new RegraNegocioException("Envie a logomarca em PNG.");
		}
		int[] tamanho = tamanho(png);
		Empresa empresa = buscar(empresaId);

		Integer anterior = empresa.trocarLogo(tamanho[0], tamanho[1]);
		String caminho = caminho(empresaId, empresa.getLogoVersao());
		armazenamento.gravar(caminho, png, "image/png");
		cache.put(caminho, png);
		apagarDepoisDoCommit(empresaId, anterior);
	}

	@Transactional
	public void remover(Long empresaId) {
		Empresa empresa = buscar(empresaId);
		apagarDepoisDoCommit(empresaId, empresa.removerLogo());
	}

	/** Só a versão atual é servida: um endereço antigo responde 404. */
	@Transactional(readOnly = true)
	public Optional<byte[]> ler(Long empresaId, int versao) {
		return empresas.findById(empresaId)
			.filter(e -> e.getLogoVersao() != null && e.getLogoVersao() == versao)
			.flatMap(e -> lerArquivo(caminho(empresaId, versao)));
	}

	/** A logo como data URI, para os PDFs (o Chromium não precisa buscar nada na rede). */
	public String dataUri(Empresa empresa) {
		if (empresa == null || empresa.getLogoVersao() == null) {
			return null;
		}
		try {
			return lerArquivo(caminho(empresa.getId(), empresa.getLogoVersao()))
				.map(png -> "data:image/png;base64," + Base64.getEncoder().encodeToString(png))
				.orElse(null);
		}
		catch (RuntimeException e) {
			// Sem a logo, o PDF sai com o monograma: melhor do que não sair.
			log.warn("Logomarca da empresa {} indisponível: {}", empresa.getId(), e.getMessage());
			return null;
		}
	}

	private Optional<byte[]> lerArquivo(String caminho) {
		byte[] emCache = cache.get(caminho);
		if (emCache != null) {
			return Optional.of(emCache);
		}
		Optional<byte[]> lido = armazenamento.ler(caminho);
		lido.ifPresent(png -> cache.put(caminho, png));
		return lido;
	}

	private Empresa buscar(Long empresaId) {
		return empresas.findById(empresaId).orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
	}

	/** O arquivo antigo só sai depois que o banco confirmar a troca. */
	private void apagarDepoisDoCommit(Long empresaId, Integer versaoAnterior) {
		if (versaoAnterior == null) {
			return;
		}
		String caminho = caminho(empresaId, versaoAnterior);
		cache.remove(caminho);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					armazenamento.apagar(caminho);
				}
				catch (RuntimeException e) {
					log.warn("Não foi possível apagar a logomarca antiga {}: {}", caminho, e.getMessage());
				}
			}
		});
	}

	/** Largura e altura lidas só do cabeçalho do PNG. */
	private static int[] tamanho(byte[] png) {
		try (var entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
			var leitores = ImageIO.getImageReaders(entrada);
			if (!leitores.hasNext()) {
				throw new RegraNegocioException("Não conseguimos ler a imagem. Tente outro arquivo.");
			}
			ImageReader leitor = leitores.next();
			try {
				leitor.setInput(entrada);
				int largura = leitor.getWidth(0);
				int altura = leitor.getHeight(0);
				if (largura > LADO_MAXIMO || altura > LADO_MAXIMO || largura < 8 || altura < 8) {
					throw new RegraNegocioException("A logomarca precisa ter entre 8 e 2400 pixels de lado.");
				}
				return new int[] { largura, altura };
			}
			finally {
				leitor.dispose();
			}
		}
		catch (IOException e) {
			throw new RegraNegocioException("Não conseguimos ler a imagem. Tente outro arquivo.");
		}
	}

}
