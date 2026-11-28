package br.com.diagnosticolider.empresa;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
 * A foto de fundo da página do RH de cada empresa (a entrada e o topo da página), guardada no
 * bucket como a logomarca. O painel já envia um JPEG pronto (reduzido e comprimido no navegador):
 * aqui só se confere que é mesmo um JPEG de tamanho razoável.
 */
@Service
public class FotoDeFundoService {

	private static final Logger log = LoggerFactory.getLogger(FotoDeFundoService.class);

	static final int MAXIMO_BYTES = 2_500_000;

	static final int LADO_MAXIMO = 3200;

	/** Menor que isso fica borrada esticada na metade de uma tela de computador. */
	static final int LARGURA_MINIMA = 640;

	static final int ALTURA_MINIMA = 400;

	private final EmpresaRepository empresas;

	private final Armazenamento armazenamento;

	FotoDeFundoService(EmpresaRepository empresas, Armazenamento armazenamento) {
		this.empresas = empresas;
		this.armazenamento = armazenamento;
	}

	static String caminho(Long empresaId, int versao) {
		return "empresas/" + empresaId + "/fundo-v" + versao + ".jpg";
	}

	/**
	 * A foto atual para as telas (null sem foto). Com bucket, o endereço é assinado e o navegador
	 * baixa direto dele; no disco (desenvolvimento), o backend serve.
	 */
	public MarcaEmpresa.Fundo fundo(Empresa empresa) {
		if (empresa == null || empresa.getFundoVersao() == null) {
			return null;
		}
		String caminho = caminho(empresa.getId(), empresa.getFundoVersao());
		String url = armazenamento.urlDireta(caminho)
			.orElseGet(() -> "/api/public/empresas/" + empresa.getId() + "/fundo-v" + empresa.getFundoVersao() + ".jpg");
		return new MarcaEmpresa.Fundo(url, empresa.getFundoLargura(), empresa.getFundoAltura(), empresa.getFundoFocoX(),
				empresa.getFundoFocoY());
	}

	@Transactional
	public void gravar(Long empresaId, byte[] jpeg, int focoX, int focoY) {
		if (jpeg == null || jpeg.length == 0) {
			throw new RegraNegocioException("Envie a foto de fundo.");
		}
		if (jpeg.length > MAXIMO_BYTES) {
			throw new RegraNegocioException("A foto ficou grande demais. Use uma imagem menor.");
		}
		if (jpeg.length < 3 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8 || (jpeg[2] & 0xFF) != 0xFF) {
			throw new RegraNegocioException("Envie a foto em JPG.");
		}
		int[] tamanho = tamanho(jpeg);
		Empresa empresa = buscar(empresaId);
		Integer anterior = empresa.trocarFundo(tamanho[0], tamanho[1], focoX, focoY);
		armazenamento.gravar(caminho(empresaId, empresa.getFundoVersao()), jpeg, "image/jpeg");
		apagarDepoisDoCommit(empresaId, anterior);
	}

	/** Só o ponto de foco muda: a mesma foto, cortada em outro lugar. */
	@Transactional
	public void focar(Long empresaId, int focoX, int focoY) {
		Empresa empresa = buscar(empresaId);
		if (empresa.getFundoVersao() == null) {
			throw new RegraNegocioException("A empresa ainda não tem foto de fundo.");
		}
		empresa.focarFundo(focoX, focoY);
	}

	@Transactional
	public void remover(Long empresaId) {
		apagarDepoisDoCommit(empresaId, buscar(empresaId).removerFundo());
	}

	/** Só a versão atual é servida: um endereço antigo responde 404. */
	@Transactional(readOnly = true)
	public Optional<byte[]> ler(Long empresaId, int versao) {
		return empresas.findById(empresaId)
			.filter(e -> e.getFundoVersao() != null && e.getFundoVersao() == versao)
			.flatMap(e -> armazenamento.ler(caminho(empresaId, versao)));
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
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					armazenamento.apagar(caminho);
				}
				catch (RuntimeException e) {
					log.warn("Não foi possível apagar a foto de fundo antiga {}: {}", caminho, e.getMessage());
				}
			}
		});
	}

	/** Largura e altura lidas só do cabeçalho do JPEG. */
	private static int[] tamanho(byte[] jpeg) {
		try (var entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
			var leitores = ImageIO.getImageReaders(entrada);
			if (!leitores.hasNext()) {
				throw new RegraNegocioException("Não conseguimos ler a foto. Tente outro arquivo.");
			}
			ImageReader leitor = leitores.next();
			try {
				leitor.setInput(entrada);
				int largura = leitor.getWidth(0);
				int altura = leitor.getHeight(0);
				if (largura > LADO_MAXIMO || altura > LADO_MAXIMO) {
					throw new RegraNegocioException("A foto pode ter até 3200 pixels de lado.");
				}
				if (largura < LARGURA_MINIMA || altura < ALTURA_MINIMA) {
					throw new RegraNegocioException(
							"A foto é pequena demais e ficaria borrada: use uma com pelo menos 640 × 400 pixels.");
				}
				return new int[] { largura, altura };
			}
			finally {
				leitor.dispose();
			}
		}
		catch (IOException e) {
			throw new RegraNegocioException("Não conseguimos ler a foto. Tente outro arquivo.");
		}
	}

}
