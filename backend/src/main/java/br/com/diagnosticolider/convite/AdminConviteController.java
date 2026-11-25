package br.com.diagnosticolider.convite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.diagnosticolider.convite.ConviteDtos.ConviteItem;
import br.com.diagnosticolider.convite.ConviteDtos.Convites;
import br.com.diagnosticolider.convite.ConviteDtos.Criados;
import br.com.diagnosticolider.convite.ConviteDtos.CriarConvites;
import br.com.diagnosticolider.convite.ConviteDtos.Envio;
import br.com.diagnosticolider.convite.ConviteDtos.EnvioFeito;
import br.com.diagnosticolider.convite.ConviteDtos.EnvioIniciado;
import br.com.diagnosticolider.convite.ConviteDtos.Evento;
import br.com.diagnosticolider.convite.ConviteDtos.Importacao;
import br.com.diagnosticolider.convite.ConviteDtos.NovoConvite;
import jakarta.validation.Valid;

/** Convites nominais no painel. Exige usuário logado, como todo /api/admin. */
@RestController
@RequestMapping("/api/admin")
class AdminConviteController {

	private static final MediaType XLSX = MediaType
		.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final ConviteService service;

	private final ParticipacaoService participacao;

	AdminConviteController(ConviteService service, ParticipacaoService participacao) {
		this.service = service;
		this.participacao = participacao;
	}

	/** Relatório de participação para a empresa cobrar quem falta (PDF com a marca dela). */
	@GetMapping("/empresas/{id}/convites/participacao.pdf")
	ResponseEntity<byte[]> participacaoPdf(@PathVariable Long id) {
		return arquivo(participacao.pdf(id), MediaType.APPLICATION_PDF);
	}

	/** O mesmo relatório em HTML, para conferir o layout no navegador. */
	@GetMapping(value = "/empresas/{id}/convites/participacao.html", produces = MediaType.TEXT_HTML_VALUE)
	String participacaoHtml(@PathVariable Long id) {
		return participacao.html(id);
	}

	/** A participação em Excel, com filtro e o link pessoal de quem falta. */
	@GetMapping("/empresas/{id}/convites/participacao.xlsx")
	ResponseEntity<byte[]> participacaoPlanilha(@PathVariable Long id) {
		return arquivo(participacao.planilha(id), XLSX);
	}

	private static ResponseEntity<byte[]> arquivo(ParticipacaoService.Arquivo arquivo, MediaType tipo) {
		var disposicao = ContentDisposition.attachment().filename(arquivo.nome(), StandardCharsets.UTF_8);
		return ResponseEntity.ok()
			.contentType(tipo)
			.header(HttpHeaders.CONTENT_DISPOSITION, disposicao.build().toString())
			.body(arquivo.conteudo());
	}

	@GetMapping("/empresas/{id}/convites")
	Convites listar(@PathVariable Long id) {
		return service.listar(id);
	}

	/** A planilha modelo, com os cargos da empresa. */
	@GetMapping("/empresas/{id}/convites/modelo.xlsx")
	ResponseEntity<byte[]> modelo(@PathVariable Long id) {
		var disposicao = ContentDisposition.attachment().filename(service.nomeDoModelo(id), StandardCharsets.UTF_8);
		return ResponseEntity.ok()
			.contentType(XLSX)
			.header(HttpHeaders.CONTENT_DISPOSITION, disposicao.build().toString())
			.body(service.modelo(id));
	}

	/** Lê a planilha preenchida e devolve as linhas para revisão (não grava nada). */
	@PostMapping(path = "/empresas/{id}/convites/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	Importacao importar(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo) {
		try {
			return service.importar(id, arquivo.getOriginalFilename(), arquivo.getBytes());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@PostMapping("/empresas/{id}/convites")
	@ResponseStatus(HttpStatus.CREATED)
	Criados criar(@PathVariable Long id, @Valid @RequestBody CriarConvites pedido) {
		return service.criar(id, pedido);
	}

	@PostMapping("/empresas/{id}/convites/enviar")
	EnvioIniciado enviar(@PathVariable Long id, @RequestBody(required = false) Envio pedido) {
		return service.enviar(id, pedido);
	}

	@GetMapping(path = "/empresas/{id}/convites/email-previa", produces = MediaType.TEXT_HTML_VALUE)
	String previaEmail(@PathVariable Long id) {
		return service.previaEmail(id);
	}

	@GetMapping(path = "/empresas/{id}/convites/lembrete-previa", produces = MediaType.TEXT_HTML_VALUE)
	String previaLembrete(@PathVariable Long id) {
		return service.previaLembrete(id);
	}

	@PutMapping("/convites/{id}")
	ConviteItem editar(@PathVariable Long id, @RequestBody NovoConvite pedido) {
		return service.editar(id, pedido);
	}

	/** O link fecha e o líder (com o diagnóstico) sai de todos os cálculos. */
	@PostMapping("/convites/{id}/inativar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void inativar(@PathVariable Long id) {
		service.inativar(id);
	}

	/** O convite de um líder, na hora (para quem não recebeu). Responde depois que o e-mail saiu. */
	@PostMapping("/convites/{id}/enviar")
	EnvioFeito enviarUm(@PathVariable Long id) {
		return service.enviarUm(id);
	}

	@PostMapping("/convites/{id}/reativar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reativar(@PathVariable Long id) {
		service.reativar(id);
	}

	@GetMapping("/convites/{id}/eventos")
	List<Evento> eventos(@PathVariable Long id) {
		return service.eventos(id);
	}

}
