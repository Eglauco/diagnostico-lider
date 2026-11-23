package br.com.diagnosticolider.arquivos;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.com.diagnosticolider.config.AppProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Bucket S3 do Railway. O bucket é privado: o navegador baixa as imagens direto dele por um
 * endereço assinado (o backend só assina, a imagem não passa por aqui).
 */
class S3Armazenamento implements Armazenamento, AutoCloseable {

	/** Arquivos nunca mudam (o nome tem a versão): o navegador pode guardar por um ano. */
	static final String CACHE_IMUTAVEL = "public, max-age=31536000, immutable";

	/** Validade máxima de uma assinatura S3 (SigV4). */
	static final Duration VALIDADE = Duration.ofDays(7);

	/**
	 * O mesmo endereço é reaproveitado por 6 dias, para o cache do navegador funcionar (endereço
	 * novo = download novo). Assim todo endereço entregue ainda vale por pelo menos 1 dia.
	 */
	static final Duration REUSO = Duration.ofDays(6);

	/**
	 * Só leitura (GET/HEAD), de qualquer origem: sem a assinatura ninguém lê nada, e o CORS só
	 * permite que o painel desenhe a logo no canvas do QR Code. "*" porque desenvolvimento e
	 * produção dividem o bucket.
	 */
	private static final CORSRule CORS_LEITURA = CORSRule.builder()
		.allowedOrigins("*")
		.allowedMethods("GET", "HEAD")
		.allowedHeaders("*")
		.maxAgeSeconds(86_400)
		.build();

	private record Assinada(String url, Instant renovarEm) {
	}

	private final S3Client s3;

	private final S3Presigner assinador;

	private final String bucket;

	private final String prefixo;

	private final Map<String, Assinada> assinadas = new ConcurrentHashMap<>();

	S3Armazenamento(AppProperties.Armazenamento config) {
		this.bucket = config.bucket();
		this.prefixo = config.prefixo();
		var endpoint = URI.create(config.endpoint());
		var regiao = Region.of(config.regiao() == null || config.regiao().isBlank() ? "auto" : config.regiao());
		var credenciais = StaticCredentialsProvider
			.create(AwsBasicCredentials.create(config.chave(), config.segredo()));
		this.s3 = S3Client.builder()
			.endpointOverride(endpoint)
			.region(regiao)
			.credentialsProvider(credenciais)
			.httpClientBuilder(UrlConnectionHttpClient.builder())
			.forcePathStyle(config.estiloCaminho())
			// Serviços S3-compatíveis nem sempre aceitam as somas de verificação novas do SDK.
			.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
			.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
			.build();
		this.assinador = S3Presigner.builder()
			.endpointOverride(endpoint)
			.region(regiao)
			.credentialsProvider(credenciais)
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(config.estiloCaminho()).build())
			.build();
	}

	@Override
	public void gravar(String caminho, byte[] conteudo, String tipo) {
		s3.putObject(r -> r.bucket(bucket).key(chave(caminho)).contentType(tipo).cacheControl(CACHE_IMUTAVEL),
				RequestBody.fromBytes(conteudo));
	}

	@Override
	public Optional<byte[]> ler(String caminho) {
		try {
			return Optional.of(s3.getObjectAsBytes(r -> r.bucket(bucket).key(chave(caminho))).asByteArray());
		}
		catch (NoSuchKeyException e) {
			return Optional.empty();
		}
	}

	@Override
	public void apagar(String caminho) {
		assinadas.remove(caminho);
		s3.deleteObject(r -> r.bucket(bucket).key(chave(caminho)));
	}

	/** Assinar é um cálculo local (HMAC), sem ida ao bucket. */
	@Override
	public Optional<String> urlDireta(String caminho) {
		Instant agora = Instant.now();
		Assinada assinada = assinadas.compute(caminho,
				(c, atual) -> atual != null && agora.isBefore(atual.renovarEm()) ? atual : assinar(c, agora));
		return Optional.of(assinada.url());
	}

	private Assinada assinar(String caminho, Instant agora) {
		var url = assinador.presignGetObject(p -> p.signatureDuration(VALIDADE)
			.getObjectRequest(g -> g.bucket(bucket)
				.key(chave(caminho))
				// Vale também para arquivos gravados antes do Cache-Control ir junto no upload.
				.responseCacheControl(CACHE_IMUTAVEL)))
			.url();
		return new Assinada(url.toString(), agora.plus(REUSO));
	}

	@Override
	public void preparar() {
		criarPasta();
		garantirCors();
	}

	/** A pasta do ambiente: no S3, um objeto "pasta/" vazio. */
	private void criarPasta() {
		String pasta = prefixo + "/";
		try {
			s3.headObject(r -> r.bucket(bucket).key(pasta));
		}
		catch (S3Exception e) {
			if (e.statusCode() != 404) {
				throw e;
			}
			s3.putObject(r -> r.bucket(bucket).key(pasta).contentType("application/x-directory"), RequestBody.empty());
		}
	}

	/** Acrescenta a regra de leitura, se faltar, sem apagar outras regras do bucket. */
	private void garantirCors() {
		List<CORSRule> atuais;
		try {
			atuais = s3.getBucketCors(r -> r.bucket(bucket)).corsRules();
		}
		catch (S3Exception e) {
			if (e.statusCode() != 404) {
				throw e;
			}
			atuais = List.of();
		}
		boolean temLeitura = atuais.stream()
			.anyMatch(r -> r.allowedOrigins().contains("*") && r.allowedMethods().contains("GET"));
		if (!temLeitura) {
			var regras = new ArrayList<>(atuais);
			regras.add(CORS_LEITURA);
			s3.putBucketCors(r -> r.bucket(bucket).corsConfiguration(c -> c.corsRules(regras)));
		}
	}

	@Override
	public String descricao() {
		return "bucket S3 " + bucket + ", pasta " + prefixo + "/ (o navegador baixa direto do bucket)";
	}

	@Override
	public void close() {
		assinador.close();
		s3.close();
	}

	private String chave(String caminho) {
		return prefixo + "/" + caminho;
	}

}
