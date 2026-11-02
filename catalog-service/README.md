# catalog-service

> Status: **not started**

**Responsibility:** Events, venues, sectors and prices. Read-heavy, cached.

| | |
|---|---|
| Port | 8082 |
| Data store | MongoDB `catalog_db` + Redis cache |

Internal design, data model and API contract will live in [docs/](docs/).
See the [architecture overview](../docs/architecture.md) for how this service fits in.
