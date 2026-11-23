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

	LogomarcaController(LogomarcaService logomarcas) {
		this.logomarcas = logomarcas;
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
