package tools;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

import javax.tools.*;

import org.jacoco.core.analysis.*;
import org.jacoco.core.data.*;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Paper Benchmark Runner
 * 
 * Comprehensive benchmark tool for evaluating the baseline LLM approach
 * (zero-shot + reflection) against all benchmark subjects.
 * Produces paper-ready LaTeX tables and detailed comparison reports.
 * 
 * This runner evaluates the "Baseline LLM" approach:
 *   - Zero-shot: Single prompt asking for tests
 *   - Reflection: Iterative feedback with coverage/error information
 * 
 * The Multi-Agent System (MAS) results are collected separately via JaCaMo runs.
 * 
 * Usage: gradle runPaperBenchmark
 */
public class PaperBenchmarkRunner {

    // ── Configuration ──────────────────────────────────────────────────────────
    // Set the OPENAI_API_KEY environment variable before running.
    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL_NAME = "gpt-4o-mini";
    private static final int MAX_REFLECTION_STEPS = 5;
    private static final String SOURCE_DIR = "src/main/java/app/";

    // ── Benchmark Subject Definitions ──────────────────────────────────────────
    // Each entry: {filename, category, description}
    private static final String[][] BENCHMARK_SUBJECTS = {
        // Simple (S)
        {"MaxFinder.java",           "S",  "Three-way maximum finder"},
        {"Blackjack.java",           "S",  "Card game threshold logic"},
        {"ComplexExample.java",      "S",  "Nested branches with derived variables"},

        // Medium (M)
        {"LoopTestExample.java",     "M",  "For/while loops with conditions"},
        {"BuggyCalculator.java",     "M",  "Multi-path evaluation with edge cases"},
        {"Hesaplama.java",           "M",  "Rounding with negative number handling"},
        {"TriangleClassifier.java",  "M",  "Triangle classification (multi-condition)"},
        {"LeapYearChecker.java",     "M",  "Calendar algorithm (modular arithmetic)"},

        // Hard (H)
        {"LoopExample.java",         "H",  "Loop with break and derived variables"},
        {"VotingEligibility.java",   "H",  "Multi-criteria sequential elimination"},
        {"BankAccount.java",         "H",  "Banking with overdraft and tiered fees"},
        {"ArrayStatAnalyzer.java",   "H",  "Stats with for/while loops and nested sort"},
        {"PayrollCalculator.java",   "H",  "Object-based payroll with field branching"},

        // Very Hard (VH)
        {"InsurancePremiumCalculator.java", "VH", "Multi-factor premium with interactions"},
        {"OrderProcessor.java",            "VH", "E-commerce pricing engine (7 params)"},
        {"StudentGradeAnalyzer.java",      "VH", "Academic grading with arrays (8 params)"},
    };

    // ── Main Entry Point ───────────────────────────────────────────────────────
    public static void main(String[] args) {
        System.out.println("=".repeat(90));
        System.out.println("  PAPER BENCHMARK: Baseline LLM Evaluation");
        System.out.println("  Model: " + MODEL_NAME + " | Reflection Steps: " + MAX_REFLECTION_STEPS);
        System.out.println("  Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("=".repeat(90));

        List<BenchmarkResult> results = new ArrayList<>();
        int total = BENCHMARK_SUBJECTS.length;

        for (int idx = 0; idx < total; idx++) {
            String fileName = BENCHMARK_SUBJECTS[idx][0];
            String category = BENCHMARK_SUBJECTS[idx][1];
            String description = BENCHMARK_SUBJECTS[idx][2];

            System.out.println("\n" + "─".repeat(90));
            System.out.printf("[%d/%d] %s (%s) - %s%n", idx + 1, total, fileName, category, description);
            System.out.println("─".repeat(90));

            File file = new File(SOURCE_DIR + fileName);
            if (!file.exists()) {
                System.err.println("  SKIP: File not found - " + file.getPath());
                continue;
            }

            try {
                String sourceCode = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                BenchmarkResult result = runFullBenchmark(fileName, category, description, sourceCode);
                results.add(result);
                printStepResult(result);
            } catch (Exception e) {
                System.err.println("  ERROR: " + e.getMessage());
                e.printStackTrace();

                BenchmarkResult errResult = new BenchmarkResult();
                errResult.fileName = fileName;
                errResult.category = category;
                errResult.description = description;
                errResult.errorMessage = e.getMessage();
                results.add(errResult);
            }
        }

        // ── Output ─────────────────────────────────────────────────────────────
        System.out.println("\n\n");
        printSummaryTable(results);
        printLatexTable(results);
        saveFullReport(results);
    }

    // ── Core Benchmark Logic ───────────────────────────────────────────────────

    /**
     * Run the complete baseline benchmark for a single subject:
     * zero-shot + up to MAX_REFLECTION_STEPS reflection iterations.
     */
    private static BenchmarkResult runFullBenchmark(String fileName, String category,
                                                     String description, String sourceCode) {
        BenchmarkResult result = new BenchmarkResult();
        result.fileName = fileName;
        result.category = category;
        result.description = description;
        result.sourceLines = countSourceLines(sourceCode);
        result.branchCount = countBranches(sourceCode);

        JsonArray messageHistory = new JsonArray();
        addMessage(messageHistory, "user", createZeroShotPrompt(sourceCode));

        long startTime = System.currentTimeMillis();

        for (int step = 1; step <= MAX_REFLECTION_STEPS; step++) {
            System.out.printf("  Step #%d ... ", step);
            try {
                String llmResponse = askLLM(messageHistory);
                addMessage(messageHistory, "assistant", llmResponse);

                String testCode = extractTestCode(llmResponse);
                CoverageResult coverage = runTestWithCoverage(sourceCode, testCode);

                System.out.printf("Coverage: %.1f%% (%d/%d) | Tests: %d/%d%n",
                        coverage.coverage, coverage.coveredLines, coverage.totalLines,
                        coverage.passedTests, coverage.totalTests);

                // Record step-by-step progress
                StepRecord rec = new StepRecord();
                rec.stepNumber = step;
                rec.lineCoverage = coverage.coverage;
                rec.coveredLines = coverage.coveredLines;
                rec.totalLines = coverage.totalLines;
                rec.passedTests = coverage.passedTests;
                rec.totalTests = coverage.totalTests;
                rec.failedTests = coverage.failedTests;
                rec.errorLog = coverage.errorLog;
                rec.testCode = testCode;
                rec.timeMs = System.currentTimeMillis() - startTime;
                result.steps.add(rec);

                // Update best result
                if (coverage.coverage > result.bestCoverage) {
                    result.bestCoverage = coverage.coverage;
                    result.bestCoveredLines = coverage.coveredLines;
                    result.bestTotalLines = coverage.totalLines;
                    result.bestStep = step;
                    result.bestTestCode = testCode;
                }

                // Record zero-shot result
                if (step == 1) {
                    result.zeroShotCoverage = coverage.coverage;
                    result.zeroShotCoveredLines = coverage.coveredLines;
                    result.zeroShotTotalLines = coverage.totalLines;
                    result.zeroShotPassedTests = coverage.passedTests;
                    result.zeroShotTotalTests = coverage.totalTests;
                }

                // Early termination on full coverage
                if (coverage.coverage >= 100.0) {
                    System.out.println("  >>> 100% coverage reached!");
                    break;
                }

                // Provide reflection feedback for next iteration
                if (step < MAX_REFLECTION_STEPS) {
                    String feedback = buildReflectionFeedback(coverage, step);
                    addMessage(messageHistory, "user", feedback);
                }

            } catch (Exception e) {
                System.out.printf("ERROR: %s%n", e.getMessage());
                StepRecord errRec = new StepRecord();
                errRec.stepNumber = step;
                errRec.errorLog = e.getMessage();
                result.steps.add(errRec);
                break;
            }
        }

        result.totalTimeMs = System.currentTimeMillis() - startTime;
        return result;
    }

    // ── Prompt Engineering ─────────────────────────────────────────────────────

    private static String createZeroShotPrompt(String sourceCode) {
        return "You are an expert Java test engineer. Write a JUnit 5 test class that achieves " +
               "100% LINE COVERAGE for the following Java class.\n\n" +
               "RULES:\n" +
               "- Use ONLY JUnit 5 annotations (@Test, @BeforeEach, etc.)\n" +
               "- Import from org.junit.jupiter.api.* and org.junit.jupiter.api.Assertions.*\n" +
               "- The test class name MUST end with 'Test' (e.g., MyClassTest)\n" +
               "- Test class must be in package 'app'\n" +
               "- Cover ALL branches: if/else, loops (enter AND skip), exceptions\n" +
               "- Use assertThrows for methods that throw exceptions\n" +
               "- Return ONLY the Java code, no explanations\n\n" +
               "```java\n" + sourceCode + "\n```";
    }

    private static String buildReflectionFeedback(CoverageResult coverage, int step) {
        StringBuilder fb = new StringBuilder();
        fb.append("The test code has issues. Please fix and improve it.\n\n");
        fb.append("CURRENT COVERAGE: ").append(String.format("%.1f%%", coverage.coverage));
        fb.append(" (").append(coverage.coveredLines).append("/").append(coverage.totalLines).append(" lines)\n");
        fb.append("PASSED TESTS: ").append(coverage.passedTests).append("/").append(coverage.totalTests).append("\n");

        if (coverage.failedTests > 0 && coverage.errorLog != null && !coverage.errorLog.isEmpty()) {
            fb.append("\nERRORS:\n").append(coverage.errorLog).append("\n");
        }

        fb.append("\nFix any failing tests and add more test methods to cover the remaining ");
        fb.append(coverage.totalLines - coverage.coveredLines).append(" uncovered lines.\n");
        fb.append("Return ONLY the complete Java test class code.");

        return fb.toString();
    }

    // ── Code Analysis Utilities ────────────────────────────────────────────────

    private static int countSourceLines(String sourceCode) {
        int count = 0;
        for (String line : sourceCode.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
                    && !trimmed.startsWith("*") && !trimmed.equals("}") && !trimmed.equals("{")) {
                count++;
            }
        }
        return count;
    }

    private static int countBranches(String sourceCode) {
        int branches = 0;
        // Count if, else if, else, while, for, switch/case, try/catch
        Matcher m = Pattern.compile("\\b(if|else\\s+if|else|while|for|switch|case|catch|throw)\\b").matcher(sourceCode);
        while (m.find()) branches++;
        return branches;
    }

    // ── Output Formatting ──────────────────────────────────────────────────────

    private static void printStepResult(BenchmarkResult r) {
        System.out.printf("  RESULT: Zero-shot=%.1f%% | Best=%.1f%% (step %d) | Time=%dms%n",
                r.zeroShotCoverage, r.bestCoverage, r.bestStep, r.totalTimeMs);
    }

    private static void printSummaryTable(List<BenchmarkResult> results) {
        System.out.println("=".repeat(110));
        System.out.println("  SUMMARY TABLE: Baseline LLM Performance (" + MODEL_NAME + ")");
        System.out.println("=".repeat(110));
        System.out.printf("%-32s %4s %5s %5s %12s %12s %5s %8s%n",
                "Subject", "Cat", "LOC", "Br", "Zero-Shot", "Best(+Refl)", "Step", "Time(s)");
        System.out.println("-".repeat(110));

        double totalZeroShot = 0, totalBest = 0;
        int count = 0;

        for (BenchmarkResult r : results) {
            if (r.errorMessage != null && r.bestCoverage == 0) {
                System.out.printf("%-32s %4s %5d %5d %12s %12s %5s %8s%n",
                        r.fileName, r.category, r.sourceLines, r.branchCount,
                        "ERROR", "ERROR", "-", "-");
                continue;
            }

            System.out.printf("%-32s %4s %5d %5d %10.1f%% %10.1f%% %5d %7.1fs%n",
                    r.fileName, r.category, r.sourceLines, r.branchCount,
                    r.zeroShotCoverage, r.bestCoverage, r.bestStep, r.totalTimeMs / 1000.0);

            totalZeroShot += r.zeroShotCoverage;
            totalBest += r.bestCoverage;
            count++;
        }

        System.out.println("-".repeat(110));
        if (count > 0) {
            System.out.printf("%-32s %4s %5s %5s %10.1f%% %10.1f%%%n",
                    "AVERAGE", "", "", "", totalZeroShot / count, totalBest / count);
        }
        System.out.println("=".repeat(110));
    }

    private static void printLatexTable(List<BenchmarkResult> results) {
        System.out.println("\n% ── LaTeX Table (copy-paste into paper) ──");
        System.out.println("\\begin{table}[htbp]");
        System.out.println("\\centering");
        System.out.println("\\caption{Baseline LLM (" + MODEL_NAME + ") coverage results with zero-shot and reflection.}");
        System.out.println("\\label{tab:baseline_results}");
        System.out.println("\\begin{tabular}{llrrrrr}");
        System.out.println("\\toprule");
        System.out.println("Subject & Cat. & LOC & Br. & Zero-Shot (\\%) & +Reflect (\\%) & Steps \\\\");
        System.out.println("\\midrule");

        String lastCategory = "";
        double totalZS = 0, totalBest = 0;
        int count = 0;

        for (BenchmarkResult r : results) {
            if (!r.category.equals(lastCategory) && !lastCategory.isEmpty()) {
                System.out.println("\\addlinespace");
            }
            lastCategory = r.category;

            String name = r.fileName.replace(".java", "").replace("_", "\\_");
            if (r.errorMessage != null && r.bestCoverage == 0) {
                System.out.printf("%s & %s & %d & %d & \\multicolumn{2}{c}{Error} & -- \\\\%n",
                        name, r.category, r.sourceLines, r.branchCount);
            } else {
                System.out.printf("%s & %s & %d & %d & %.1f & %.1f & %d \\\\%n",
                        name, r.category, r.sourceLines, r.branchCount,
                        r.zeroShotCoverage, r.bestCoverage, r.bestStep);
                totalZS += r.zeroShotCoverage;
                totalBest += r.bestCoverage;
                count++;
            }
        }

        System.out.println("\\midrule");
        if (count > 0) {
            System.out.printf("\\textbf{Average} & & & & \\textbf{%.1f} & \\textbf{%.1f} & \\\\%n",
                    totalZS / count, totalBest / count);
        }
        System.out.println("\\bottomrule");
        System.out.println("\\end{tabular}");
        System.out.println("\\end{table}");

        // Also print a LaTeX table for comparing with MAS (placeholder)
        System.out.println("\n% ── Comparison Table Template (fill MAS results manually) ──");
        System.out.println("\\begin{table}[htbp]");
        System.out.println("\\centering");
        System.out.println("\\caption{Coverage comparison: Baseline LLM vs.\\ Multi-Agent System.}");
        System.out.println("\\label{tab:comparison}");
        System.out.println("\\begin{tabular}{llrrrr}");
        System.out.println("\\toprule");
        System.out.println("Subject & Cat. & Baseline (\\%) & MAS (\\%) & $\\Delta$ (\\%) & MAS Iter. \\\\");
        System.out.println("\\midrule");

        for (BenchmarkResult r : results) {
            String name = r.fileName.replace(".java", "").replace("_", "\\_");
            if (!r.category.equals(lastCategory) && !lastCategory.isEmpty()) {
                System.out.println("\\addlinespace");
            }
            System.out.printf("%s & %s & %.1f & \\textit{--} & \\textit{--} & \\textit{--} \\\\%n",
                    name, r.category, r.bestCoverage);
        }

        System.out.println("\\bottomrule");
        System.out.println("\\end{tabular}");
        System.out.println("\\end{table}");
    }

    // ── Report Persistence ─────────────────────────────────────────────────────

    private static void saveFullReport(List<BenchmarkResult> results) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "paper_benchmark_" + ts + ".txt";
            StringBuilder sb = new StringBuilder();

            sb.append("=".repeat(100)).append("\n");
            sb.append("PAPER BENCHMARK REPORT\n");
            sb.append("Model: ").append(MODEL_NAME).append("\n");
            sb.append("Max Reflection Steps: ").append(MAX_REFLECTION_STEPS).append("\n");
            sb.append("Date: ").append(LocalDateTime.now()).append("\n");
            sb.append("=".repeat(100)).append("\n\n");

            // Summary section
            sb.append("════════════════════ SUMMARY ════════════════════\n\n");
            sb.append(String.format("%-32s %4s %5s %5s %12s %12s %5s %8s%n",
                    "Subject", "Cat", "LOC", "Br", "Zero-Shot", "Best", "Step", "Time"));
            sb.append("-".repeat(100)).append("\n");

            for (BenchmarkResult r : results) {
                if (r.errorMessage != null && r.bestCoverage == 0) {
                    sb.append(String.format("%-32s %4s %5d %5d %12s %12s %5s %8s%n",
                            r.fileName, r.category, r.sourceLines, r.branchCount,
                            "ERROR", "ERROR", "-", "-"));
                } else {
                    sb.append(String.format("%-32s %4s %5d %5d %10.1f%% %10.1f%% %5d %7.1fs%n",
                            r.fileName, r.category, r.sourceLines, r.branchCount,
                            r.zeroShotCoverage, r.bestCoverage, r.bestStep, r.totalTimeMs / 1000.0));
                }
            }

            sb.append("\n\n");

            // Detailed results per subject
            sb.append("════════════════════ DETAILED RESULTS ════════════════════\n\n");
            for (BenchmarkResult r : results) {
                sb.append("━".repeat(100)).append("\n");
                sb.append("FILE: ").append(r.fileName).append(" (").append(r.category).append(")\n");
                sb.append("DESC: ").append(r.description).append("\n");
                sb.append("LOC: ").append(r.sourceLines).append(" | Branches: ").append(r.branchCount).append("\n");
                sb.append("Zero-Shot Coverage: ").append(String.format("%.1f%%", r.zeroShotCoverage)).append("\n");
                sb.append("Best Coverage: ").append(String.format("%.1f%%", r.bestCoverage));
                sb.append(" (at step ").append(r.bestStep).append(")\n");
                sb.append("Total Time: ").append(String.format("%.1fs", r.totalTimeMs / 1000.0)).append("\n");

                if (r.errorMessage != null) {
                    sb.append("ERROR: ").append(r.errorMessage).append("\n");
                }

                // Step-by-step progression
                sb.append("\nStep-by-Step Progression:\n");
                for (StepRecord step : r.steps) {
                    sb.append(String.format("  Step %d: %.1f%% (%d/%d) | Tests: %d passed, %d failed | %dms%n",
                            step.stepNumber, step.lineCoverage, step.coveredLines, step.totalLines,
                            step.passedTests, step.failedTests, step.timeMs));
                    if (step.errorLog != null && !step.errorLog.isEmpty()) {
                        sb.append("    Errors: ").append(step.errorLog.replace("\n", "\n    ")).append("\n");
                    }
                }

                // Best test code
                if (r.bestTestCode != null) {
                    sb.append("\nBest Test Code (Step ").append(r.bestStep).append("):\n");
                    sb.append("```java\n").append(r.bestTestCode).append("\n```\n");
                }

                sb.append("\n");
            }

            Files.write(Paths.get(filename), sb.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("\nReport saved: " + filename);
        } catch (IOException e) {
            System.err.println("Failed to save report: " + e.getMessage());
        }
    }

    // ── LLM Communication ──────────────────────────────────────────────────────

    private static String askLLM(JsonArray messages) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL_NAME);
        body.add("messages", messages);
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", 4096);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .timeout(java.time.Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            throw new RuntimeException("OpenAI API Error: " + resp.statusCode() + " - " + resp.body());
        }

        return JsonParser.parseString(resp.body()).getAsJsonObject()
                .getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }

    private static void addMessage(JsonArray history, String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        history.add(msg);
    }

    // ── Compilation & Coverage ─────────────────────────────────────────────────

    private static CoverageResult runTestWithCoverage(String sourceCode, String testCode) throws Exception {
        String fullClassName = extractFullClassName(sourceCode);
        String testClassName = extractFullClassName(testCode);

        if (fullClassName.equals(testClassName)) {
            throw new IllegalArgumentException(
                "Test class name must differ from source class: " + testClassName);
        }

        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(fullClassName, sourceCode);
        sources.put(testClassName, testCode);
        Map<String, byte[]> compiled = compileMultipleFiles(sources);

        if (compiled.get(fullClassName) == null) {
            throw new RuntimeException("Source class compilation failed: " + fullClassName);
        }
        if (compiled.get(testClassName) == null) {
            throw new RuntimeException("Test class compilation failed: " + testClassName);
        }

        IRuntime runtime = new LoggerRuntime();
        Instrumenter instr = new Instrumenter(runtime);
        byte[] instrBytes = instr.instrument(compiled.get(fullClassName), fullClassName);

        RuntimeData data = new RuntimeData();
        runtime.startup(data);

        MemoryClassLoader loader = new MemoryClassLoader(PaperBenchmarkRunner.class.getClassLoader());
        loader.addDefinition(fullClassName, instrBytes);
        loader.addDefinition(testClassName, compiled.get(testClassName));

        CoverageResult res = new CoverageResult();
        StringBuilder log = new StringBuilder();

        try {
            Class<?> testClazz = loader.loadClass(testClassName);
            var constructor = testClazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object testInstance = constructor.newInstance();

            for (var method : testClazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
                    res.totalTests++;
                    try {
                        method.setAccessible(true);
                        method.invoke(testInstance);
                        res.passedTests++;
                    } catch (Exception e) {
                        res.failedTests++;
                        Throwable cause = (e.getCause() != null) ? e.getCause() : e;
                        String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                        log.append("- ").append(method.getName()).append(": ").append(msg).append("\n");
                    }
                }
            }
        } finally {
            ExecutionDataStore exec = new ExecutionDataStore();
            data.collect(exec, new SessionInfoStore(), false);
            runtime.shutdown();

            CoverageBuilder cb = new CoverageBuilder();
            new Analyzer(exec, cb).analyzeClass(compiled.get(fullClassName), fullClassName);
            for (IClassCoverage cc : cb.getClasses()) {
                res.totalLines = cc.getLineCounter().getTotalCount();
                res.coveredLines = cc.getLineCounter().getCoveredCount();
                res.coverage = res.totalLines > 0 ? res.coveredLines * 100.0 / res.totalLines : 0;
            }
            res.errorLog = log.toString();
        }

        return res;
    }

    private static Map<String, byte[]> compileMultipleFiles(Map<String, String> sources) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("Java compiler not available. Run with JDK, not JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        MemoryJavaFileManager manager = new MemoryJavaFileManager(
                compiler.getStandardFileManager(diagnostics, null, null));

        List<JavaFileObject> files = new ArrayList<>();
        sources.forEach((name, src) -> files.add(
                new SimpleJavaFileObject(
                        URI.create("string:///" + name.replace('.', '/') + ".java"),
                        JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean b) { return src; }
                }));

        boolean success = compiler.getTask(null, manager, diagnostics,
                Arrays.asList("-classpath", System.getProperty("java.class.path")),
                null, files).call();

        if (!success) {
            StringBuilder errors = new StringBuilder("Compilation failed:\n");
            for (var d : diagnostics.getDiagnostics()) {
                errors.append("  ").append(d.getMessage(null)).append("\n");
            }
            throw new RuntimeException(errors.toString());
        }

        Map<String, byte[]> result = new LinkedHashMap<>();
        sources.keySet().forEach(n -> result.put(n, manager.getClassBytes(n)));
        return result;
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private static String extractTestCode(String response) {
        String code = response;

        if (response.contains("```java")) {
            String[] parts = response.split("```java");
            String selectedBlock = null;

            for (int i = 1; i < parts.length; i++) {
                String block = parts[i].split("```")[0];
                if (block.contains("@Test") || block.matches("(?s).*class\\s+\\w*Test\\s*\\{.*")) {
                    selectedBlock = block;
                    break;
                }
            }

            if (selectedBlock == null && parts.length > 1) {
                selectedBlock = parts[parts.length - 1].split("```")[0];
            }

            code = selectedBlock != null ? selectedBlock : code;
        } else if (response.contains("```")) {
            String[] blocks = response.split("```");
            if (blocks.length >= 2) {
                code = blocks[1];
                if (code.startsWith("java\n")) code = code.substring(5);
            }
        }

        code = code.trim();

        // JUnit 4 → JUnit 5 conversion
        code = code.replace("import org.junit.Test;", "import org.junit.jupiter.api.Test;");
        code = code.replace("import static org.junit.Assert.", "import static org.junit.jupiter.api.Assertions.");
        code = code.replace("import org.junit.Before;", "import org.junit.jupiter.api.BeforeEach;");
        code = code.replace("import org.junit.After;", "import org.junit.jupiter.api.AfterEach;");
        code = code.replace("@Before\n", "@BeforeEach\n");
        code = code.replace("@After\n", "@AfterEach\n");

        // Ensure class is public with 'Test' suffix
        Matcher classMatcher = Pattern.compile("(?m)^(\\s*)(?:public\\s+)?class\\s+(\\w+)").matcher(code);
        if (classMatcher.find()) {
            String indent = classMatcher.group(1);
            String className = classMatcher.group(2);
            if (!className.endsWith("Test")) {
                String newClassName = className + "Test";
                code = code.replaceAll("(?m)^(\\s*)(?:public\\s+)?class\\s+" + className + "\\b",
                        indent + "public class " + newClassName);
            } else {
                code = code.replaceAll("(?m)^(\\s*)(?:public\\s+)?class\\s+" + className + "\\b",
                        indent + "public class " + className);
            }
        }

        // Ensure package declaration
        if (!code.contains("package ")) {
            code = "package app;\n\n" + code;
        }

        return code;
    }

    private static String extractFullClassName(String sourceCode) {
        Matcher m = Pattern.compile("class\\s+(\\w+)").matcher(sourceCode);
        String name = m.find() ? m.group(1) : "Unknown";
        return sourceCode.contains("package app") ? "app." + name : name;
    }

    // ── Data Classes ───────────────────────────────────────────────────────────

    static class BenchmarkResult {
        String fileName;
        String category;
        String description;
        int sourceLines;
        int branchCount;

        // Zero-shot (step 1) results
        double zeroShotCoverage;
        int zeroShotCoveredLines;
        int zeroShotTotalLines;
        int zeroShotPassedTests;
        int zeroShotTotalTests;

        // Best result across all reflection steps
        double bestCoverage;
        int bestCoveredLines;
        int bestTotalLines;
        int bestStep;
        String bestTestCode;

        long totalTimeMs;
        String errorMessage;

        List<StepRecord> steps = new ArrayList<>();
    }

    static class StepRecord {
        int stepNumber;
        double lineCoverage;
        int coveredLines;
        int totalLines;
        int passedTests;
        int totalTests;
        int failedTests;
        String errorLog;
        String testCode;
        long timeMs;
    }

    static class CoverageResult {
        double coverage;
        int coveredLines;
        int totalLines;
        int totalTests;
        int passedTests;
        int failedTests;
        String errorLog;
    }

    // ── In-Memory Compilation Infrastructure ───────────────────────────────────

    static class MemoryClassLoader extends ClassLoader {
        private final Map<String, byte[]> definitions = new HashMap<>();

        public MemoryClassLoader(ClassLoader parent) { super(parent); }

        public void addDefinition(String name, byte[] bytes) { definitions.put(name, bytes); }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (definitions.containsKey(name)) return findClass(name);
            return super.loadClass(name);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = definitions.get(name);
            return bytes != null ? defineClass(name, bytes, 0, bytes.length) : super.findClass(name);
        }
    }

    static class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteArrayOutputStream> classBytes = new HashMap<>();

        public MemoryJavaFileManager(StandardJavaFileManager manager) { super(manager); }

        public byte[] getClassBytes(String name) {
            return classBytes.containsKey(name) ? classBytes.get(name).toByteArray() : null;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                    JavaFileObject.Kind kind, FileObject sibling) {
            return new SimpleJavaFileObject(
                    URI.create("mem:///" + className + kind.extension), kind) {
                @Override
                public OutputStream openOutputStream() {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    classBytes.put(className, os);
                    return os;
                }
            };
        }
    }
}
