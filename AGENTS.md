# Agent Rules

These rules apply to any AI-assisted code changes in this repository.

## Documentation Requirements

- Every new public class, interface, enum, object, and function must include KDoc that explains what it does.
- Every non-trivial private helper should include a short KDoc comment describing purpose.
- If logic has constraints or side effects, document assumptions directly above the code.
- Keep documentation accurate when behavior changes. Updating code without updating docs is not allowed.

## Implementation Standards

- Prefer small, composable functions with clear names.
- Avoid hidden behavior in UI layers; move persistence and side effects to repository/domain layers.
- Keep changes incremental and buildable at each step.
- Preserve existing architecture unless a task explicitly requires refactoring.

## Validation

- Run a project build after feature changes.
- Add or update tests for behavior changes when practical.
