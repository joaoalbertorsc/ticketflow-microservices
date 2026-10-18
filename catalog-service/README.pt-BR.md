[English](README.md) | **Português (Brasil)**

# catalog-service

> Status: **concluído**

**Responsabilidade:** locais, eventos, setores e preços. Muito mais lido do que escrito, com cache
no Redis.

| | |
|---|---|
| Porta | 8082 |
| Armazenamento | MongoDB `catalog_db` + cache no Redis |
| Design (em inglês) | [docs/design.md](docs/design.md) |

Veja a [visão geral da arquitetura](../docs/architecture.md) (em inglês) para entender como este
serviço se encaixa no sistema.

## Endpoints

| Método | Caminho | Acesso |
|---|---|---|
| `GET` | `/api/events?type=&city=&from=&to=&page=&size=` | público |
| `GET` | `/api/events/{id}` | público (com cache) |
| `GET` | `/api/venues` | público |
| `POST` | `/api/venues` | `ADMIN` |
| `POST` | `/api/events` | `ADMIN` |
| `PUT` | `/api/events/{id}` | `ADMIN` (apenas rascunhos) |
| `POST` | `/api/events/{id}/publish` | `ADMIN` |
| `POST` | `/api/events/{id}/cancel` | `ADMIN` |
| `GET` | `/api/admin/events?status=` | `ADMIN` |
| `GET` | `/api/admin/events/{id}` | `ADMIN` |
| `GET` | `/internal/events/{eventId}/sectors/{sectorId}` | autenticado (entre serviços, com cache) |

## Como rodar

A partir da raiz do repositório:

```bash
docker compose up -d --build
```

Depois, preencha com locais e eventos de exemplo (precisa do auth-service para o token de admin):

```bash
./scripts/seed.sh
```

Pela IDE, com o MongoDB e o Redis do compose:

```bash
docker compose up -d mongo redis
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Experimente

```bash
curl "localhost:8082/api/events?city=sao%20paulo"
curl localhost:8082/api/events/<id>
docker compose exec redis redis-cli --scan --pattern '*'   # veja o que está em cache
```

## Testes

```bash
./mvnw test
```

- Testes unitários: regras do ciclo de vida do `Event`, serviços de consulta e de comando
- Testes da camada web: acesso público vs. admin, JSON polimórfico de `details`, validação,
  mapeamento de erros
- Testes de integração (o Docker precisa estar rodando): MongoDB + Redis no Testcontainers —
  documentos e preços em `Decimal128` como ficam gravados, acerto e invalidação do cache, e uma
  execução com o **Redis inacessível** provando que o catálogo continua respondendo pelo MongoDB
