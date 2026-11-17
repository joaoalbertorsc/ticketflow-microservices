[English](README.md) | **Português (Brasil)**

# auth-service

> Status: **concluído**

## Contexto

Todo serviço precisa saber quem está chamando e o que essa pessoa pode fazer, sem que cada um guarde
usuários ou senhas.

## Ação

É dono dos usuários e papéis: cadastro, login com senhas em hash BCrypt e JWTs assinados que os
outros serviços validam sozinhos, sem chamar o auth-service. O primeiro administrador é criado na
inicialização, e só administradores mudam papéis.

## Resultado

A identidade fica em um só lugar. Comprovado por:

- Testes unitários: `UserService`, `AuthenticationService`, `JwtTokenIssuer`
- Testes da camada web: validação, códigos de status e regras de segurança de cada controller
- Teste de integração: a aplicação completa contra um PostgreSQL no Testcontainers (o Docker
  precisa estar rodando)

| | |
|---|---|
| Porta | 8081 |
| Armazenamento | PostgreSQL `auth_db` |
| Design (em inglês) | [docs/design.md](docs/design.md) |

Veja a [visão geral da arquitetura](../docs/architecture.md) (em inglês) para entender como este
serviço se encaixa no sistema.

## Endpoints

| Método | Caminho | Acesso |
|---|---|---|
| `POST` | `/api/auth/register` | público |
| `POST` | `/api/auth/login` | público |
| `GET` | `/api/auth/me` | autenticado |
| `PUT` | `/api/users/{id}/roles` | `ADMIN` |
| `GET` | `/internal/users/{id}` | autenticado (entre serviços) |
| `GET` | `/actuator/health` | público |

## Como rodar

Com Docker (a partir da raiz do repositório):

```bash
docker compose up -d --build postgres auth-service
```

Pela IDE / linha de comando, com o Postgres do compose e o perfil `local`:

```bash
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Experimente

```bash
curl -X POST localhost:8081/api/auth/register -H "Content-Type: application/json" \
  -d '{"name":"Ana Souza","email":"ana@mail.com","password":"Str0ngPass"}'

curl -X POST localhost:8081/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"ana@mail.com","password":"Str0ngPass"}'

curl localhost:8081/api/auth/me -H "Authorization: Bearer <accessToken>"
```

O primeiro administrador (`admin@ticketflow.local` / `Admin12345` por padrão) é criado na
inicialização.

## Testes

```bash
./mvnw test
```
