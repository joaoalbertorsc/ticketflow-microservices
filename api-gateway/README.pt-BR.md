[English](README.md) | **Português (Brasil)**

# api-gateway

> Status: **concluído**

**Responsabilidade:** o ponto de entrada único. Encaminha cada caminho público ao seu serviço,
rejeita na borda as requisições sem um token válido, nunca expõe `/internal/**` e marca cada
requisição com um `X-Request-Id`.

| | |
|---|---|
| Porta | 8080 |
| Armazenamento | — |
| Design (em inglês) | [docs/design.md](docs/design.md) |

Veja a [visão geral da arquitetura](../docs/architecture.md) (em inglês) para entender como este
serviço se encaixa no sistema.

## Rotas

| Caminho | Serviço |
|---|---|
| `/api/auth/**`, `/api/users/**` | auth-service |
| `/api/events/**`, `/api/venues/**`, `/api/admin/events/**` | catalog-service |
| `/api/inventory/**` | inventory-service |
| `/api/payments/**` | payment-service |
| `/api/notifications/**`, `/api/admin/notifications/**` | notification-service |
| `/api/bookings/**`, `/api/admin/bookings/**` | booking-service |
| `/internal/**` | nunca encaminhado — `404` |

As rotas são configuração, em `src/main/resources/application.yml`. O código Java se resume às
regras de segurança (`SecurityConfig`) e ao filtro de id de requisição (`RequestIdFilter`).

## Como rodar

A partir da raiz do repositório:

```bash
docker compose up -d --build
./scripts/demo.sh           # todas as chamadas à API passam por localhost:8080
```

## Experimente

```bash
curl localhost:8080/api/events                     # público
curl -i localhost:8080/api/bookings/me             # 401 no gateway, nunca chega ao booking-service
curl -i localhost:8080/internal/users/x            # 404: endpoints internos não são expostos
docker compose logs api-gateway | grep access      # uma linha por requisição, com o X-Request-Id
```

## Testes

```bash
./mvnw test
```

O `GatewayTest` roda o gateway de verdade na frente de seis servidores WireMock, um por serviço:
- cada rota chega ao serviço certo, com caminho, query, corpo e headers inalterados;
- rotas públicas não precisam de token; as protegidas recebem `401` sem token ou com um token
  forjado, expirado ou de outro emissor, e a requisição nunca chega ao serviço;
- `/internal/**` responde `404` mesmo com um token válido;
- ids de requisição são gerados, mantidos quando válidos, substituídos quando não, e devolvidos
  também nas rejeições.
