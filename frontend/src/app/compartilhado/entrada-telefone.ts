import { Component, input, model, output } from '@angular/core';
import { FormValueControl } from '@angular/forms/signals';

/**
 * Regra única do WhatsApp, a mesma de `backend/.../compartilhado/Telefones.java` (mude as duas
 * juntas): DDD que existe, celular com o 9 depois do DDD, 10 ou 11 números (o 55 do país é
 * aceito e descartado).
 */
const DDDS = new Set([
  11, 12, 13, 14, 15, 16, 17, 18, 19, 21, 22, 24, 27, 28, 31, 32, 33, 34, 35, 37, 38, 41, 42, 43, 44, 45, 46, 47, 48,
  49, 51, 53, 54, 55, 61, 62, 63, 64, 65, 66, 67, 68, 69, 71, 73, 74, 75, 77, 79, 81, 82, 83, 84, 85, 86, 87, 88, 89,
  91, 92, 93, 94, 95, 96, 97, 98, 99,
]);

/** Só os dígitos, sem o 55 do país quando ele vem na frente de um número completo. */
export function digitosTelefone(valor: string): string {
  return valor.replace(/\D/g, '').replace(/^55(?=\d{10,11}$)/, '');
}

/**
 * O que está errado no número, numa frase; null se está certo ou vazio. Com `digitando`, só o
 * que já é certeza no meio da digitação (DDD que não existe, celular completo sem o 9), sem
 * cobrar os números que ainda faltam.
 */
export function problemaTelefone(valor: string, digitando = false): string | null {
  const d = digitosTelefone(valor);
  if (!d) return null;
  if (d.length >= 2 && !DDDS.has(Number(d.slice(0, 2)))) {
    return `O DDD ${d.slice(0, 2)} não existe: confira os dois primeiros números.`;
  }
  // Quem digita com o 55 na frente passa por 11 números antes do 12º: não acusa no meio do caminho.
  if (d.length === 11 && d[2] !== '9' && !(digitando && d.startsWith('55'))) {
    return 'Celular tem o 9 logo depois do DDD: confira o número.';
  }
  if (digitando) return null;
  if (d.length < 10) return 'Faltam números: digite o DDD e o número, como (11) 98765-4321.';
  if (d.length > 11) return 'Tem números a mais: deixe só o DDD e o número, como (11) 98765-4321.';
  if (d.length === 10 && d[2] >= '6') return 'Falta o 9 do celular logo depois do DDD.';
  if (d.length === 10 && d[2] < '2') return 'Confira o número: celular tem o 9 logo depois do DDD.';
  return null;
}

/** "11987654321" → "(11) 98765-4321", formatando enquanto a pessoa digita (no máximo 11 números). */
export function formatarTelefone(valor: string): string {
  const d = digitosTelefone(valor).slice(0, 11);
  if (d.length === 0) return '';
  if (d.length <= 2) return `(${d}`;
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

/**
 * Aplica a máscara direto no campo e devolve o valor formatado. Reescreve o campo sempre, porque
 * o Angular não mexe no DOM quando o valor formatado não muda (o 12º número ficaria na tela), e
 * mantém o cursor depois do mesmo número quando a pessoa edita no meio.
 */
export function mascararTelefone(alvo: EventTarget | null): string {
  const campo = alvo as HTMLInputElement;
  const bruto = campo.value;
  const formatado = formatarTelefone(bruto);
  if (formatado === bruto) return formatado;
  const cursor = campo.selectionStart ?? bruto.length;
  const noFim = cursor >= bruto.length;
  const numerosAntes = bruto.slice(0, cursor).replace(/\D/g, '').length;
  campo.value = formatado;
  if (!noFim && document.activeElement === campo) {
    let posicao = 0;
    for (let vistos = 0; posicao < formatado.length && vistos < numerosAntes; posicao++) {
      if (/\d/.test(formatado[posicao])) vistos++;
    }
    campo.setSelectionRange(posicao, posicao);
  }
  return formatado;
}

/** Campo de WhatsApp com máscara, compatível com Signal Forms via [formField]. */
@Component({
  selector: 'app-entrada-telefone',
  host: { class: 'block' },
  template: `
    <input
      #campo
      class="entrada-grande"
      type="tel"
      inputmode="tel"
      autocomplete="tel-national"
      enterkeyhint="next"
      placeholder="(11) 98765-4321"
      [id]="idCampo()"
      [value]="value()"
      [attr.aria-invalid]="mostrarErro()"
      [attr.aria-describedby]="descricao()"
      (input)="digitar(campo)"
      (blur)="saiu.emit()"
    />
  `,
})
export class EntradaTelefone implements FormValueControl<string> {
  readonly value = model('');
  /** Preenchido pelo [formField]. O destaque visual usa mostrarErro, que só liga depois de o líder tentar avançar. */
  readonly invalid = input(false);
  readonly mostrarErro = input(false);
  readonly idCampo = input<string>();
  readonly descricao = input<string>();
  readonly saiu = output();

  protected digitar(campo: HTMLInputElement) {
    this.value.set(mascararTelefone(campo));
  }
}
