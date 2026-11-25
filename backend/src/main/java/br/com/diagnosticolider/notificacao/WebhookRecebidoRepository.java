package br.com.diagnosticolider.notificacao;

import org.springframework.data.jpa.repository.JpaRepository;

interface WebhookRecebidoRepository extends JpaRepository<WebhookRecebido, String> {
}
