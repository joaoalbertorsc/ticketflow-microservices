[English](README.md) | **Português (Brasil)**

# inventory-service

> Status: **concluído**

**Responsabilidade:** assentos por setor e reservas (reter → confirmar / liberar). Garante que
nunca se vende mais assentos do que existem, mesmo com muita concorrência.

| | |
|---|---|
| Porta | 8083 |
| Armazenamento | PostgreSQL `inventory_db` |
| Design (em inglês) | [docs/design.md](docs/design.md) |

Veja a [visão geral da arquitetura](../docs/architecture.md) (em inglês) para entender como este
serviço se encaixa no sistema.

## Endpoints

| Método | Caminho | Acesso |
|---|---|---|
| `GET` | `/api/inventory/events/{eventId}/sectors` | público |
| `GET` | `/api/inventory/events/{eventId}/sectors/{sectorId}` | público |
| `PUT` | `/api/inventory/events/{eventId}/sectors/{sectorId}` | `ADMIN` — `{ "totalSeats": 500 }` |
| `POST` | `/internal/reservations` | autenticado — `{ bookingId, eventId, sectorId, quantity }` |
| `GET` | `/internal/reservations/booking/{bookingId}` | autenticado |
| `POST` | `/internal/reservations/booking/{bookingId}/confirm` | autenticado |
| `POST` | `/internal/reservations/booking/{bookingId}/release?reason=COMPENSATION\|CANCELLED` | autenticado |

Toda chamada `/internal` é identificada pelo `bookingId` e pode ser repetida com segurança.

## Como rodar

A partir da raiz do repositório:

```bash
docker compose up -d --build
./scripts/seed.sh
```

> O volume do Postgres foi criado antes de este serviço existir? O script de inicialização só roda
> em um volume novo. Recrie o volume (`docker compose down -v`, o que apaga os dados locais) ou crie
> o banco no próprio servidor, mantendo seus dados:
>
> ```bash
> docker compose exec postgres psql -U postgres -c "CREATE USER inventory_user WITH PASSWORD 'inventory_pass'" -c "CREATE DATABASE inventory_db OWNER inventory_user"
> ```

Pela IDE, com o Postgres do compose:

```bash
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Experimente

```bash
TOKEN=...   # o token de qualquer usuário, obtido no auth-service
curl -X POST localhost:8083/internal/reservations -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"bookingId":"3f2b...","eventId":"<event>","sectorId":"balcao","quantity":2}'
curl localhost:8083/api/inventory/events/<event>/sectors
```

## Testes

```bash
./mvnw test
```

- Testes unitários: máquina de estados da reserva, regras de idempotência, tratamento da expiração
- Testes da camada web: acesso público / admin / interno, validação, mapeamento de erros
- Testes de integração (o Docker precisa estar rodando): PostgreSQL real — contagem de assentos ao
  longo de todo o ciclo de vida, chamadas repetidas, confirmação após a expiração, o job de
  expiração, redimensionamento
- **Teste de concorrência:** 50 compradores em paralelo, 10 assentos → exatamente 10 conseguem,
  `available_seats = 0`
