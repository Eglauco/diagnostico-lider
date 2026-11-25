import { Component, computed, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { problemaDe } from '../../api/diagnostico-api';
import { baixarArquivo, buscarArquivo, compartilharArquivo, podeCompartilharArquivos } from '../../compartilhado/download';
import { quando } from '../../compartilhado/formatos';
import { API_ADMIN, ConviteItem, Convites, EmpresaDetalhe, Importacao, StatusConvite } from '../admin-modelos';
import { encerramentoPorExtenso } from '../encerramento/secao-encerramento';
import { CartaoAcompanhamento } from '../gestores/cartao-acompanhamento';
import { CLASSE_STATUS, ROTULO_STATUS } from './convites';
import { DetalheConvite } from './detalhe-convite';
import { NovoConvite } from './novo-convite';
import { RevisaoImportacao } from './revisao-importacao';

type Filtro = 'todos' | StatusConvite;

/**
 * Convites por e-mail de uma empresa: a planilha (modelo e importação com revisão), o disparo
 * dos e-mails (liberado quando houver domínio), o funil e cada convite com a sua auditoria.
 */
@Component({
  selector: 'app-admin-convites',
  imports: [CartaoAcompanhamento, DetalheConvite, NovoConvite, RevisaoImportacao, RouterLink],
  templateUrl: './admin-convites.html',
  styleUrl: '../admin.css',
  host: { class: 'block' },
})
export class AdminConvites {
  private readonly http = inject(HttpClient);
  private readonly sanitizador = inject(DomSanitizer);
  private readonly arquivo = viewChild<ElementRef<HTMLInputElement>>('arquivo');
  private readonly dialogoEmail = viewChild<ElementRef<HTMLDialogElement>>('dialogoEmail');

  readonly id = input.required<string>();

  protected readonly empresa = httpResource<EmpresaDetalhe>(() => `${API_ADMIN}/empresas/${this.id()}`);
  protected readonly convites = httpResource<Convites>(() => `${API_ADMIN}/empresas/${this.id()}/convites`);
  private readonly dados = computed(() => (this.convites.hasValue() ? this.convites.value() : null));

  protected readonly ROTULO_STATUS = ROTULO_STATUS;
  protected readonly CLASSE_STATUS = CLASSE_STATUS;
  protected readonly quando = quando;
  protected readonly filtro = signal<Filtro>('todos');
  protected readonly busca = signal('');
  protected readonly selecionado = signal<number | null>(null);
  protected readonly importacao = signal<Importacao | null>(null);
  protected readonly lendo = signal(false);
  protected readonly enviando = signal(false);
  protected readonly baixando = signal(false);
  /** Relatório de participação sendo gerado (PDF ou Excel). */
  protected readonly exportando = signal<'pdf' | 'xlsx' | null>(null);
  /** No celular o PDF vai direto para a folha de compartilhar (WhatsApp, e-mail…). */
  protected readonly compartilhaPdf = podeCompartilharArquivos();
  /** PDF já gerado esperando um novo toque (o navegador só compartilha logo depois de um toque). */
  private pdfPronto: File | null = null;
  /** Quantos ainda não responderam (ativos), para o texto do cartão de exportar. */
  protected readonly faltamResponder = computed(() => {
    const r = this.dados()?.resumo;
    return r ? r.total - r.concluiram : 0;
  });
  protected readonly aviso = signal<{ tipo: 'ok' | 'info' | 'erro'; texto: string } | null>(null);
  /** HTML do nosso próprio template de e-mail, mostrado num iframe sem scripts (sandbox). */
  protected readonly htmlEmail = signal<SafeHtml | null>(null);
  protected readonly arrastando = signal(false);
  /** Diálogo "Adicionar líder" aberto. */
  protected readonly adicionando = signal(false);

  protected readonly encerramento = computed(() => {
    const d = this.dados();
    if (!d?.encerraEm) return null;
    return { encerrada: d.encerrada, texto: encerramentoPorExtenso(d.encerraEm) };
  });

  /** O funil, com a porcentagem de quem foi convidado. */
  protected readonly funil = computed(() => {
    const r = this.dados()?.resumo;
    if (!r) return [];
    const pct = (n: number) => (r.total ? Math.round((n / r.total) * 100) : 0);
    return [
      { rotulo: 'Convidados', valor: r.total, pct: null },
      { rotulo: 'Abriram o link', valor: r.abriram, pct: pct(r.abriram) },
      { rotulo: 'Começaram', valor: r.comecaram, pct: pct(r.comecaram) },
      { rotulo: 'Concluíram', valor: r.concluiram, pct: pct(r.concluiram) },
    ];
  });

  protected readonly chips = computed(() => {
    const lista = this.dados()?.convites ?? [];
    const contar = (s: StatusConvite) => lista.filter((c) => c.status === s).length;
    // "Todos" são os ativos: o inativo sai da lista (e dos cálculos) e fica no filtro próprio.
    const todos: { valor: Filtro; texto: string; total: number }[] = [
      { valor: 'todos', texto: 'Todos', total: lista.filter((c) => c.status !== 'INATIVO').length },
    ];
    (['AGUARDANDO_ENVIO', 'ENVIADO', 'ABRIU', 'COMECOU', 'CONCLUIDO', 'INATIVO'] as StatusConvite[]).forEach((s) => {
      const total = contar(s);
      if (total) todos.push({ valor: s, texto: ROTULO_STATUS[s], total });
    });
    return todos;
  });

  protected readonly lista = computed(() => {
    const termo = this.busca().trim().toLowerCase();
    const filtro = this.filtro();
    return (this.dados()?.convites ?? []).filter(
      (c) =>
        (filtro === 'todos' ? c.status !== 'INATIVO' : c.status === filtro) &&
        (!termo || c.nome.toLowerCase().includes(termo) || c.email.toLowerCase().includes(termo)),
    );
  });

  protected readonly pendentesDeEnvio = computed(() => this.dados()?.resumo.aguardandoEnvio ?? 0);

  /** Convites cujo último e-mail não saiu, com o motivo (um só, quando todos falharam pelo mesmo). */
  protected readonly naoSairam = computed(() => {
    const falhas = (this.dados()?.convites ?? []).filter((c) => c.falhaEnvio);
    const motivos = [...new Set(falhas.map((c) => c.falhaEnvio))];
    return {
      ids: falhas.map((c) => c.id),
      motivo: motivos.length === 1 ? motivos[0] : falhas.length ? 'Os motivos estão no histórico de cada líder.' : null,
    };
  });
  protected readonly conviteSelecionado = computed<ConviteItem | null>(
    () => this.dados()?.convites.find((c) => c.id === this.selecionado()) ?? null,
  );

  protected ultimaAtividade(c: ConviteItem): string {
    if (c.falhaEnvio) return 'O e-mail não chegou';
    if (c.concluidoEm) return `Concluiu ${quando(c.concluidoEm).toLowerCase()}`;
    if (c.ultimoAcessoEm) return `Abriu ${quando(c.ultimoAcessoEm).toLowerCase()}`;
    if (c.enviadoEm) return `E-mail ${quando(c.enviadoEm).toLowerCase()}`;
    return `Criado ${quando(c.criadoEm).toLowerCase()}`;
  }

  protected escolherArquivo() {
    this.arquivo()?.nativeElement.click();
  }

  protected aoEscolher(evento: Event) {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    campo.value = '';
    if (arquivo) void this.ler(arquivo);
  }

  protected aoSoltar(evento: DragEvent) {
    evento.preventDefault();
    this.arrastando.set(false);
    const arquivo = evento.dataTransfer?.files?.[0];
    if (arquivo) void this.ler(arquivo);
  }

  private async ler(arquivo: File) {
    if (!/\.(xlsx|csv)$/i.test(arquivo.name)) {
      this.aviso.set({ tipo: 'erro', texto: 'Envie a planilha modelo preenchida (.xlsx) ou um arquivo .csv.' });
      return;
    }
    this.lendo.set(true);
    this.aviso.set(null);
    const corpo = new FormData();
    corpo.append('arquivo', arquivo, arquivo.name);
    try {
      this.importacao.set(
        await firstValueFrom(this.http.post<Importacao>(`${API_ADMIN}/empresas/${this.id()}/convites/importar`, corpo)),
      );
    } catch (e) {
      this.aviso.set({ tipo: 'erro', texto: problemaDe(e).detail ?? 'Não foi possível ler a planilha.' });
    } finally {
      this.lendo.set(false);
    }
  }

  protected criados(total: number) {
    this.importacao.set(null);
    this.filtro.set('todos');
    this.convites.reload();
    this.aviso.set({
      tipo: 'ok',
      texto: `${total} ${total === 1 ? 'convite criado' : 'convites criados'}. ${
        this.dados()?.emailAtivo ? 'Envie quando quiser.' : 'O envio por e-mail está desligado neste servidor.'
      }`,
    });
  }

  protected adicionado(nome: string) {
    this.convites.reload();
    this.filtro.set('todos');
    this.aviso.set({
      tipo: 'ok',
      texto: `${nome} foi adicionado. ${
        this.dados()?.emailAtivo ? 'Envie o convite quando quiser.' : 'O convite sai por e-mail quando o envio estiver liberado.'
      }`,
    });
  }

/** PDF de participação: compartilha no celular, baixa no computador. */
  protected async exportarPdf() {
    if (this.exportando()) return;
    const url = `${API_ADMIN}/empresas/${this.id()}/convites/participacao.pdf`;
    if (!this.compartilhaPdf) return this.baixarRelatorio('pdf', url, 'participacao.pdf');
    this.exportando.set('pdf');
    try {
      const arquivo = this.pdfPronto ?? (await buscarArquivo(url, 'participacao.pdf'));
      const resultado = await compartilharArquivo(arquivo, 'Participação no Diagnóstico do Líder');
      this.pdfPronto = resultado === 'precisa-de-toque' ? arquivo : null;
      if (resultado === 'precisa-de-toque') {
        this.aviso.set({ tipo: 'info', texto: 'O PDF está pronto. Toque em "Compartilhar PDF" de novo para enviar.' });
      }
    } catch {
      this.aviso.set({ tipo: 'erro', texto: 'Não foi possível gerar o PDF agora. Tente de novo.' });
    } finally {
      this.exportando.set(null);
    }
  }

  protected exportarExcel() {
    return this.baixarRelatorio('xlsx', `${API_ADMIN}/empresas/${this.id()}/convites/participacao.xlsx`, 'participacao.xlsx');
  }

  private async baixarRelatorio(tipo: 'pdf' | 'xlsx', url: string, nome: string) {
    if (this.exportando()) return;
    this.exportando.set(tipo);
    try {
      await baixarArquivo(url, nome);
    } catch {
      this.aviso.set({ tipo: 'erro', texto: `Não foi possível gerar ${tipo === 'pdf' ? 'o PDF' : 'a planilha'} agora. Tente de novo.` });
    } finally {
      this.exportando.set(null);
    }
  }

  protected async baixarModelo() {
    this.baixando.set(true);
    try {
      await baixarArquivo(`${API_ADMIN}/empresas/${this.id()}/convites/modelo.xlsx`, 'convite-lideres.xlsx');
    } catch {
      this.aviso.set({ tipo: 'erro', texto: 'Não foi possível gerar a planilha agora.' });
    } finally {
      this.baixando.set(false);
    }
  }

  /** Sem ids: todos que aguardam envio. Com ids: só esses (ex.: os que não saíram). */
  protected async enviar(ids: number[] = []) {
    const d = this.dados();
    if (!d || this.enviando()) return;
    if (!d.emailAtivo) {
      this.aviso.set({
        tipo: 'info',
        texto: 'O envio por e-mail está desligado neste servidor. Os convites ficam prontos aqui até ele ser ligado.',
      });
      return;
    }
    this.enviando.set(true);
    try {
      const r = await firstValueFrom(
        this.http.post<{ enviando: number }>(`${API_ADMIN}/empresas/${this.id()}/convites/enviar`, { ids }),
      );
      this.aviso.set({ tipo: 'ok', texto: `Enviando ${r.enviando} ${r.enviando === 1 ? 'convite' : 'convites'} por e-mail.` });
      setTimeout(() => this.convites.reload(), 2500);
    } catch (e) {
      this.aviso.set({ tipo: 'erro', texto: problemaDe(e).detail ?? 'Não foi possível enviar agora.' });
    } finally {
      this.enviando.set(false);
    }
  }

  protected async verEmail() {
    try {
      const html = await firstValueFrom(
        this.http.get(`${API_ADMIN}/empresas/${this.id()}/convites/email-previa`, { responseType: 'text' }),
      );
      this.htmlEmail.set(this.sanitizador.bypassSecurityTrustHtml(html));
      setTimeout(() => this.dialogoEmail()?.nativeElement.showModal(), 0);
    } catch {
      this.aviso.set({ tipo: 'erro', texto: 'Não foi possível montar o e-mail agora.' });
    }
  }

  protected fecharEmail() {
    this.dialogoEmail()?.nativeElement.close();
  }
}
