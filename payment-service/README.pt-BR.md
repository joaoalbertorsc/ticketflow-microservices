[English](README.md) | **Português (Brasil)**

# payment-service

> Status: **concluído**

**Responsabilidade:** cobranças e reembolsos simulados para a saga de reserva, idempotentes pelo
id da reserva, além de um modo caos para demonstrar o circuit breaker do `booking-service`.

| | |
|---|---|
| Porta | 8084 |
| Armazenamento | PostgreSQL `payment_db` |
| Design (em inglês) | [docs/design.md](docs/design.md) |

Veja a [visão geral da arquitetura](../docs/architecture.md) (em inglês) para entender como este
serviço se encaixa no sistema.

## Endpoints

| Método | Caminho | Acesso |
|---|---|---|
| `POST` | `/internal/payments` | autenticado — `{ bookingId, amount, currency, cardToken }` |
| `POST` | `/internal/payments/booking/{bookingId}/refund` | autenticado |
| `GET` | `/internal/payments/booking/{bookingId}` | autenticado |
| `GET` | `/api/payments/booking/{bookingId}` | dono do pagamento ou `ADMIN` |
| `GET` / `PUT` / `DELETE` | `/api/payments/admin/chaos` | `ADMIN` — `{ "failureRate": 0.5, "latencyMs": 2000 }` |

Cartões de teste: `tok_declined` (saldo insuficiente), `tok_fraud` (suspeita de fraude); qualquer
outro é aprovado. Valores acima de 50 000.00 são recusados.

## Como rodar

A partir da raiz do repositório:

```bash
docker compose up -d --build
```

> O volume do Postgres foi criado antes de este serviço existir? Crie o banco no próprio servidor:
>
> ```bash
> docker compose exec postgres psql -U postgres -c "CREATE USER payment_user WITH PASSWORD 'payment_pass'" -c "CREATE DATABASE payment_db OWNER payment_user"
> ```

## Experimente

```bash
TOKEN=...   # o token de um usuário, obtido no auth-service
curl -X POST localhost:8084/internal/payments -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"bookingId":"<uuid>","amount":690.00,"currency":"BRL","cardToken":"tok_visa"}'

ADMIN=...   # o token do administrador
curl -X PUT localhost:8084/api/payments/admin/chaos -H "Authorization: Bearer $ADMIN" \
  -H "Content-Type: application/json" -d '{"failureRate":1.0,"latencyMs":0}'
```

## Testes

```bash
./mvnw test
```

- Testes unitários: transições do pagamento, regras do gateway simulado, modo caos, idempotência
  e anulação no serviço
- Testes da camada web: recusa = `201`, caos = `503`, dono vs. outros vs. admin, caos só para admin
- Testes de integração (o Docker precisa estar rodando): PostgreSQL real — cobrança/reembolso, sem
  segunda chamada ao gateway em uma nova tentativa, reembolso antes da cobrança, **um reembolso que
  ultrapassa uma cobrança lenta ainda em andamento**, caos não grava nada, reembolsos não são
  afetados pelo caos
- **Teste de concorrência:** 20 cobranças idênticas ao mesmo tempo → o gateway é chamado
  exatamente uma vez
