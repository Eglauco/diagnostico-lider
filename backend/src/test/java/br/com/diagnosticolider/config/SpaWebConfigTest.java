package br.com.diagnosticolider.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

/** Depois de um deploy o navegador precisa notar que o index.html mudou, mesmo com a data igual. */
class SpaWebConfigTest {

	@Test
	void etagSegueOConteudoENaoAData() {
		String antes = SpaWebConfig.etag(new ByteArrayResource("<script src=\"main-L7N5OXGZ.js\">".getBytes(StandardCharsets.UTF_8)));
		String depois = SpaWebConfig.etag(new ByteArrayResource("<script src=\"main-DGYOKN7H.js\">".getBytes(StandardCharsets.UTF_8)));
		String deNovo = SpaWebConfig.etag(new ByteArrayResource("<script src=\"main-DGYOKN7H.js\">".getBytes(StandardCharsets.UTF_8)));
		assertThat(antes).isNotEqualTo(depois);
		assertThat(depois).isEqualTo(deNovo);
	}

}
