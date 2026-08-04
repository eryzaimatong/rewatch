# 17 - Security

> Reconciled against the actual implementation. The previous version of this
> doc referenced `flask-cors` and predates the current Spring Boot backend
> entirely — it described a system that was never actually shipped.

## Authentication

- Passwords are hashed with BCrypt (`spring-boot-starter-security`'s
  `BCryptPasswordEncoder`) before being persisted; the raw password is never
  stored, logged, or echoed back. `User.password` is additionally marked
  `@JsonProperty(access = WRITE_ONLY)` so it can be deserialized out of a
  register request but can never be serialized into any response, even
  accidentally.
- Sessions are stateless JWTs (`security/JwtService.java`, HS256), issued on
  register/login and validated on every request by `JwtAuthFilter`. No
  server-side session store; logging out is client-side (the token is
  discarded) since there is no refresh-token/revocation mechanism yet — see
  the limitations section of `docs/CASE-STUDY.md`.
- `jwt.secret` follows the same pattern as `tmdb.api.key`: an env-var
  override (`JWT_SECRET`) with an explicitly-labelled dev-only fallback in
  `application.properties`, never committed as a real secret.

## Authorization

Every endpoint that reads or writes a specific user's data is checked
against the *authenticated* caller, not a client-supplied id —
`SecurityUtil.requireSelf(authentication, resourceUserId)` is the single
function every such controller method calls, so the rule lives in one
place. Concretely:

- Endpoints that take a `{userId}` path variable or a body `userId` field
  (ratings, watchlist, onboarding, TasteDNA profile/history/replay,
  recommendations, discovery) 403 if the authenticated caller isn't that
  user.
- The handful of routes that support logged-out browsing (`/api/movies/popular`,
  `/search`, `/nlp-search`, `/trending`, `/top-rated`, `/search-suggestions`,
  and `GET /api/titles`) derive the *personalizing* id from the JWT when one
  is present, rather than trusting a `?userId=` query parameter — a public
  route that trusted that parameter would let anyone read anyone's
  personalized feed without ever logging in.
- The social layer (`/api/social/**`) is a deliberately different shape:
  reads like a public profile or a public list require *a* valid login but
  not that the caller *be* the target (that's the point of a public
  profile); follow/unfollow always act as the authenticated caller — the
  follower id is never accepted from the request, so there is no way to
  forge a follow edge on someone else's behalf.
- `/api/admin/**` (lexicon recompute, feature-stat calibration) requires a
  valid login but not a specific role — there is no admin role system yet.
  Acceptable for a single-operator portfolio app; would need gating before
  any multi-tenant use.

## A bug worth documenting, not just fixing

`SecurityUtil.requireSelf` signals failure by throwing
`org.springframework.web.server.ResponseStatusException`. Left to Spring's
default resolution, that exception can propagate past `DispatcherServlet`
and into Spring Security's `ExceptionTranslationFilter`, which — for a
403 raised against what it considers an anonymous-looking security context —
reroutes it through the configured `AuthenticationEntryPoint`, silently
turning "you're logged in as the wrong user" (403) into "please log in"
(401). The same misrouting caught a second, unrelated bug during testing: a
service method missing `@Transactional` threw `TransactionRequiredException`,
which — left unhandled — took the *same* path and also surfaced as a
misleading 401, nearly hiding a completely different defect.

The fix, `security/SecurityExceptionHandler.java`, is a `@RestControllerAdvice`
that resolves `ResponseStatusException` (and, as a catch-all, any other
uncaught exception) at the highest-precedence resolver
(`ExceptionHandlerExceptionResolver`), fully committing the response inside
the servlet dispatch so it can never reach that filter. Real 403s stay
403s; real 500s stay visible as 500s instead of masquerading as an auth
failure.

## Input handling

- Request bodies are validated with `spring-boot-starter-validation`
  (`@Valid` + Bean Validation annotations — `@NotNull`, `@Min`/`@Max` on
  rating facets, `@NotBlank` on names) before reaching service logic.
- CORS is centralized in one place (`config/WebConfig.java`), not
  per-controller — it used to be declared inconsistently, with one
  controller pinning `localhost:5173` while every other one used `origins="*"`.
- JPA/Hibernate parameterizes all queries by construction (no raw SQL string
  concatenation anywhere in the codebase), which is what actually prevents
  SQL injection here — not a manual escaping step.
