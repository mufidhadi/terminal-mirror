# Agents Rulebook - Terminal Mirror

Guidelines for AI agents and pair-programming assistants contributing to the Terminal Mirror codebase:

## Engineering Principles
1. **Test-Driven Development (TDD)**: Every protocol modification, parser change, and cipher logic must have automated unit tests before implementation. Run `cargo test` to verify.
2. **SOLID Architecture**: Keep PTY management, transport networking, and cryptographic routines completely decoupled.
3. **Strict Data Privacy & Security**:
   - NEVER commit or log real IP addresses, server credentials, or secrets to the public repository.
   - All sample configs must use generic RFC placeholders (e.g. `127.0.0.1`, `10.x.x.x`, `vpn.example.internal`).
4. **Documentation**:
   - All documentation resides strictly in `/docs` (except root `README.md` and `AGENTS.md`).
   - Every completed task must generate an AI report under `docs/ai_report/xxx_<task_name>.md`.
5. **Git Etiquette**:
   - Always branch off `main` into a feature branch (e.g. `feature/<name>`).
   - Do not commit directly to `main` or protected branches.
   - Do NOT include any co-authoring attributions in commit messages.
