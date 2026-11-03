[English](README.md) | **Português (Brasil)**

# notification-service

> Status: **concluído**

**Responsabilidade:** notificações por e-mail simuladas (reserva confirmada / falhou / cancelada),
cada uma guardada como um documento completo e nunca enviada duas vezes para a mesma reserva.

| | |
|---|---|
| Porta | 8085 |
| Armazenamento | MongoDB `notification_db` |
| Design (em inglês) | [docs/design.md](docs/design.md) |

Veja a [visão geral da arquitetura](../docs/architecture.md) (em inglês) para entender como este
serviço se encaixa no sistema.

## Endpoints

| Método | Caminho | Acesso |
|---|---|---|
| `POST` | `/internal/notifications` | autenticado — `{ bookingId, type, recipient, data }` |
| `GET` | `/internal/notifications/booking/{bookingId}` | autenticado |
| `GET` | `/api/notifications/me?page=&size=` | autenticado (apenas as próprias notificações) |
| `GET` | `/api/admin/notifications?bookingId=&page=&size=` | `ADMIN` |

Endereços terminados em `@bounce.test` são rejeitados pelo envio simulado (gravados como `FAILED`).

## Como rodar

A partir da raiz do repositório:

```bash
docker compose up -d --build
docker compose logs -f notification-service   # os e-mails "enviados" aparecem aqui
```

> O volume do MongoDB foi criado antes de este serviço existir? Crie o usuário no próprio servidor:
>
> ```bash
> docker compose exec mongo mongosh -u root -p root --quiet --eval "db.getSiblingDB('notification_db').createUser({user:'notification_user',pwd:'notification_pass',roles:[{role:'readWrite',db:'notification_db'}]})"
> ```

## Experimente

```bash
TOKEN=...   # o token de um usuário, obtido no auth-service
curl -X POST localhost:8085/internal/notifications -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{
    "bookingId": "<uuid>", "type": "BOOKING_FAILED",
    "recipient": {"name": "Ana", "email": "ana@mail.com"},
    "data": {"eventName": "Hamlet", "reason": "Card declined"}}'
curl localhost:8085/api/notifications/me -H "Authorization: Bearer $TOKEN"
```

## Testes

```bash
./mvnw test
```

- Testes unitários: cada template, campos obrigatórios lidos dos placeholders, registro completo
  de templates, idempotência por "reservar a vaga e depois enviar", e-mails devolvidos
- Testes da camada web: validação, `201` vs. `200`, apenas as próprias notificações, busca
  exclusiva para admin
- Testes de integração (o Docker precisa estar rodando): MongoDB real — formato do documento
  gravado e dos UUIDs, índice único, tipos separados por reserva, **10 requisições idênticas ao
  mesmo tempo enviam um único e-mail**, paginação por usuário da mais nova para a mais antiga
