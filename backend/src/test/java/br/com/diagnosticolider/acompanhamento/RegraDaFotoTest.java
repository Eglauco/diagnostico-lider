package br.com.diagnosticolider.acompanhamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import br.com.diagnosticolider.acompanhamento.RegraDaFoto.Aguardar;
import br.com.diagnosticolider.acompanhamento.RegraDaFoto.Manter;
import br.com.diagnosticolider.acompanhamento.RegraDaFoto.Nova;

/** O resultado que a empresa vê nunca muda por causa de uma pessoa só. */
class RegraDaFotoTest {

	@Test
	void semFotoAbreComTresConcluidos() {
		assertThat(RegraDaFoto.decidir(ids(2), null, false, false)).isEqualTo(new Aguardar(2));
		Set<UUID> tres = ids(3);
		assertThat(RegraDaFoto.decidir(tres, null, false, false)).isEqualTo(new Nova(tres, false, false));
	}

	@Test
	void umaOuDuasRespostasNovasNaoMudamOResultado() {
		Set<UUID> foto = ids(10);
		assertThat(RegraDaFoto.decidir(mais(foto, 1), foto, false, false)).isInstanceOf(Manter.class);
		assertThat(RegraDaFoto.decidir(mais(foto, 2), foto, false, false)).isInstanceOf(Manter.class);
		Set<UUID> treze = mais(foto, 3);
		assertThat(RegraDaFoto.decidir(treze, foto, false, false)).isEqualTo(new Nova(treze, false, false));
	}

	@Test
	void noFimQuemChegouDepoisDaUltimaFotoFicaDeForaSeForemMenosDeTres() {
		Set<UUID> foto = ids(10);
		// Prazo venceu com 2 respostas novas: o resultado final é a mesma base, agora completo.
		assertThat(RegraDaFoto.decidir(mais(foto, 2), foto, false, true)).isEqualTo(new Nova(foto, true, true));
		// Com 3 novas, elas entram.
		Set<UUID> treze = mais(foto, 3);
		assertThat(RegraDaFoto.decidir(treze, foto, false, true)).isEqualTo(new Nova(treze, true, false));
		// Já final e nada novo: continua.
		assertThat(RegraDaFoto.decidir(foto, foto, true, true)).isInstanceOf(Manter.class);
	}

	@Test
	void inativarNaoMudaOResultadoSozinho() {
		Set<UUID> foto = ids(10);
		Set<UUID> semUm = new HashSet<>(foto);
		semUm.remove(foto.iterator().next());
		assertThat(RegraDaFoto.decidir(semUm, foto, false, false)).isInstanceOf(Manter.class);
		// Na próxima foto (3 novas), quem foi inativado sai junto.
		Set<UUID> proxima = mais(semUm, 3);
		assertThat(RegraDaFoto.decidir(proxima, foto, false, false)).isEqualTo(new Nova(proxima, false, false));
	}

	@Test
	void reabrirVoltaAoParcialComAMesmaBase() {
		Set<UUID> foto = ids(10);
		assertThat(RegraDaFoto.decidir(foto, foto, true, false)).isEqualTo(new Nova(foto, false, true));
	}

	@Test
	void notaExplicaOQueFicouDeFora() {
		assertThat(ResultadoDaEmpresa.nota(false, 2)).contains("pelo menos 3 respostas novas");
		assertThat(ResultadoDaEmpresa.nota(true, 0)).isNull();
		assertThat(ResultadoDaEmpresa.nota(true, 1)).startsWith("1 resposta chegou depois").endsWith("de quem era.");
		assertThat(ResultadoDaEmpresa.nota(true, 2)).startsWith("2 respostas chegaram depois").endsWith("de quem eram.");
	}

	private static Set<UUID> ids(int n) {
		return IntStream.range(0, n).mapToObj(i -> UUID.randomUUID()).collect(Collectors.toSet());
	}

	private static Set<UUID> mais(Set<UUID> base, int n) {
		Set<UUID> todos = new HashSet<>(base);
		todos.addAll(ids(n));
		return todos;
	}

}
