# Registrar Error Governance Note - 2026-07-04

## What Changed

- The registrar now has a dedicated custom error page at `/error`.
- Browser-facing controller exceptions are intercepted by a registrar-level exception handler instead of falling through to Spring Boot Whitelabel.
- Common browser failure classes are mapped to controlled responses:
  - `IllegalStateException`
  - `IllegalArgumentException`
  - `NoSuchElementException`
  - binding and validation failures
  - access-denied cases
  - `ResponseStatusException`
  - unexpected exceptions

## What The User Sees Now

- A registrar-branded error page with status code, message, path, timestamp, and back-link.
- No raw Whitelabel fallback for the standard browser flow.

## Validation

- `mvn -q -Dtest=RegistrarErrorHandlingTest test` passed.
- `mvn -q test` passed.
- Live browser probe confirmed that:
  - a bad registrar path now renders the registrar error page
  - an authenticated login that reaches the dashboard parse failure also lands on the registrar error page instead of Whitelabel

## Open Follow-Up

- The dashboard template currently fails during login and is now surfaced safely through the registrar error page.
- That template issue can be repaired later without reintroducing Whitelabel exposure.

