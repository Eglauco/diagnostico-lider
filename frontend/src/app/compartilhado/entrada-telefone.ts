import { Component, input, model, output } from '@angular/core';
import { FormValueControl } from '@angular/forms/signals';

/** "11987654321" → "(11) 98765-4321", formatando enquanto o líder digita. */
export function formatarTelefone(valor: string): string {
  const d = valor.replace(/\D/g, '').replace(/^55(?=\d{10,11}$)/, '').slice(0, 11);
  if (d.length === 0) return '';
  if (d.length <= 2) return `(${d}`;
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
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
      maxlength="16"
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
    const formatado = formatarTelefone(campo.value);
    campo.value = formatado;
    this.value.set(formatado);
  }
}
