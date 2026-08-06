# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Реестр ресурсов** — a resource registry for a bioinformatics platform. It manages bioinformatics workflows, tools, and notebooks stored in GitHub repositories, parsing `.dockstore.yml` files to index resources, versions, authors, Docker images, and source file attachments.

## Tech Stack

- **Backend**: Java 21 (compiled), Maven build, BE5 framework (`com.developmentontheedge.be5`)
- **Database**: PostgreSQL (dev: H2 in-memory)
- **Frontend**: React 16, Webpack 4, BE5 React components (`be5-react`)
- **Deployment**: Docker Compose (PostgreSQL + Tomcat)
- **i18n**: English and Russian (`l10n: [en,ru]`)

## Key Commands

```bash
# Backend build
mvn package

# Frontend dev server (hot reload)
npm start

# Frontend production build
npm run build

# Frontend minified build
npm run build-min

# Frontend tests (Jest)
npm test

# Java tests (JUnit)
mvn test
```

## Architecture

### Backend (Java + BE5)

- **Entry point**: `src/main/java/ru/genespace/registry/AppMain.java` — starts embedded Jetty
- **Servlet config**: `AppGuiceServletConfig.java` — Guice DI, installs BE5 core/web/monitoring modules plus custom `GitHubModule` and `WebserverApiModule`
- **Custom API**: `WebserverController.java` handles `/webserver/*` routes:
  - `GET /webserver/web/content` — fetches file content from GitHub (with caching), supports markdown rendering
  - `GET /webserver/web/dag` — generates workflow DAG diagram images (WDL/Nextflow)
- **GitHub integration**: `ru.genespace.github.*` — `GitHubManager` parses `.dockstore.yml` from GitHub repos, maps to Resources/Versions. Uses `GitHubRepository` for low-level Git API access.
- **Content caching**: `CachedContentManager` caches file contents and DAG images to avoid repeated GitHub API calls.

### Data Model (YAML-driven)

Entity schemas are defined in `src/meta/entities/*.yaml`. The BE5 framework generates database tables, CRUD queries, and UI from these definitions. Key entities:

| Entity | Description |
|--------|-------------|
| `repositories` | GitHub repositories (URL, DOI) |
| `resources` | Tools, workflows, notebooks (type, language, name) |
| `versions` | Git branches/tags with commit hashes |
| `resource2versions` | Junction: links resources to versions, stores descriptor paths |
| `docker` | Docker images associated with resources |
| `resource2docker` | Junction: resource-version to Docker image |
| `attachments` | Source files (workflow scripts, etc.) |
| `authors` | Author metadata (name, email, ORCID) |
| `resource2author` | Junction: resource-version to author |

### Operations (Groovy)

Custom business logic lives in `src/main/groovy/operations/*.groovy`. These extend `GOperationSupport` and are invoked from entity query layouts (e.g., `AddRepository.groovy` parses a GitHub repo and populates the database).

### Frontend (React)

- **Entry**: `src/frontend/scripts/initApp.js` — initializes BE5 app with Redux store
- **Page registration**: `src/frontend/scripts/register.js` — imports all page modules
- **Pages** (each calls `registerPage()` with a route handler):
  - `ResourceCardPage` — resource detail hub with navigation tabs
  - `ResourceTabPage` — shows resource metadata + rendered README markdown
  - `ResourceFilePage` — lists and displays workflow source files
  - `ResourceDAGPage` — renders workflow dependency graph image
- **Table boxes**: `src/frontend/scripts/tableBoxes/ResourceTabTableBox.js` — custom BE5 table box component
- **Shared utilities**: `src/frontend/scripts/utils.js` — `Field`, `FieldNotEmpty`, `createPageValueLocal`

### Configuration Files

| File | Purpose |
|------|---------|
| `project.yaml` | BE5 project config: entities, modules, l10n, scripts |
| `src/security.yaml` | Role definitions (Administrator, Guest, SystemDeveloper, User) |
| `src/daemons.yaml` | Daemon configuration (currently empty) |
| `src/connectionProfiles.*.yaml` | Database connection profiles |
| `src/forms.yaml` | Form definitions |
| `src/customization.yaml` | UI customizations |

### Docker

`Docker/docker-compose.yaml` runs PostgreSQL 16 + Tomcat 8 (JDK 21). The WAR is mounted from `docker.in/registry.war`. For development, `docker.in/src` is hot-reloaded into Tomcat.

## Conventions

- Entity YAML files use BE5's `KEYTYPE` for auto-increment PKs and `___` suffix for audit fields (whoInserted___, creationDate___, etc.)
- Queries use BE5 template syntax: `<if parameter="...">`, `<parameter:.../>`, `<@COMMON_WHO_FIELDS 'alias' />`
- Layouts are JSON strings in entity YAML controlling UI rendering (`{"quickType":"select", "layout":"..."}`)
- Operations reference Groovy files by dotted path: `file: operations.AddRepository.groovy`
- Frontend pages register themselves via `registerPage(name, Component, routeHandler)` — the route handler transforms URL params into page state
- The `webserver/web/content` endpoint resolves owner IDs (resource2versions, resources, versions, repositories) to GitHub repository + reference, then fetches files with caching
