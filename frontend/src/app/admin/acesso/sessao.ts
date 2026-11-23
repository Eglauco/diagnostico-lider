import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, Service, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, firstValueFrom, throwError } from 'rxjs';

export interface UsuarioSessao {
  id: number;
  nome: string;
  email: string;
}

const API = '/api/auth';
export const ROTA_ENTRAR = '/administrador/entrar';

/**
 * Quem está logado no painel. A sessão vive num cookie HttpOnly (o JavaScript não lê); aqui
 * fica só o nome e o e-mail para a interface. O Angular envia o token CSRF sozinho.
 */
@Service()
export class SessaoPainel {
  private readonly http = inject(HttpClient);
  private conferida = false;

  readonly usuario = signal<UsuarioSessao | null>(null);
  /** Outra tela pediu para abrir "Trocar minha senha" (o diálogo mora no layout). */
  readonly pedidoTrocaDeSenha = signal(0);

  /** Pergunta ao servidor quem está logado (e recebe o cookie CSRF para o login). */
  async conferir(): Promise<UsuarioSessao | null> {
    if (this.conferida) return this.usuario();
    const sessao = await firstValueFrom(this.http.get<{ usuario: UsuarioSessao | null }>(`${API}/sessao`));
    this.usuario.set(sessao.usuario);
    this.conferida = true;
    return sessao.usuario;
  }

  async entrar(email: string, senha: string): Promise<UsuarioSessao> {
    const usuario = await firstValueFrom(this.http.post<UsuarioSessao>(`${API}/entrar`, { email, senha }));
    this.usuario.set(usuario);
    this.conferida = true;
    return usuario;
  }

  async sair(): Promise<void> {
    try {
      await firstValueFrom(this.http.post(`${API}/sair`, null));
    } finally {
      this.encerrada();
    }
  }

  trocarSenha(senhaAtual: string, novaSenha: string): Promise<unknown> {
    return firstValueFrom(this.http.put(`${API}/senha`, { senhaAtual, novaSenha }));
  }

  /** O nome ou o e-mail mudou (ex.: a pessoa editou o próprio cadastro). */
  atualizar(usuario: UsuarioSessao) {
    this.usuario.set(usuario);
  }

  encerrada() {
    this.usuario.set(null);
    this.conferida = false;
  }

  abrirTrocaDeSenha() {
    this.pedidoTrocaDeSenha.update((n) => n + 1);
  }
}

/** "Ana Paula Souza" → "AS"; "Ana" → "AN". */
export function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return '?';
  const [primeira, ultima] = [partes[0], partes.length > 1 ? partes[partes.length - 1] : ''];
  return (ultima ? primeira[0] + ultima[0] : primeira.slice(0, 2)).toUpperCase();
}

/** Para onde voltar depois do login: só caminhos do próprio painel. */
export function destinoSeguro(voltar: string | null | undefined): string {
  return voltar && voltar.startsWith('/administrador') && !voltar.startsWith(ROTA_ENTRAR) ? voltar : '/administrador';
}

/** Painel só com login; sem sessão, vai para a tela de entrar e depois volta para cá. */
export const exigeLogin: CanActivateFn = async (_rota, estado) => {
  const sessao = inject(SessaoPainel);
  const router = inject(Router);
  try {
    if (await sessao.conferir()) return true;
  } catch {
    // Sem resposta do servidor: a tela de entrar mostra o erro ao tentar.
  }
  return router.createUrlTree([ROTA_ENTRAR], { queryParams: { voltar: estado.url } });
};

/** Quem já está logado não precisa ver a tela de entrar. */
export const soVisitante: CanActivateFn = async (rota) => {
  const sessao = inject(SessaoPainel);
  try {
    if (await sessao.conferir()) {
      return inject(Router).parseUrl(destinoSeguro(rota.queryParamMap.get('voltar')));
    }
  } catch {
    // Segue para a tela de entrar.
  }
  return true;
};

/** A sessão caiu no meio do uso (expirou, senha trocada, acesso desativado): volta ao login. */
export const sessaoExpiradaInterceptor: HttpInterceptorFn = (pedido, proximo) => {
  const sessao = inject(SessaoPainel);
  const router = inject(Router);
  return proximo(pedido).pipe(
    catchError((erro: unknown) => {
      if (erro instanceof HttpErrorResponse && erro.status === 401 && pedido.url.startsWith('/api/admin')) {
        sessao.encerrada();
        if (!router.url.startsWith(ROTA_ENTRAR)) {
          void router.navigate([ROTA_ENTRAR], { queryParams: { voltar: router.url, motivo: 'sessao' } });
        }
      }
      return throwError(() => erro);
    }),
  );
};
