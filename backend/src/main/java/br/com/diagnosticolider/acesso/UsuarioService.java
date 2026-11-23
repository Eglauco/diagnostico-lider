package br.com.diagnosticolider.acesso;

import java.time.Clock;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.acesso.AcessoDtos.EditarUsuario;
import br.com.diagnosticolider.acesso.AcessoDtos.NovoUsuario;
import br.com.diagnosticolider.acesso.AcessoDtos.UsuarioItem;
import br.com.diagnosticolider.compartilhado.NaoEncontradoException;
import br.com.diagnosticolider.compartilhado.RegraNegocioException;

/** Cadastro de quem acessa o painel. Sem perfis: todos podem tudo, inclusive gerir usuários. */
@Service
@Transactional
public class UsuarioService {

	private final UsuarioRepository usuarios;

	private final PasswordEncoder senhas;

	private final Clock clock;

	UsuarioService(UsuarioRepository usuarios, PasswordEncoder senhas, Clock clock) {
		this.usuarios = usuarios;
		this.senhas = senhas;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<UsuarioItem> listar(Long idAtual) {
		return usuarios.findAllByOrderByAtivoDescNomeAsc().stream().map(u -> UsuarioItem.de(u, idAtual)).toList();
	}

	@Transactional(readOnly = true)
	public Usuario buscar(Long id) {
		return usuarios.findById(id).orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
	}

	public Usuario criar(NovoUsuario pedido) {
		String email = Usuario.normalizarEmail(pedido.email());
		if (usuarios.existsByEmailIgnoreCase(email)) {
			throw RegraNegocioException.conflito("Já existe um usuário com esse e-mail.").noCampo("email");
		}
		try {
			return usuarios.saveAndFlush(new Usuario(pedido.nome(), email, senhas.encode(pedido.senha()), clock.instant()));
		}
		catch (DataIntegrityViolationException e) {
			throw RegraNegocioException.conflito("Já existe um usuário com esse e-mail.").noCampo("email");
		}
	}

	public Usuario editar(Long id, EditarUsuario pedido) {
		Usuario usuario = buscar(id);
		String email = Usuario.normalizarEmail(pedido.email());
		if (usuarios.existsByEmailIgnoreCaseAndIdNot(email, id)) {
			throw RegraNegocioException.conflito("Já existe um usuário com esse e-mail.").noCampo("email");
		}
		usuario.atualizar(pedido.nome(), email);
		return usuario;
	}

	/** Senha definida por outra pessoa: as sessões abertas do usuário caem. */
	public Usuario definirSenha(Long id, String senha, Long idAtual) {
		if (id.equals(idAtual)) {
			throw new RegraNegocioException("Para trocar a sua própria senha, use “Trocar minha senha”.");
		}
		Usuario usuario = buscar(id);
		usuario.trocarSenha(senhas.encode(senha), clock.instant());
		return usuario;
	}

	public Usuario trocarPropriaSenha(Long id, String senhaAtual, String novaSenha) {
		Usuario usuario = buscar(id);
		if (!senhas.matches(senhaAtual, usuario.getSenhaHash())) {
			throw new RegraNegocioException("A senha atual não confere.").noCampo("senhaAtual");
		}
		if (senhas.matches(novaSenha, usuario.getSenhaHash())) {
			throw new RegraNegocioException("A nova senha precisa ser diferente da atual.").noCampo("novaSenha");
		}
		usuario.trocarSenha(senhas.encode(novaSenha), clock.instant());
		return usuario;
	}

	public Usuario alterarSituacao(Long id, boolean ativo, Long idAtual) {
		if (!ativo && id.equals(idAtual)) {
			throw new RegraNegocioException("Você não pode desativar o seu próprio acesso.");
		}
		Usuario usuario = buscar(id);
		if (ativo) {
			usuario.reativar();
		}
		else if (usuario.isAtivo()) {
			usuario.desativar(clock.instant());
		}
		return usuario;
	}

	public void registrarAcesso(Long id) {
		usuarios.findById(id).ifPresent(u -> u.registrarAcesso(clock.instant()));
	}

}
