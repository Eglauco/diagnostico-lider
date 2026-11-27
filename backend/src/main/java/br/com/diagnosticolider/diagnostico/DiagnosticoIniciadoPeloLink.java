package br.com.diagnosticolider.diagnostico;

import java.util.UUID;

/**
 * Um líder começou o diagnóstico pelo link aberto de uma empresa (não pelo link pessoal do
 * convite). O cadastro de líderes da empresa o recebe na mesma transação.
 */
public record DiagnosticoIniciadoPeloLink(UUID diagnosticoId) {
}
