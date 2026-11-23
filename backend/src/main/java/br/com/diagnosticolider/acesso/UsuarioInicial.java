package br.com.diagnosticolider.acesso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import br.com.diagnosticolider.acesso.AcessoDtos.NovoUsuario;
import br.com.diagnosticolider.config.AppProperties;

/**
 * Cria o primeiro acesso a partir de ADMIN_EMAIL e ADMIN_SENHA quando o painel ainda não tem
 * nenhum usuário. Depois disso, as variáveis são ignoradas: os usuários são geridos na tela.
 * Não existe tela pública de "primeiro cadastro": num site aberto, alguém poderia chegar antes.
 */
@Component
class UsuarioInicial implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(UsuarioInicial.class);

	private final UsuarioRepository usuarios;

	private final UsuarioService servico;

	private final AppProperties.AcessoInicial inicial;

	UsuarioInicial(UsuarioRepository usuarios, UsuarioService servico, AppProperties propriedades) {
		this.usuarios = usuarios;
		this.servico = servico;
		this.inicial = propriedades.acessoInicial();
	}

	@Override
	public void run(ApplicationArguments args) {
		if (usuarios.count() > 0) {
			return;
		}
		if (inicial == null || !inicial.definido()) {
			log.warn("O painel não tem nenhum usuário. Defina ADMIN_EMAIL e ADMIN_SENHA para criar o primeiro acesso.");
			return;
		}
		if (inicial.senha().length() < AcessoDtos.SENHA_MINIMA) {
			log.error("ADMIN_SENHA precisa ter pelo menos {} caracteres. Primeiro acesso não criado.", AcessoDtos.SENHA_MINIMA);
			return;
		}
		String nome = inicial.nome() == null || inicial.nome().isBlank() ? "Administrador" : inicial.nome();
		servico.criar(new NovoUsuario(nome, inicial.email(), inicial.senha()));
		log.info("Primeiro acesso ao painel criado para {}.", Usuario.normalizarEmail(inicial.email()));
	}

}
