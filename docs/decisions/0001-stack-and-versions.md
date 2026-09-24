# ADR 0001 - Stack e versioni

**Problema.** La specifica chiede Java 21 e "Spring Boot in una versione stabile compatibile con
Java 21", React/TypeScript/Vite, React Router, TanStack Query, RHF + Zod.

**Alternative.** Spring Boot 3.5.x (maturo ma fuori dal supporto OSS da giugno 2026) oppure
Spring Boot 4.0.x (linea corrente, Spring Framework 7, Jakarta EE 11, Hibernate 7, Jackson 3).

**Decisione.** Spring Boot **4.0.8** (ultima patch della linea 4.0 al 24/09/2026), Java 21,
Maven Wrapper 3.3.4 (Maven 3.9.11). Frontend: React 19, React Router 8, TanStack Query 5,
React Hook Form 7 + Zod 4, Vite 8, Vitest 5, Playwright 1.56, TypeScript 6 in modalità strict.

**Motivazione.** Linea supportata, starter modulari (`spring-boot-starter-webmvc`,
`spring-boot-starter-flyway`), Problem Details nativi, CSRF SPA integrato in Spring Security 7.
React Router 8 mantiene l'API di data router della v7 (`createBrowserRouter`, `RouterProvider`).

**Conseguenze.** I test usano `org.springframework.boot.webmvc.test.autoconfigure` e
Testcontainers 2 (`org.testcontainers.postgresql.PostgreSQLContainer`). Lo schema è validato
da Hibernate (`ddl-auto=validate`) e creato solo da Flyway.
