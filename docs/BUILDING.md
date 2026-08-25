# Building hitorro-search-ui

Every target Mac-local, Docker, K8s, Orion.

## Requirements

- **Java 21+** (Temurin recommended)
- **Maven 3.9+**
- Everything else is downloaded on demand — `frontend-maven-plugin` pulls a pinned Node 20 + pnpm 8.15 into `backend/target/` so you don't need them on `$PATH`.

## Mac-local

```bash
cd ~/hitorro/hitorro-search-ui
mvn clean install
```

What happens:

1. `frontend-maven-plugin` downloads Node + pnpm to `backend/target/`
2. `pnpm install` on the workspace root — installs deps for the core library + demo-app
3. `pnpm -r build` — TypeScript check + Vite build for both packages
4. `maven-resources-plugin` copies `frontend/packages/demo-app/dist/` into `backend/src/main/resources/static/`
5. `spring-boot-maven-plugin` repackages into a fat jar at `backend/target/hitorro-search-ui-3.0.1.jar`
6. Unit tests run (JvsQueryShaperTest, 11 tests)
7. Jar installed into your local `~/.m2/repository/`

Fast iteration flags:

- `mvn install -Dskip.frontend=true` — backend-only rebuild (skips node/pnpm entirely); use when editing Java only
- `mvn -DskipTests install` — skip the Spring Boot test context spinup + JvsQueryShaperTest
- `mvn -pl backend -am install -Dskip.frontend=true` — narrow scope + skip frontend + include dependencies

## Frontend-only iteration (fastest UI dev loop)

`pnpm dev` runs the Vite dev server with hot-reload against your running backend:

```bash
cd ~/hitorro/hitorro-search-ui/frontend
pnpm install                # once
pnpm dev                    # starts on :5173, proxies /api → :8100
```

Now open `http://localhost:5173` — HMR is instant. `/api/*` is proxied to the backend at `:8100` (see `packages/demo-app/vite.config.ts`), so you can edit the React library or demo-app and never restart Spring Boot.

## Running the packaged jar

```bash
java -jar backend/target/hitorro-search-ui-3.0.1.jar \
    --server.port=8100 \
    --hitorro.searchui.fleet.base-url=http://localhost:8095
```

Env-var equivalents (for K8s / Docker):

- `SEARCH_UI_PORT=8100`
- `SEARCH_UI_FLEET_URL=http://localhost:8095`
- `SEARCH_UI_FLEET_TIMEOUT_MS=15000`
- `SEARCH_UI_CORS_ORIGINS=*` — comma-separated allow-list, `*` for wide-open (dev default), empty string for same-origin only
- `HITORRO_PIPELINES_HOME=$HOME/.hitorro/pipelines` — where the BFF reads `.jvs-type.json` sidecars from for the schema endpoint
- `SPRING_PROFILES_ACTIVE=docker | k8s` — swaps to the pre-baked profile in `application.yml`

## Docker

Multi-stage build (node → maven → jvm):

```bash
cd ~/hitorro/hitorro-search-ui
docker build -f deploy/Dockerfile -t hitorro/search-ui:3.0.1 .
```

Isolated demo compose (fleet-retrieval + search-ui, no full mesh):

```bash
cd deploy
docker compose up --build
open http://localhost:8100
```

The compose file mounts your local `~/.hitorro/pipelines/` into the fleet-retrieval container in shared mode, so it sees every index/KV your mesh has produced.

## Kubernetes (Helm)

```bash
cd ~/hitorro/hitorro-search-ui/deploy/helm/hitorro-search-ui

# Preview what would be applied
helm template search-ui . --set image.tag=3.0.1 --set fleet.baseUrl=http://fleet-retrieval:8095

# Install
helm install search-ui . \
    --namespace hitorro \
    --create-namespace \
    --set image.tag=3.0.1 \
    --set image.repository=your-registry/hitorro/search-ui \
    --set fleet.baseUrl=http://fleet-retrieval:8095

# Optional: expose via ingress
helm upgrade search-ui . --reuse-values \
    --set ingress.enabled=true \
    --set ingress.host=search.your-company.example
```

Chart values worth knowing (see `deploy/helm/hitorro-search-ui/values.yaml` for the full list):

| Value | Default | Purpose |
| --- | --- | --- |
| `image.repository` | `hitorro/search-ui` | Registry + image name |
| `image.tag` | `3.0.1` | Image tag |
| `replicaCount` | `2` | Pod replicas |
| `fleet.baseUrl` | `http://fleet-retrieval:8095` | Fleet-retrieval Service URL in-cluster |
| `service.port` | `8100` | Container + Service port |
| `resources.requests` | `100m / 256Mi` | Baseline; app is mostly-idle proxy |
| `resources.limits` | `500m / 768Mi` | Cap; scale up if you get lots of concurrent facet queries |
| `ingress.enabled` | `false` | Turn on when you want an external URL |
| `ingress.host` | `search.hitorro.example.com` | Ingress hostname |
| `ingress.tls.enabled` | `false` | Terminate TLS at the ingress |

## Orion

```bash
orion service register ~/hitorro/hitorro-search-ui/deploy/orion/search-ui.yaml
```

Positions this as a sibling of `hitorro-fleet-retrieval` in the fleet-* family. The driver's Fleet panel discovers it automatically once it's up.

The manifest expects the fat JAR to be resolvable by Orion (typically via the same Maven repo the rest of the family uses). Override `FLEET_RETRIEVAL_URL` and `PIPELINES_HOME` at register time via tenant config if the defaults don't match your cluster.

## Tests

```bash
# Backend — JUnit + WebTestClient
mvn -pl backend test -Dskip.frontend=true

# Frontend — Vitest (add tests under packages/core/src/**/*.test.ts)
cd frontend && pnpm test
```

11 backend tests covering `JvsQueryShaper` (query composition, execute-body assembly, response translation) ship today. Frontend tests are Phase-2 work — the shell + components are simple enough that the first round of contributions should add coverage.

## Common build failures

| Symptom | Fix |
| --- | --- |
| `Duplicate project '…' in the reactor` | Parent + child pom have the same `artifactId` — check `pom.xml` uses `hitorro-search-ui-parent` and `backend/pom.xml` uses `hitorro-search-ui`. |
| `error TS2688: Cannot find type definition file for 'node'` | `frontend/tsconfig.base.json` shouldn't declare `types: ["node"]` — the browser packages don't need it. |
| `BeanDefinitionOverrideException` for `fleetRetrievalClient` | Bean name collision between the `@Component` class and the `@Bean` WebClient factory — the config's method is `fleetWebClient` for this reason. Don't rename it. |
| `Name for argument of type [java.lang.String] not specified, and parameter name information not available via reflection` | Missing `-parameters` compiler flag — `backend/pom.xml`'s `maven-compiler-plugin` block already sets it; ensure you're not overriding the plugin config. |
| `pnpm install` fails with `ERR_PNPM_LOCKFILE_MISSING_DEPENDENCY` | Delete `frontend/pnpm-lock.yaml` and rebuild — happens if package.jsons changed without a matching lockfile regen. |
