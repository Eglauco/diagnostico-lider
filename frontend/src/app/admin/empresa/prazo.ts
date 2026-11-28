import { Encerramento } from '../admin-modelos';

/** "30 de nov." (com o ano se não for o corrente). */
export function diaCurto(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString('pt-BR', {
    day: 'numeric',
    month: 'short',
    year: d.getFullYear() === new Date().getFullYear() ? undefined : 'numeric',
  });
}

/** Dias de calendário de hoje até a data (0: hoje; negativo: já passou). */
export function diasAte(iso: string, agora = Date.now()): number {
  const fim = new Date(iso);
  const hoje = new Date(agora);
  return Math.round(
    (new Date(fim.getFullYear(), fim.getMonth(), fim.getDate()).getTime() -
      new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate()).getTime()) /
      86_400_000,
  );
}

export interface PrazoDaAvaliacao {
  /** "Aberta até 30 de nov. · faltam 3 dias", "Encerra hoje, às 22h" ou "Encerrada em 30 de nov.". */
  texto: string;
  aberta: boolean;
  /** Encerra hoje ou amanhã. */
  urgente: boolean;
}

/** O prazo da avaliação em linguagem de gente (null: sem data para encerrar). */
export function prazoDaAvaliacao(enc: Encerramento, agora = Date.now()): PrazoDaAvaliacao | null {
  if (!enc.em) return null;
  if (enc.encerrada) return { texto: `Encerrada em ${diaCurto(enc.em)}`, aberta: false, urgente: false };
  const dias = diasAte(enc.em, agora);
  const d = new Date(enc.em);
  const hora = `${d.getHours()}h${d.getMinutes() ? String(d.getMinutes()).padStart(2, '0') : ''}`;
  const texto =
    dias <= 0
      ? `Encerra hoje, às ${hora}`
      : dias === 1
        ? `Encerra amanhã, às ${hora}`
        : `Aberta até ${diaCurto(enc.em)} · faltam ${dias} dias`;
  return { texto, aberta: true, urgente: dias <= 1 };
}
