# Vertical Log Format

Each run of `scripts/run_week1_verticals.sh` writes a timestamped markdown log to:

- `docs/verticals/logs/week1-YYYYMMDD-HHMMSS.md`

## What each step log contains

1. `Result`
- `PASSED` or `FAILED`.

2. `HTTP Status`
- The response code observed for the step's request.

3. `Concept Exercised`
- The system-design concept under test (for example retry-safety, auth boundary).

4. `Request`
- The endpoint/action used during this step.

5. `Class Flow`
- Core class chain exercised end-to-end for that step.

6. `Why It Matters`
- Why this behavior matters from a reliability, correctness, or architecture perspective.

7. `Notes`
- Important details from execution (token length, file id, error body snippets, etc.).

## Usage

```bash
cd /Users/jcpineda/Code/FileStoreDesignLab
./scripts/run_week1_verticals.sh
```

After run completion, open the latest file under `docs/verticals/logs/`.
