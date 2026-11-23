import { Component, computed, DestroyRef, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Meta } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { email, form, FormField, required } from '@angular/forms/signals';

import { geometriaCurva, posicaoNaCurva } from '../../compartilhado/curva-carreira';
import { FAIXAS } from '../../compartilhado/faixas';
import { CampoSenha } from './campo-senha';
import { destinoSeguro, SessaoPainel } from './sessao';

/** Alguns líderes fictícios para ilustrar a curva do painel (decorativo). */
const PONTOS_ILUSTRATIVOS = [92, 81, 74, 66, 58, 51, 43, 31, 22];

/** Tela de entrar no painel. Sem "esqueci a senha": quem tem acesso define uma nova. */
@Component({
  selector: 'app-admin-entrar',
  imports: [CampoSenha, FormField],
  templateUrl: './admin-entrar.html',
  styleUrl: '../admin.css',
  host: { class: 'block' },
})
export class AdminEntrar {
  private readonly sessao = inject(SessaoPainel);
  private readonly router = inject(Router);
  private readonly campoEmail = viewChild<ElementRef<HTMLInputElement>>('campoEmail');

  /** Da URL: para onde voltar depois de entrar e por que a pessoa caiu aqui. */
  readonly voltar = input<string>();
  readonly motivo = input<string>();

  protected readonly modelo = signal({ email: '', senha: '' });
  protected readonly formulario = form(this.modelo, (c) => {
    required(c.email, { message: 'Escreva o seu e-mail.' });
    email(c.email, { message: 'Confira o e-mail: parece faltar o @ ou o domínio.' });
    required(c.senha, { message: 'Escreva a sua senha.' });
  });

  protected readonly tentou = signal(false);
  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected readonly erroEmail = computed(() =>
    this.tentou() ? (this.formulario.email().errors()[0]?.message ?? null) : null,
  );
  protected readonly erroSenha = computed(() =>
    this.tentou() ? (this.formulario.senha().errors()[0]?.message ?? null) : null,
  );

  protected readonly curva = geometriaCurva(560, 210);
  protected readonly pontos = PONTOS_ILUSTRATIVOS.map((indice) => {
    const faixa = indice >= 70 ? 'ASCENSAO' : indice >= 40 ? 'PLATO' : 'TRAVADA';
    return { ...this.curva.ponto(posicaoNaCurva(indice)), cor: FAIXAS[faixa].cor };
  });

  constructor() {
    const meta = inject(Meta);
    meta.updateTag({ name: 'robots', content: 'noindex, nofollow' });
    inject(DestroyRef).onDestroy(() => meta.removeTag('name="robots"'));
  }

  protected editou() {
    this.erro.set(null);
  }

  protected async entrar(evento: Event) {
    evento.preventDefault();
    if (this.enviando()) return;
    this.tentou.set(true);
    if (this.formulario().invalid()) {
      if (this.erroEmail()) this.campoEmail()?.nativeElement.focus();
      else document.getElementById('entrar-senha')?.focus();
      return;
    }
    this.enviando.set(true);
    this.erro.set(null);
    try {
      const { email: endereco, senha } = this.modelo();
      await this.sessao.entrar(endereco.trim(), senha);
      await this.router.navigateByUrl(destinoSeguro(this.voltar()), { replaceUrl: true });
    } catch (e) {
      this.erro.set(mensagemDeErro(e));
      // Limpa a senha para a nova tentativa, sem acusar "campo vazio" junto com o erro do servidor.
      this.tentou.set(false);
      this.modelo.update((m) => ({ ...m, senha: '' }));
      document.getElementById('entrar-senha')?.focus();
    } finally {
      this.enviando.set(false);
    }
  }
}

function mensagemDeErro(erro: unknown): string {
  if (erro instanceof HttpErrorResponse) {
    if (erro.status === 0) return 'Sem conexão com o servidor. Confira a internet e tente de novo.';
    if (erro.status === 403 && !(erro.error as { detail?: string } | null)?.detail) {
      return 'A página ficou desatualizada. Recarregue e tente de novo.';
    }
    const detalhe = (erro.error as { detail?: string } | null)?.detail;
    if (detalhe) return detalhe;
  }
  return 'Não foi possível entrar agora. Tente de novo em instantes.';
}
