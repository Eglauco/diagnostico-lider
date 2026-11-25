import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { Location } from '@angular/common';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

import { baixarArquivo } from '../compartilhado/download';
import { FAIXAS, ORDEM_FAIXAS } from '../compartilhado/faixas';
import { dataCompleta } from '../compartilhado/formatos';
import { API_ADMIN, Convites, EmpresaDetalhe } from './admin-modelos';
import { linkWhatsappComTexto, mensagemConvite } from './convite';
import { encerramentoPorExtenso } from './encerramento/secao-encerramento';
import { KitQr } from './qr/kit-qr';

/** Tudo o que a empresa precisa para divulgar: link, convite pronto e QR Code. */
@Component({
  selector: 'app-admin-empresa',
  imports: [KitQr, RouterLink],
  templateUrl: './admin-empresa.html',
  styleUrl: './admin.css',
  host: { class: 'block' },
})
export class AdminEmpresa {
  readonly id = input.required<string>();

  protected readonly empresa = httpResource<EmpresaDetalhe>(() => `${API_ADMIN}/empresas/${this.id()}`);
  /** Resumo dos convites por e-mail (o funil) para o cartão da página. */
  protected readonly convites = httpResource<Convites>(() => `${API_ADMIN}/empresas/${this.id()}/convites`);
  protected readonly encerramentoPorExtenso = encerramentoPorExtenso;
  private readonly dados = computed(() => (this.empresa.hasValue() ? this.empresa.value() : undefined));

  /** Acabou de ser cadastrada (veio do formulário): mostra a confirmação uma vez só. */
  protected readonly confirmacao = signal(this.lerConfirmacao());

  protected readonly convite = computed(() => {
    const e = this.dados();
    return e?.link ? mensagemConvite(e.nome, e.link.url) : '';
  });
  protected readonly whatsapp = computed(() => linkWhatsappComTexto(this.convite()));
  protected readonly distribuicao = computed(() => {
    const ind = this.dados()?.indicadores;
    const total = ORDEM_FAIXAS.reduce((soma, f) => soma + (ind?.porFaixa[f] ?? 0), 0);
    return ORDEM_FAIXAS.map((codigo) => {
      const quantidade = ind?.porFaixa[codigo] ?? 0;
      return { codigo, ...FAIXAS[codigo], quantidade, percentual: total ? (quantidade / total) * 100 : 0 };
    });
  });

  protected readonly copiado = signal<'link' | 'convite' | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly dataCompleta = dataCompleta;

  // Relatório consolidado: só com respostas suficientes para ninguém ser identificado.
  protected readonly faltamParaRelatorio = computed(() => {
    const e = this.dados();
    return e ? Math.max(0, e.minimoRelatorio - e.indicadores.concluidos) : 0;
  });
  /** Cargos como escada: o nível mais alto em cima (nivel 1 = primeiro nível de liderança). */
  protected readonly cargosNaEscada = computed(() =>
    (this.dados()?.cargos ?? []).map((cargo, i) => ({ ...cargo, nivel: i + 1 })).reverse(),
  );
  protected readonly urlRelatorio = computed(() => `${API_ADMIN}/empresas/${this.id()}/relatorio.pdf`);
  protected readonly gerandoRelatorio = signal(false);

  protected async baixarRelatorio() {
    if (this.gerandoRelatorio()) return;
    this.gerandoRelatorio.set(true);
    this.erro.set(null);
    try {
      await baixarArquivo(this.urlRelatorio(), 'relatorio-consolidado.pdf');
    } catch (e) {
      this.erro.set(e instanceof Error && !e.message.startsWith('Falha') ? e.message : 'Não foi possível gerar o relatório agora. Tente de novo.');
    } finally {
      this.gerandoRelatorio.set(false);
    }
  }

  constructor() {
    const titulo = inject(Title);
    effect(() => {
      const e = this.dados();
      if (e) titulo.setTitle(`${e.nome} · Empresas · Administrador`);
    });
  }

  /** O estado do histórico sobrevive ao recarregar: limpa a marca depois de ler. */
  private lerConfirmacao(): 'criada' | 'salva' | null {
    const location = inject(Location);
    const { criada, salva, ...resto } = (location.getState() ?? {}) as { criada?: boolean; salva?: boolean };
    if (criada || salva) location.replaceState(location.path(), '', resto);
    return criada ? 'criada' : salva ? 'salva' : null;
  }

  protected dataDeCadastro(iso: string): string {
    return dataCompleta(iso).replace(/ às .*$/, '');
  }

  protected async copiar(texto: string, chave: 'link' | 'convite') {
    this.erro.set(null);
    try {
      await navigator.clipboard.writeText(texto);
      this.copiado.set(chave);
      setTimeout(() => this.copiado.set(null), 2000);
    } catch {
      this.erro.set('O navegador não deixou copiar. Selecione o texto e copie manualmente.');
    }
  }
}
