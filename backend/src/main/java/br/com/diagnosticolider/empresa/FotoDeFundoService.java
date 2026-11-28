package br.com.diagnosticolider.empresa;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

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
 * As fotos da empresa na página do RH, guardadas no bucket como a logomarca: a da entrada (em pé
 * ou quadrada, a metade esquerda da entrada no computador) e a do topo (deitada, a capa da página
 * e a faixa da entrada no celular). Sem uma, as telas usam a outra. O painel já envia um JPEG
 * pronto (reduzido e comprimido no navegador): aqui só se confere que é mesmo um JPEG razoável.
 */
@Service
public class FotoDeFundoService {

	/** Qual foto: o nome no bucket e no endereço, e o menor tamanho que não fica borrado. */
	public enum Tipo {

		FUNDO("fundo", "foto da entrada", 640, 400, Empresa::getFundo),
		CAPA("capa", "imagem do topo", 1000, 250, Empresa::getCapa);

		private final String nome;

		private final String rotulo;

		private final int larguraMinima;

		private final int alturaMinima;

		private final Function<Empresa, FotoDaEmpresa> foto;

		Tipo(String nome, String rotulo, int larguraMinima, int alturaMinima, Function<Empresa, FotoDaEmpresa> foto) {
			this.nome = nome;
			this.rotulo = rotulo;
			this.larguraMinima = larguraMinima;
			this.alturaMinima = alturaMinima;
			this.foto = foto;
		}

		public String nome() {
			return nome;
		}

		FotoDaEmpresa de(Empresa empresa) {
			return foto.apply(empresa);
		}

	}

	private static final Logger log = LoggerFactory.getLogger(FotoDeFundoService.class);

	static final int MAXIMO_BYTES = 2_500_000;

	static final int LADO_MAXIMO = 3200;

	private final EmpresaRepository empresas;

	private final Armazenamento armazenamento;

	FotoDeFundoService(EmpresaRepository empresas, Armazenamento armazenamento) {
		this.empresas = empresas;
		this.armazenamento = armazenamento;
	}

	static String caminho(Tipo tipo, Long empresaId, int versao) {
		return "empresas/" + empresaId + "/" + tipo.nome + "-v" + versao + ".jpg";
	}

	/** A foto da entrada (null sem foto). */
	public MarcaEmpresa.Foto fundo(Empresa empresa) {
		return foto(Tipo.FUNDO, empresa);
	}

	/** A imagem do topo (null sem imagem). */
	public MarcaEmpresa.Foto capa(Empresa empresa) {
		return foto(Tipo.CAPA, empresa);
	}

	/**
	 * A foto para as telas (null sem foto). Com bucket, o endereço é assinado e o navegador baixa
	 * direto dele; no disco (desenvolvimento), o backend serve.
	 */
	public MarcaEmpresa.Foto foto(Tipo tipo, Empresa empresa) {
		if (empresa == null || !tipo.de(empresa).existe()) {
			return null;
		}
		FotoDaEmpresa f = tipo.de(empresa);
		String url = armazenamento.urlDireta(caminho(tipo, empresa.getId(), f.getVersao()))
			.orElseGet(() -> "/api/public/empresas/" + empresa.getId() + "/" + tipo.nome + "-v" + f.getVersao() + ".jpg");
		return new MarcaEmpresa.Foto(url, f.getLargura(), f.getAltura(), f.getFocoX(), f.getFocoY());
	}

	@Transactional
	public void gravar(Tipo tipo, Long empresaId, byte[] jpeg, int focoX, int focoY) {
		if (jpeg == null || jpeg.length == 0) {
			throw new RegraNegocioException("Envie a " + tipo.rotulo + ".");
		}
		if (jpeg.length > MAXIMO_BYTES) {
			throw new RegraNegocioException("A " + tipo.rotulo + " ficou grande demais. Use uma imagem menor.");
		}
		if (jpeg.length < 3 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8 || (jpeg[2] & 0xFF) != 0xFF) {
			throw new RegraNegocioException("Envie a " + tipo.rotulo + " em JPG.");
		}
		int[] tamanho = tamanho(tipo, jpeg);
		Empresa empresa = buscar(empresaId);
		FotoDaEmpresa foto = tipo.de(empresa);
		Integer anterior = foto.trocar(tamanho[0], tamanho[1], focoX, focoY);
		armazenamento.gravar(caminho(tipo, empresaId, foto.getVersao()), jpeg, "image/jpeg");
		apagarDepoisDoCommit(tipo, empresaId, anterior);
	}

	/** Só o ponto de foco muda: a mesma foto, cortada em outro lugar. */
	@Transactional
	public void focar(Tipo tipo, Long empresaId, int focoX, int focoY) {
		FotoDaEmpresa foto = tipo.de(buscar(empresaId));
		if (!foto.existe()) {
			throw new RegraNegocioException("A empresa ainda não tem " + tipo.rotulo + ".");
		}
		foto.focar(focoX, focoY);
	}

	@Transactional
	public void remover(Tipo tipo, Long empresaId) {
		apagarDepoisDoCommit(tipo, empresaId, tipo.de(buscar(empresaId)).remover());
	}

	/** Só a versão atual é servida: um endereço antigo responde 404. */
	@Transactional(readOnly = true)
	public Optional<byte[]> ler(Tipo tipo, Long empresaId, int versao) {
		return empresas.findById(empresaId)
			.filter(e -> tipo.de(e).existe() && tipo.de(e).getVersao() == versao)
			.flatMap(e -> armazenamento.ler(caminho(tipo, empresaId, versao)));
	}

	private Empresa buscar(Long empresaId) {
		return empresas.findById(empresaId).orElseThrow(() -> new NaoEncontradoException("Empresa não encontrada."));
	}

	/** O arquivo antigo só sai depois que o banco confirmar a troca. */
	private void apagarDepoisDoCommit(Tipo tipo, Long empresaId, Integer versaoAnterior) {
		if (versaoAnterior == null) {
			return;
		}
		String caminho = caminho(tipo, empresaId, versaoAnterior);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					armazenamento.apagar(caminho);
				}
				catch (RuntimeException e) {
					log.warn("Não foi possível apagar a foto antiga {}: {}", caminho, e.getMessage());
				}
			}
		});
	}

	/** Largura e altura lidas só do cabeçalho do JPEG. */
	private static int[] tamanho(Tipo tipo, byte[] jpeg) {
		try (var entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
			var leitores = ImageIO.getImageReaders(entrada);
			if (!leitores.hasNext()) {
				throw new RegraNegocioException("Não conseguimos ler a imagem. Tente outro arquivo.");
			}
			ImageReader leitor = leitores.next();
			try {
				leitor.setInput(entrada);
				int largura = leitor.getWidth(0);
				int altura = leitor.getHeight(0);
				if (largura > LADO_MAXIMO || altura > LADO_MAXIMO) {
					throw new RegraNegocioException("A imagem pode ter até 3200 pixels de lado.");
				}
				if (largura < tipo.larguraMinima || altura < tipo.alturaMinima) {
					throw new RegraNegocioException("A " + tipo.rotulo + " é pequena demais e ficaria borrada: use uma com pelo menos "
							+ tipo.larguraMinima + " × " + tipo.alturaMinima + " pixels.");
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
