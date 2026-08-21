# omnigent-akka

Decides ALLOW, ASK or DENY for one action an AI coding agent is about to take, by
running every rule attached to that agent's session in order and combining their
verdicts — the same decision omnigent's policy engine makes before letting a tool call,
a request, or a response through, no matter which coding-agent backend is driving the
session.

A port of [omnigent-ai/omnigent](https://github.com/omnigent-ai/omnigent) onto **Akka**,
built with **Akka Specify**.

---

## Where it came from

omnigent-ai/omnigent is a shared control layer that sits in front of several different
coding-agent backends — Claude Code, Codex, Cursor, and others — so the same rules can
be enforced no matter which one is doing the work. It was ported to derive a
specification format precise enough to regenerate a system on a different stack — the
port is the vehicle, the specification is the deliverable.

Only one part of omnigent is rebuilt here: the composed policy-decision procedure and
enough of a multi-backend surface to show that one shared set of rules governs every
backend identically. The specifications the port was generated from are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness)
under `omnigent-port/`.

---

## omnigent-ai/omnigent → this port

📉 1,378 Python lines (the matching part of the source) → **494 Java lines**<br>
📁 24 files → **17 files**<br>
⚡ 19,838 nanoseconds → **293 nanoseconds** to decide with no rules attached<br>
⚡ 89,901 nanoseconds → **425 nanoseconds** to decide when an earlier rule already says no<br>
🧪 93 tests confirmed against the running original → **16 tests**, 9 rules checked
against both sides

Full method and the numbers that did *not* make this list:
[`bench/REPORT.md`](https://github.com/TylerJewell/akka-specify-harness/blob/main/omnigent-port/bench/REPORT.md).

---

## What it took to build

⏱️ **0.6 hours** from the first command to the published repository, **0.6** of them active<br>
💬 **311** exchanges with the model<br>
✍️ **147,167** tokens written by the model, **63,001,496** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **16** tests

```bash
python toolkit/tokens.py --port omnigent    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/akka-specify-harness/tree/main/port-log).

---

## What it does

An agent session has a list of rules attached to it, in a fixed order. Every time the
agent is about to do something — send a request, call a tool, receive a tool's result,
or send a response — each rule that applies gets asked what it thinks, and the answers
combine into one decision:

- **The first rule that says no wins, and nothing after it runs.** Whatever any later
  rule might have decided never gets asked.
- **If nothing says no but at least one rule wants to ask first, the action waits for a
  yes from a person — and nothing either rule wanted to change (a label, a running
  count) takes effect until that yes arrives.** A no after waiting still leaves no
  trace.
- **If a rule that already said yes wanted to change something, and a later rule then
  says no, that earlier change still happens.** Only what the asking-first rule wanted
  is held back.
- **Two agent sessions never see each other's rules, labels, or running counts, even
  when they were set up with the exact same rules.** Which backend — Claude Code,
  Codex, or any other — sent the event makes no difference to any of this; the same
  rules produce the same decision either way.

Nothing here calls a language model. The work is a decision over rules already
configured and an event already received; what the agent does after being told yes, no,
or wait belongs to a different part of omnigent.

---

## Design decisions

**The decision-maker is a plain calculation.** It takes the current rules and the
current running counts as input and returns an answer, the same way a calculator does —
nothing about *how* the answer was reached is kept around afterward, so a rule that
throws an error partway through just becomes a no, and everything else keeps working.

**Every session is kept completely separate.** Two agents working at the same time,
even under identical rules, cannot see or change each other's running counts, the same
way nobody would want one agent's mistake to use up another agent's budget.

**Which backend sent an event is recorded, but never changes the decision.** A
Claude Code session and a Codex session governed by the same rules get the same answer
every time, which is what lets one shared rule set actually be shared.

**A rule that changes what it is given passes its result to the next rule in line.**
When one rule edits an event on its way through — cutting out a password before a
second rule looks at it, say — the second rule sees the edited version, so several
small rules can each do one job instead of one rule having to do all of them.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/omnigent-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9040.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9040**.

### Try it

```bash
# attach a rule that stops a run-shell tool call after it has happened twice
curl -X POST localhost:9040/sessions/demo/policies -H 'Content-Type: application/json' -d '{
  "policies": [{
    "name": "limit",
    "on": [{"phase": "TOOL_CALL"}],
    "condition": null,
    "setLabelsWhitelist": null,
    "builtin": "max_tool_calls_per_session",
    "config": {"limit": 2}
  }],
  "labelDefs": {}
}'

# ask three times in a row
curl -X POST localhost:9040/sessions/demo/evaluate -H 'Content-Type: application/json' \
  -d '{"phase":"TOOL_CALL","toolName":"run_shell","content":{"cmd":"ls"},"harness":"claude-sdk"}'
curl -X POST localhost:9040/sessions/demo/evaluate -H 'Content-Type: application/json' \
  -d '{"phase":"TOOL_CALL","toolName":"run_shell","content":{"cmd":"ls"},"harness":"claude-sdk"}'
curl -X POST localhost:9040/sessions/demo/evaluate -H 'Content-Type: application/json' \
  -d '{"phase":"TOOL_CALL","toolName":"run_shell","content":{"cmd":"ls"},"harness":"codex-native"}'
# the third call comes back DENY, whichever backend sent it
```

---

## Configuration

There are no environment variables. The one setting is the port it listens on, written
in `src/main/resources/application.conf`:

```
akka.javasdk.dev-mode.http-port = 9040
```

---

## Where it differs from omnigent-ai/omnigent

Everything not listed here behaves the same way on purpose, including the parts that
look like mistakes.

- **What kinds of rules exist.** omnigent supports rules backed by a Python function
  (with an arbitrary import path or a factory call) and rules backed by a language
  model's judgement. This port supports only a fixed, named set of function-style
  rules — a run-shell limiter, an ask-before, a deny, and a label-writer — chosen to
  demonstrate how rules combine rather than to cover every kind omnigent ships.
- **How a rule is configured.** omnigent reads rules from a YAML file at startup, plus a
  REST API and a chat command for changing them live. This port takes rules over one
  HTTP call per session, as plain data, with no file format or live-editing surface.
- **What happens after an ask-first rule fires.** omnigent surfaces the wait as a prompt
  a person answers, with a timeout. This port stops at reporting that a wait is needed
  and what would be written once approved — collecting that yes or no from a person
  is a job for whatever calls this port, not something built into it.
- **How long labels and running counts are kept.** omnigent persists them to a SQL
  database so they survive a server restart. This port keeps them in the same durable
  storage every other Akka entity uses, which is a different mechanism but the same
  guarantee — both survive a restart; `not checked` against the two failing in exactly
  the same way under every kind of crash.

---

## Licence

omnigent-ai/omnigent is Apache License 2.0, © Databricks, Inc. This port reimplements
the behaviour without copied source; see [`ACKNOWLEDGEMENTS.md`](ACKNOWLEDGEMENTS.md).
