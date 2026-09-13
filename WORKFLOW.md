# Development Workflow

## Rules

1. **Never implement without explicit approval.**
   Always present the plan first and wait for "ok" or "go" before code
   is written or changes are made.

2. **Explore → Plan → Code.**
   Show the plan before code is written for each new, well-scoped piece of work.

3. **Small steps. TDD.**
   Tests are written before implementation — unit tests always, and
   integration tests too whenever the change crosses a real boundary
   (filesystem, threads, ServiceLoader, network, etc).

4. **After every small step:**
   Show diff + test results. Commit only after the user's approval.

## Git

- Remote: https://github.com/guranxp/metrics-to-file
- Branch: main
- Commit messages in English, short and descriptive
