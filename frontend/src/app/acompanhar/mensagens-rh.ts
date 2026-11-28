import { LiderRh, primeiroNome } from './rh-api';

/**
 * As mensagens prontas que o RH manda do próprio e-mail ou do WhatsApp (com o link pessoal e o
 * prazo) e os endereços que abrem o e-mail já escrito no Outlook, no Gmail ou no programa do
 * computador. Uma só fonte para a lista, o painel do líder e o "Lembrar todos que faltam".
 */

/** A mensagem de cada situação, com o prazo (frasePrazo: " O prazo vai até…" ou vazio) e o link pessoal. */
export function mensagemDoLider(l: LiderRh, empresa: string, frasePrazo: string): string {
  const nome = primeiroNome(l.nome);
  const link = l.link ?? '';
  switch (l.situacao) {
    case 'COMECOU':
      return `Olá, ${nome}! Falta pouco para você terminar o Diagnóstico do Líder. O link continua de onde você parou.${frasePrazo}\n\n${link}`;
    case 'ABRIU':
      return `Olá, ${nome}! Você já abriu o convite do Diagnóstico do Líder: leva poucos minutos, e as suas respostas não são mostradas à empresa.${frasePrazo}\n\n${link}`;
    default:
      return `Olá, ${nome}! ${empresa} convidou você para o Diagnóstico do Líder: leva poucos minutos, e os seus dados já vêm preenchidos.${frasePrazo}\n\nSeu link pessoal: ${link}`;
  }
}

export function assuntoDoLider(l: LiderRh): string {
  return l.situacao === 'COMECOU' ? 'Falta pouco: seu Diagnóstico do Líder' : 'Seu link do Diagnóstico do Líder';
}

/**
 * O e-mail único para todos que faltam (em cópia oculta): sem link pessoal (cada um tem o seu),
 * aponta para o e-mail do convite e oferece mandar de novo.
 */
export function mensagemParaTodos(empresa: string, frasePrazo: string): { assunto: string; corpo: string } {
  return {
    assunto: `Lembrete: Diagnóstico do Líder da ${empresa}`,
    corpo:
      `Olá! Este é um lembrete do Diagnóstico do Líder da ${empresa}: leva poucos minutos, e as suas respostas não são mostradas à empresa.${frasePrazo}\n\n` +
      `O seu link pessoal está no e-mail "${empresa} convida você para o Diagnóstico de Carreira do Líder" (veja também no spam). ` +
      `Quem já começou continua de onde parou.\n\n` +
      `Não encontrou o e-mail? Responda esta mensagem que mandamos o link de novo.`,
  };
}

export interface EmailPronto {
  para: string[];
  ocultos: string[];
  assunto: string;
  corpo: string;
}

export type ProgramaEmail = 'OUTLOOK' | 'GMAIL' | 'APP';

/** Os endereços que abrem o e-mail já escrito: Outlook na web, Gmail na web e o programa padrão (mailto). */
export function enderecoDoEmail(programa: ProgramaEmail, e: EmailPronto): string {
  const para = e.para.join(',');
  const ocultos = e.ocultos.join(',');
  const consulta = (partes: Record<string, string>) =>
    Object.entries(partes)
      .filter(([, valor]) => valor)
      .map(([chave, valor]) => `${chave}=${encodeURIComponent(valor)}`)
      .join('&');
  switch (programa) {
    case 'OUTLOOK':
      return `https://outlook.office.com/mail/deeplink/compose?${consulta({ to: para, bcc: ocultos, subject: e.assunto, body: e.corpo })}`;
    case 'GMAIL':
      return `https://mail.google.com/mail/?view=cm&fs=1&${consulta({ to: para, bcc: ocultos, su: e.assunto, body: e.corpo })}`;
    case 'APP':
      return `mailto:${e.para.map(encodeURIComponent).join(',')}?${consulta({ bcc: ocultos, subject: e.assunto, body: e.corpo })}`;
  }
}
