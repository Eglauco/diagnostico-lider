package br.com.diagnosticolider.acesso;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.diagnosticolider.acesso.AcessoDtos.EditarUsuario;
import br.com.diagnosticolider.acesso.AcessoDtos.NovaSenha;
import br.com.diagnosticolider.acesso.AcessoDtos.NovoUsuario;
import br.com.diagnosticolider.acesso.AcessoDtos.Situacao;
import br.com.diagnosticolider.acesso.AcessoDtos.UsuarioItem;

/** Gestão de usuários do painel (qualquer usuário logado pode gerir, por enquanto). */
@RestController
@RequestMapping("/api/admin/usuarios")
class UsuarioController {

	private final UsuarioService usuarios;

	UsuarioController(UsuarioService usuarios) {
		this.usuarios = usuarios;
	}

	@GetMapping
	List<UsuarioItem> listar(@AuthenticationPrincipal UsuarioLogado logado) {
		return usuarios.listar(logado.getId());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	UsuarioItem criar(@AuthenticationPrincipal UsuarioLogado logado, @RequestBody @Validated NovoUsuario pedido) {
		return UsuarioItem.de(usuarios.criar(pedido), logado.getId());
	}

	@PutMapping("/{id}")
	UsuarioItem editar(@AuthenticationPrincipal UsuarioLogado logado, @PathVariable Long id,
			@RequestBody @Validated EditarUsuario pedido) {
		return UsuarioItem.de(usuarios.editar(id, pedido), logado.getId());
	}

	@PutMapping("/{id}/senha")
	UsuarioItem definirSenha(@AuthenticationPrincipal UsuarioLogado logado, @PathVariable Long id,
			@RequestBody @Validated NovaSenha pedido) {
		return UsuarioItem.de(usuarios.definirSenha(id, pedido.senha(), logado.getId()), logado.getId());
	}

	@PutMapping("/{id}/situacao")
	UsuarioItem alterarSituacao(@AuthenticationPrincipal UsuarioLogado logado, @PathVariable Long id,
			@RequestBody @Validated Situacao pedido) {
		return UsuarioItem.de(usuarios.alterarSituacao(id, pedido.ativo(), logado.getId()), logado.getId());
	}

}
