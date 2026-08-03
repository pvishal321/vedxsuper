# Coding Rules and Standards

## General Principles
- **Simplicity over complexity.**
- Follow **Clean Code** principles.
- Use **SOLID** where useful (avoid unnecessary abstractions).
- Keep classes small and focused.
- Write readable code with proper comments.
- Avoid duplicate code (DRY).
- Never create unnecessary interfaces or layers.

## Strategy Rules
- Improve readability, performance, and maintainability.
- Do NOT rewrite the SuperTrend strategy unless specifically instructed.
- Never change the trading logic without approval.

## Login Rules
- Reuse existing Angel One login implementation.
- Do not redesign the login flow.
- Keep it simple and secure.

## UI Philosophy
- Professional feel.
- Keep screens clean.
- Avoid unnecessary animations.
- **Performance is more important than appearance.**
- Dark Mode support is mandatory.

## Development Rules
- Build one module at a time.
- Each module must compile before moving to the next.
- Do not generate placeholder code.
- Do not create files that are not immediately required.
- Always ask before making architectural changes.

## Long-Term Goal
VedxSuper should become a reliable Android application for SuperTrend-based virtual trading. The application should remain lightweight, maintainable, and production-ready for years without unnecessary complexity. Whenever there is a choice between adding more features or keeping the application simple, always choose simplicity.
