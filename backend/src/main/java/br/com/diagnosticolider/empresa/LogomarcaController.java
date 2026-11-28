package br.com.diagnosticolider.empresa;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serve a logomarca (o bucket é privado). O endereço tem a versão, então o navegador e o
 * e-mail podem guardar a imagem para sempre; uma logo nova ganha outro endereço.
 */
@RestController
class LogomarcaController {

	private final LogomarcaService logomarcas;

	private final FotoDeFundoService fundos;

	LogomarcaController(LogomarcaService logomarcas, FotoDeFundoService fundos) {
		this.logomarcas = logomarcas;
		this.fundos = fundos;
	}

	/** A foto da entrada da página do RH (quando os arquivos estão no disco; com bucket, vai direto dele). */
	@GetMapping("/api/public/empresas/{id}/fundo-v{versao}.jpg")
	ResponseEntity<byte[]> fundo(@PathVariable Long id, @PathVariable int versao) {
		return jpeg(fundos.ler(FotoDeFundoService.Tipo.FUNDO, id, versao));
	}

	/** A imagem do topo da página do RH (idem). */
	@GetMapping("/api/public/empresas/{id}/capa-v{versao}.jpg")
	ResponseEntity<byte[]> capa(@PathVariable Long id, @PathVariable int versao) {
		return jpeg(fundos.ler(FotoDeFundoService.Tipo.CAPA, id, versao));
	}

	private static ResponseEntity<byte[]> jpeg(java.util.Optional<byte[]> arquivo) {
		return arquivo
			.map(jpeg -> ResponseEntity.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
				.body(jpeg))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/api/public/empresas/{id}/logo-v{versao}.png")
	ResponseEntity<byte[]> logo(@PathVariable Long id, @PathVariable int versao) {
		return logomarcas.ler(id, versao)
			.map(png -> ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
				.body(png))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
