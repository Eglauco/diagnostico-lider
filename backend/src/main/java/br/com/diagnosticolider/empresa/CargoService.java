package br.com.diagnosticolider.empresa;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.diagnosticolider.compartilhado.RegraNegocioException;

/**
 * Lista de cargos de cada empresa. A ordem da lista é a hierarquia. Tirar da lista um cargo que
 * já tem diagnósticos só o arquiva (os relatórios continuam com ele); sem diagnósticos, apaga.
 */
@Service
@Transactional(readOnly = true)
public class CargoService {

	/** Mais que isso deixa a tela do líder longa demais no celular. */
	public static final int MAXIMO = 12;

	public static final int TAMANHO_NOME = 80;

	/** Um item da lista enviada pelo painel (id nulo = cargo novo). */
	public record Pedido(Long id, String nome) {
	}

	/** Cargo ativo e quantos diagnósticos já recebeu (para o painel avisar antes de remover). */
	public record Item(Long id, String nome, long diagnosticos) {
	}

	private final CargoRepository cargos;

	CargoService(CargoRepository cargos) {
		this.cargos = cargos;
	}

	public List<Cargo> ativos(Long empresaId) {
		return cargos.findByEmpresaIdAndAtivoTrueOrderByOrdemAscIdAsc(empresaId);
	}

	/** Todos os cargos, inclusive arquivados, na ordem da hierarquia. */
	public List<Cargo> todos(Long empresaId) {
		return cargos.findByEmpresaIdOrderByOrdemAscIdAsc(empresaId);
	}

	public List<Item> itens(Long empresaId) {
		Map<Long, Long> contagem = contagem(empresaId);
		return ativos(empresaId).stream()
			.map(c -> new Item(c.getId(), c.getNome(), contagem.getOrDefault(c.getId(), 0L)))
			.toList();
	}

	/** Grava a lista na ordem recebida: renomeia, cria, reposiciona, arquiva ou apaga. */
	@Transactional
	public void definir(Empresa empresa, List<Pedido> pedidos) {
		List<Pedido> lista = pedidos == null ? List.of() : pedidos;
		List<String> nomes = validar(lista);

		List<Cargo> existentes = todos(empresa.getId());
		Map<Long, Cargo> porId = existentes.stream().collect(Collectors.toMap(Cargo::getId, Function.identity()));
		Set<Long> mantidos = new HashSet<>();
		for (int i = 0; i < lista.size(); i++) {
			String nome = nomes.get(i);
			Long id = lista.get(i).id();
			Cargo cargo = id == null ? null : porId.get(id);
			if (cargo == null) {
				// Um cargo removido antes, com o mesmo nome, volta com o histórico dele.
				cargo = existentes.stream()
					.filter(c -> !c.isAtivo() && !mantidos.contains(c.getId()) && c.getNome().equalsIgnoreCase(nome))
					.findFirst()
					.orElse(null);
			}
			if (cargo == null) {
				cargo = cargos.save(new Cargo(empresa, nome, i));
			}
			else {
				cargo.atualizar(nome, i);
			}
			mantidos.add(cargo.getId());
		}

		Map<Long, Long> contagem = contagem(empresa.getId());
		existentes.stream().filter(c -> c.isAtivo() && !mantidos.contains(c.getId())).forEach(c -> {
			if (contagem.getOrDefault(c.getId(), 0L) > 0) {
				c.arquivar();
			}
			else {
				cargos.delete(c);
			}
		});
	}

	private static List<String> validar(List<Pedido> lista) {
		if (lista.size() > MAXIMO) {
			throw new RegraNegocioException("Use até %d cargos: mais que isso fica longo para o líder escolher."
				.formatted(MAXIMO)).noCampo("cargos");
		}
		List<String> nomes = lista.stream().map(p -> normalizar(p.nome())).toList();
		if (nomes.stream().anyMatch(String::isEmpty)) {
			throw new RegraNegocioException("Escreva o nome de cada cargo ou remova a linha vazia.").noCampo("cargos");
		}
		nomes.stream().filter(n -> n.length() > TAMANHO_NOME).findFirst().ifPresent(n -> {
			throw new RegraNegocioException("Use até %d caracteres no nome do cargo.".formatted(TAMANHO_NOME))
				.noCampo("cargos");
		});
		Set<String> vistos = new HashSet<>();
		nomes.stream().filter(n -> !vistos.add(n.toLowerCase(Locale.ROOT))).findFirst().ifPresent(n -> {
			throw new RegraNegocioException("O cargo “%s” aparece duas vezes.".formatted(n)).noCampo("cargos");
		});
		return nomes;
	}

	private Map<Long, Long> contagem(Long empresaId) {
		return cargos.contagemDeDiagnosticos(empresaId)
			.stream()
			.collect(Collectors.toMap(ContagemCargo::cargo, ContagemCargo::diagnosticos));
	}

	/** Espaços nas pontas e repetidos saem; nulo vira vazio. */
	public static String normalizar(String nome) {
		return nome == null ? "" : nome.strip().replaceAll("\\s+", " ");
	}

}
