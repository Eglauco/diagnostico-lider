package br.com.diagnosticolider.compartilhado;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/** Datas como o líder lê: no horário de Brasília e por extenso ("30 de novembro, às 18h"). */
public final class Datas {

	public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private Datas() {
	}

	/** "30 de novembro, às 18h" (com o ano quando não é o ano corrente; minutos quando há). */
	public static String porExtenso(Instant instante, Instant agora) {
		ZonedDateTime data = instante.atZone(FUSO);
		String mes = data.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
		String ano = data.getYear() == agora.atZone(FUSO).getYear() ? "" : " de " + data.getYear();
		String hora = data.getHour() + "h" + (data.getMinute() == 0 ? "" : "%02d".formatted(data.getMinute()));
		return data.getDayOfMonth() + " de " + mes + ano + ", às " + hora;
	}

}
