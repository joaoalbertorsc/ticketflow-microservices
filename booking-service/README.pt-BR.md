[English](README.md) | **Português (Brasil)**

# booking-service

> Status: **concluído**

**Responsabilidade:** as reservas e a **saga de reserva** — o fluxo que passa por todos os outros
serviços, com um **circuit breaker do Resilience4j e um método de fallback** em cada chamada,
compensações e um job de recuperação.

| | |
|---|---|
| Porta | 8086 |
| Armazenamento | PostgreSQL `booking_db` |
| Design (em inglês) | [docs/design.md](docs/design.md) |

Veja a [visão geral da arquitetura](../docs/architecture.md), a [saga](../docs/booking-saga.md) e a
[resiliência](../docs/resilience.md) (em inglês) para entender como este serviço se encaixa no
sistema.

## Endpoints

| Método | Caminho | Acesso |
|---|---|---|
| `POST` | `/api/bookings` | autenticado — `{ eventId, sectorId, quantity, cardToken }`, `Idempotency-Key` opcional |
| `GET` | `/api/bookings/{id}` | dono ou `ADMIN` (inclui o log da saga) |
| `GET` | `/api/bookings/me` | autenticado |
| `POST` | `/api/bookings/{id}/cancel` | dono |
| `GET` | `/api/admin/bookings?status=` | `ADMIN` |
| `GET` | `/actuator/circuitbreakers`, `/actuator/circuitbreakerevents` | público |

`POST /api/bookings` responde `201` quando confirmada, `409` em falha de negócio (esgotado,
cartão recusado...) e `503` quando um serviço estava indisponível — sempre com a reserva no corpo
da resposta.

## Onde olhar no código

| O quê | Onde |
|---|---|
| A saga, passo a passo | `application/BookingSaga.java` |
| Desfazimentos (reembolso, liberação) e notificações | `application/Compensations.java` |
| Circuit breakers + métodos de fallback | `infrastructure/client/*Client.java` |
| Configuração dos circuit breakers | `src/main/resources/application.yml` (`resilience4j.circuitbreaker`) |
| Job de recuperação | `application/RecoveryJob.java` |
| Máquina de estados da reserva | `domain/Booking.java` |

## Como rodar

A partir da raiz do repositório:

```bash
docker compose up -d --build
./scripts/demo.sh
```

> O volume do Postgres foi criado antes de este serviço existir? Crie o banco no próprio servidor:
>
> ```bash
> docker compose exec postgres psql -U postgres -c "CREATE USER booking_user WITH PASSWORD 'booking_pass'" -c "CREATE DATABASE booking_db OWNER booking_user"
> ```

## Testes

```bash
./mvnw test
```

- `BookingSagaTest` — cada sucesso, falha e fallback da saga, contra implementações falsas em
  memória
- `CircuitBreakerIntegrationTest` — Resilience4j de verdade contra o WireMock: respostas de
  negócio não contam como falha, 5xx e timeouts contam, OPEN → falha rápida, HALF_OPEN → CLOSED,
  tokens "em nome de" do usuário
- `SagaIntegrationTest` — a saga inteira via HTTP e PostgreSQL: códigos de status, compensações,
  o job de recuperação (desfazimento pendente, queda no meio da saga, notificação pendente),
  idempotência, cancelamento
- `BookingControllerTest` — status por resultado, validação, posse da reserva, rotas exclusivas
  de admin
