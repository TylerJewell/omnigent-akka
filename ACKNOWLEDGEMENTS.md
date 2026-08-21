# Acknowledgements

This project is a port of **[omnigent-ai/omnigent](https://github.com/omnigent-ai/omnigent)**,
read and run against a checkout of its `main` branch (2026-08-21).

## Licence

omnigent-ai/omnigent is **Apache License 2.0**, © Databricks, Inc. A copy of that
licence is included as `LICENSE-omnigent`, which Apache-2.0 requires of any work
carrying its material, along with the notice of what was changed that section 4(b) asks
for — this whole file is that notice.

## What was copied

**No source was copied.** No Python file, fragment or expression from omnigent appears
here; every file in `src/` was written for this project. Two vocabularies were carried
across deliberately, as values rather than code: the `PolicyAction` names (`ALLOW`,
`ASK`, `DENY`), and the `StateUpdate` action names (`SET`, `INCREMENT`, `DELETE`,
`APPEND`) — the exact wire vocabulary the source's `PolicyResponse`/`StateUpdate`
contract uses, kept because they are the terms an operator configuring either system
already knows, not because the code that implements them was reused.

## What is derived

The behaviour is. Every rule in `omnigent-port/specs/SPEC-001-omnigent.md` was
established by reading `omnigent/runtime/policies/engine.py`, `omnigent/policies/base.py`,
`omnigent/policies/function.py`, `omnigent/policies/types.py`, `omnigent/spec/types.py`,
and `docs/POLICIES.md`, then running the real package's own test suite
(`tests/runtime/policies/`, `tests/policies/` — 93 tests, all passing) against a
checkout with a minimal dependency set installed to exercise the policy engine without
the full server stack. The record of what was checked and how is
`omnigent-port/docs/question-log.md`.

This port narrows the source's general-purpose policy system to its composed-decision
core: `FunctionPolicy` only (no `PromptPolicy`, no LLM-backed builtins), a fixed Java
registry of deterministic builtins in place of the source's dotted-path Python import
resolution, and no YAML spec-loading surface. What that narrowing is and is not is
SPEC-001 §1; nothing here claims parity with the parts left out.

## Also used

- **Akka** (Akka SDK for Java, BSL 1.1) — the platform this port is built on.
- **Python** (`pytest`, via a `.venv` built from `omnigent-src/pyproject.toml`) was used
  to run omnigent's own test suite; nothing from that toolchain was copied.
