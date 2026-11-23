package br.com.diagnosticolider.arquivos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.diagnosticolider.config.AppProperties;

@Configuration
class ArmazenamentoConfig {

	private static final Logger log = LoggerFactory.getLogger(ArmazenamentoConfig.class);

	@Bean
	Armazenamento armazenamento(AppProperties props) {
		var config = props.armazenamento();
		return config.s3() ? new S3Armazenamento(config) : new DiscoArmazenamento(config.pastaLocal(), config.prefixo());
	}

	/** Prepara o armazenamento na subida. Se o bucket não responder, avisa e segue no ar. */
	@Bean
	ApplicationRunner prepararArmazenamento(Armazenamento armazenamento) {
		return args -> {
			try {
				armazenamento.preparar();
				log.info("Arquivos (logomarcas) em: {}", armazenamento.descricao());
			}
			catch (RuntimeException e) {
				log.warn("Não foi possível preparar o armazenamento ({}): {}", armazenamento.descricao(), e.getMessage());
			}
		};
	}

}
