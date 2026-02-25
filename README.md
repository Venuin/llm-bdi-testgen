# llm-bdi-testgen

> **A Hybrid Neuro-Symbolic BDI Multi-Agent Architecture for LLM-Based Unit Test Generation**

A JaCaMo/Jason multi-agent system that autonomously generates JUnit 5 test suites for Java classes using LLM-guided input generation, JaCoCo coverage analysis, and reflective retry strategies.

---

## Architecture

The system consists of four collaborative BDI agents:

| Agent            | Role                                                                                     |
| ---------------- | ---------------------------------------------------------------------------------------- |
| **Analyzer**     | Reads source code, extracts logic paths via JavaParser, generates test inputs using GPT  |
| **Orchestrator** | Tracks coverage targets, detects HIT/MISS, manages reflection budget                     |
| **Generator**    | Performs semantic analysis of the source code and generates the final JUnit 5 test class |
| **Executor**     | Compiles and runs generated tests in-memory via JUnit Platform Launcher                  |

```
Analyzer ──► Orchestrator ──► Analyzer (retry loop)
    │                               │
    └──────────────────────────────►┘
                   │
              Generator
                   │
              Executor
```

---

## Requirements

- Java 21 (e.g. [Eclipse Temurin 21](https://adoptium.net/))
- Gradle (wrapper included)
- OpenAI API key

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/Venuin/llm-bdi-testgen.git
cd llm-bdi-testgen
```

### 2. Set your OpenAI API key

**Windows (cmd):**

```cmd
set OPENAI_API_KEY=sk-...
```

**Windows (PowerShell):**

```powershell
$env:OPENAI_API_KEY="sk-..."
```

**Linux / macOS:**

```bash
export OPENAI_API_KEY=sk-...
```

### 3. Configure the target class

Edit `src/agt/analyzer.asl` and change the class name passed to `readSourceCode`:

```jason
readSourceCode("YourClassName", OkunanKod);
```

The target `.java` file must be placed under `src/main/java/app/`.

### 4. Run

```cmd
.\gradlew run
```

---

## Configuration

| Parameter              | Location           | Default       | Description                                      |
| ---------------------- | ------------------ | ------------- | ------------------------------------------------ |
| `max_reflection_steps` | `orchestrator.asl` | `100`         | Global reflection budget (total LLM retry calls) |
| Miss limit per path    | `orchestrator.asl` | `5`           | Max failed attempts before a path is blocked     |
| LLM model              | `LLMTool.java`     | `gpt-4o-mini` | OpenAI model to use                              |

---

## Output

- **Console** — per-test PASS/FAIL results with coverage statistics
- **`log/`** — MAS execution logs (git-ignored)

---

## Project Structure

```
src/
├── agt/                  # BDI agent programs (.asl)
│   ├── analyzer.asl
│   ├── orchestrator.asl
│   ├── generator.asl
│   └── executor.asl
├── main/java/
│   ├── app/              # Target Java classes under test
│   └── tools/            # CArtAgO artifacts (LLM, JaCoCo, JUnit runner, etc.)
└── org/                  # JaCaMo organisation
main.jcm                  # MAS configuration entry point
build.gradle
```

---

## License

MIT
