import { Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { form, FormField, maxLength, required, validate } from '@angular/forms/signals';
import { firstValueFrom } from 'rxjs';

import { problemaDe } from '../../api/diagnostico-api';
import { problemaEmail } from '../../compartilhado/email';
import { dataCompleta, quando } from '../../compartilhado/formatos';
import { CampoSenha } from './campo-senha';
import { iniciais, SessaoPainel } from './sessao';

export interface UsuarioItem {
  id: number;
  nome: string;
  email: string;
  ativo: boolean;
  criadoEm: string;
  ultimoAcessoEm: string | null;
  voce: boolean;
}

type Modo = 'novo' | 'editar' | 'senha';
type Campo = 'nome' | 'email' | 'senha';

const API = '/api/admin/usuarios';
const SENHA_MINIMA = 8;
/** Sem caracteres que se confundem (0/O, 1/l/I): a senha costuma ser ditada ou copiada à mão. */
const ALFABETO = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/** Senha aleatória em três blocos de quatro (ex.: "k7Rm-x2Pq-9tWe"), ~70 bits. */
export function gerarSenha(): string {
  const bytes = crypto.getRandomValues(new Uint32Array(12));
  const letras = Array.from(bytes, (n) => ALFABETO[n % ALFABETO.length]).join('');
  return `${letras.slice(0, 4)}-${letras.slice(4, 8)}-${letras.slice(8)}`;
}

/** Quem pode entrar no painel. Sem perfis por enquanto: todo usuário acessa tudo. */
@Component({
  selector: 'app-admin-usuarios',
  imports: [CampoSenha, FormField],
  templateUrl: './admin-usuarios.html',
  styleUrl: '../admin.css',
  host: { class: 'block' },
})
export class AdminUsuarios {
  private readonly http = inject(HttpClient);
  protected readonly sessao = inject(SessaoPainel);
  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');

  protected readonly usuarios = httpResource<UsuarioItem[]>(() => API);
  protected readonly ativos = computed(() => (this.usuarios.hasValue() ? this.usuarios.value().filter((u) => u.ativo).length : 0));

  protected readonly iniciais = iniciais;
  protected readonly quando = quando;
  protected readonly dataCompleta = dataCompleta;

  // Diálogo -------------------------------------------------------------------
  protected readonly modo = signal<Modo>('novo');
  protected readonly alvo = signal<UsuarioItem | null>(null);
  /** Depois de criar ou definir senha: os dados para entregar à pessoa. */
  protected readonly entregue = signal<{ titulo: string; texto: string; nome: string; email: string; senha: string } | null>(null);

  protected readonly modelo = signal({ nome: '', email: '', senha: '' });
  protected readonly formulario = form(this.modelo, (c) => {
    required(c.nome, { message: 'Escreva o nome.', when: () => this.modo() !== 'senha' });
    maxLength(c.nome, 120, { message: 'Use até 120 caracteres.' });
    required(c.email, { message: 'Escreva o e-mail.', when: () => this.modo() !== 'senha' });
    validate(c.email, ({ value }) => {
      const problema = problemaEmail(value());
      return problema ? { kind: 'email', message: problema } : null;
    });
    validate(c.senha, ({ value }) => {
      if (this.modo() === 'editar') return null;
      if (!value()) return { kind: 'senha', message: 'Defina uma senha.' };
      return value().length < SENHA_MINIMA ? { kind: 'senha', message: 'Use pelo menos 8 caracteres.' } : null;
    });
  });

  protected readonly tentou = signal(false);
  protected readonly enviando = signal(false);
  protected readonly erroGeral = signal<string | null>(null);
  private readonly errosServidor = signal<Partial<Record<Campo, string>>>({});
  protected readonly copiado = signal(false);

  protected readonly erro = (campo: Campo) =>
    this.errosServidor()[campo] ?? (this.tentou() ? (this.formulario[campo]().errors()[0]?.message ?? null) : null);

  protected readonly titulo = computed(() => {
    switch (this.modo()) {
      case 'novo':
        return 'Novo usuário';
      case 'editar':
        return 'Editar usuário';
      case 'senha':
        return `Nova senha para ${this.alvo()?.nome.split(' ')[0] ?? ''}`;
    }
  });

  // Aviso no rodapé, com "Desfazer" quando faz sentido.
  protected readonly aviso = signal<{ mensagem: string; desfazer?: () => void } | null>(null);
  private avisoTimer?: ReturnType<typeof setTimeout>;

  protected abrir(modo: Modo, usuario: UsuarioItem | null = null) {
    this.modo.set(modo);
    this.alvo.set(usuario);
    this.entregue.set(null);
    this.tentou.set(false);
    this.erroGeral.set(null);
    this.errosServidor.set({});
    this.copiado.set(false);
    this.modelo.set({
      nome: usuario?.nome ?? '',
      email: usuario?.email ?? '',
      senha: modo === 'editar' ? '' : gerarSenha(),
    });
    this.dialogo().nativeElement.showModal();
  }

  protected fechar() {
    this.dialogo().nativeElement.close();
  }

  protected fechou() {
    // Não deixa senha em memória da tela depois de fechar.
    this.modelo.update((m) => ({ ...m, senha: '' }));
    this.entregue.set(null);
  }

  protected novaSenhaGerada() {
    this.modelo.update((m) => ({ ...m, senha: gerarSenha() }));
    this.editou('senha');
  }

  protected editou(campo: Campo) {
    if (this.errosServidor()[campo]) this.errosServidor.update(({ [campo]: _, ...resto }) => resto);
    this.erroGeral.set(null);
  }

  protected async salvar(evento: Event) {
    evento.preventDefault();
    if (this.enviando()) return;
    this.tentou.set(true);
    const primeiroErro = (['nome', 'email', 'senha'] as Campo[]).find((c) => this.erro(c));
    if (primeiroErro) {
      document.getElementById(`usuario-${primeiroErro}`)?.focus();
      return;
    }
    this.enviando.set(true);
    const { nome, email: endereco, senha } = this.modelo();
    const alvo = this.alvo();
    try {
      switch (this.modo()) {
        case 'novo': {
          const criado = await firstValueFrom(this.http.post<UsuarioItem>(API, { nome: nome.trim(), email: endereco.trim(), senha }));
          this.entregue.set({
            titulo: 'Acesso criado',
            texto: 'Envie o e-mail e a senha para a pessoa por um canal seguro. Ela pode trocar a senha depois de entrar.',
            nome: criado.nome,
            email: criado.email,
            senha,
          });
          break;
        }
        case 'editar': {
          const salvo = await firstValueFrom(this.http.put<UsuarioItem>(`${API}/${alvo!.id}`, { nome: nome.trim(), email: endereco.trim() }));
          if (salvo.voce) this.sessao.atualizar({ id: salvo.id, nome: salvo.nome, email: salvo.email });
          this.fechar();
          this.avisar(`Dados de ${salvo.nome} salvos.`);
          break;
        }
        case 'senha': {
          const salvo = await firstValueFrom(this.http.put<UsuarioItem>(`${API}/${alvo!.id}/senha`, { senha }));
          this.entregue.set({
            titulo: 'Senha definida',
            texto: 'Se a pessoa estava logada, a sessão dela foi encerrada. Envie a nova senha por um canal seguro.',
            nome: salvo.nome,
            email: salvo.email,
            senha,
          });
          break;
        }
      }
      this.usuarios.reload();
    } catch (e) {
      const problema = problemaDe(e);
      const campos = (problema.campos ?? {}) as Partial<Record<Campo, string>>;
      const campo = (['nome', 'email', 'senha'] as Campo[]).find((c) => campos[c]);
      if (campo) {
        this.errosServidor.set(campos);
        document.getElementById(`usuario-${campo}`)?.focus();
      } else {
        this.erroGeral.set(problema.detail ?? 'Não foi possível salvar agora. Tente de novo.');
      }
    } finally {
      this.enviando.set(false);
    }
  }

  protected async copiarAcesso() {
    const e = this.entregue();
    if (!e) return;
    const texto =
      `Acesso ao painel do Diagnóstico do Líder\n${location.origin}/administrador\n\n` +
      `E-mail: ${e.email}\nSenha: ${e.senha}\n\nDepois de entrar, troque a senha em “Trocar minha senha”.`;
    try {
      await navigator.clipboard.writeText(texto);
      this.copiado.set(true);
      setTimeout(() => this.copiado.set(false), 2200);
    } catch {
      this.erroGeral.set('O navegador não deixou copiar. Selecione e copie manualmente.');
    }
  }

  protected async mudarSituacao(usuario: UsuarioItem, ativo: boolean, comDesfazer = true) {
    try {
      await firstValueFrom(this.http.put(`${API}/${usuario.id}/situacao`, { ativo }));
      this.usuarios.reload();
      this.avisar(
        ativo ? `Acesso de ${usuario.nome} reativado.` : `Acesso de ${usuario.nome} desativado e sessão encerrada.`,
        comDesfazer ? () => void this.mudarSituacao(usuario, !ativo, false) : undefined,
      );
    } catch (e) {
      this.avisar(problemaDe(e).detail ?? 'Não foi possível mudar o acesso agora.');
    }
  }

  protected trocarMinhaSenha() {
    this.sessao.abrirTrocaDeSenha();
  }

  private avisar(mensagem: string, desfazer?: () => void) {
    clearTimeout(this.avisoTimer);
    this.aviso.set({ mensagem, desfazer });
    this.avisoTimer = setTimeout(() => this.aviso.set(null), desfazer ? 7000 : 4000);
  }

  protected desfazer() {
    const acao = this.aviso()?.desfazer;
    this.aviso.set(null);
    acao?.();
  }
}
