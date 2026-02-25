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

import javax.tools.*;

import org.jacoco.core.analysis.*;
import org.jacoco.core.data.*;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class SimpleLLMBenchmark {

    // Set the OPENAI_API_KEY environment variable before running.
    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
   
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final int MAX_REFLECTION_STEPS = 2; 

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("LLM BENCHMARK: STEP-BY-STEP PROGRESS REPORT");
        System.out.println("=".repeat(80));

        String[] testFiles = {
            // "src/main/java/app/Hesaplama.java",
            // "src/main/java/app/BuggyCalculator.java",
            // "src/main/java/app/MaxFinder.java",
            // "src/main/java/app/ComplexExample.java",
            // "src/main/java/app/LoopExample.java",
            // "src/main/java/app/Blackjack.java",
            // "src/main/java/app/OrderProcessor.java",
            //"src/main/java/app/LoopExample.java",
            "src/main/java/app/BankAccount.java"
        };

        List<BenchmarkResult> results = new ArrayList<>();

        for (String filePath : testFiles) {
            File file = new File(filePath);
            if (!file.exists()) continue;

            try {
                System.out.println("\n📄 Dosya: " + file.getName());
                String sourceCode = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                BenchmarkResult result = runBenchmarkWithReflection(file.getName(), sourceCode);
                results.add(result);
            } catch (Exception e) {
                System.err.println("❌ Hata: " + e.getMessage());
            }
        }

        saveDetailedResults(results);
        printSummary(results);
    }

    private static BenchmarkResult runBenchmarkWithReflection(String fileName, String sourceCode) {
        BenchmarkResult res = new BenchmarkResult();
        res.fileName = fileName;
        
        JsonArray messageHistory = new JsonArray();
        addMessage(messageHistory, "user", createSimplePrompt(sourceCode));

        long startTime = System.currentTimeMillis();

        for (int step = 1; step <= MAX_REFLECTION_STEPS; step++) {
            System.out.println("🤖 Adım #" + step + " çalışıyor...");
            try {
                String llmResponse = askLLM(messageHistory);
                System.out.println("✅ LLM yanıtı alındı");
                addMessage(messageHistory, "assistant", llmResponse);
                
                String testCode = extractTestCode(llmResponse);
                CoverageResult coverage = runTestWithCoverage(sourceCode, testCode);
                
                System.out.println("✅ Coverage: " + String.format("%.2f%%", coverage.coverage) + 
                    " (" + coverage.coveredLines + "/" + coverage.totalLines + ")");
                System.out.println("   Test Başarısı: " + coverage.passedTests + "/" + coverage.totalTests);
                
                // Adım bazlı verileri kaydet
                if (step == 1) {
                    res.firstShotResponse = llmResponse;
                    res.firstShotStats = coverage.passedTests + "/" + coverage.totalTests;
                } else {
                    res.reflectionResponse = llmResponse;
                    res.reflectionStats = coverage.passedTests + "/" + coverage.totalTests;
                }

                res.lineCoverage = coverage.coverage;
                res.coveredLines = coverage.coveredLines;
                res.totalLines = coverage.totalLines;
                res.passedTests = coverage.passedTests;
                res.totalTests = coverage.totalTests;
                res.generatedTestCode = testCode;
                res.finalErrorLog = coverage.errorLog;
                res.success = (coverage.failedTests == 0 && coverage.totalTests > 0);

                if (res.success && coverage.coverage >= 99.0) break;

                if (step < MAX_REFLECTION_STEPS) {
                    String feedback = "Hataları düzelt:\n" + coverage.errorLog + "\nCoverage: %" + coverage.coverage;
                    addMessage(messageHistory, "user", feedback);
                }
            } catch (Exception e) {
                System.err.println("❌ Hata (Adım #" + step + "): " + e.getMessage());
                e.printStackTrace();
                
                String errorDetail = "HATA: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                
                if (step == 1) {
                    res.firstShotStats = "HATA";
                    res.firstShotResponse = errorDetail;
                } else {
                    res.reflectionStats = "HATA";
                    res.reflectionResponse = errorDetail;
                }
                res.errorMessage = e.getMessage();
                break; // Hata olursa devam etme
            }
        }

        res.totalTime = System.currentTimeMillis() - startTime;
        return res;
    }

    private static void saveDetailedResults(List<BenchmarkResult> results) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "benchmark_comparison_" + ts + ".txt";
            StringBuilder sb = new StringBuilder();

            for (BenchmarkResult r : results) {
                sb.append("=".repeat(100)).append("\n");
                sb.append("DOSYA: ").append(r.fileName).append("\n");
                sb.append("İLK DENEME BAŞARI : ").append(r.firstShotStats).append("\n");
                sb.append("REFLECT DENEME BAŞARI: ").append(r.reflectionStats != null ? r.reflectionStats : "Gerekmedi").append("\n");
                sb.append("NİHAİ COVERAGE : %").append(String.format("%.2f", r.lineCoverage));
                sb.append(" (").append(r.coveredLines).append("/").append(r.totalLines).append(")\n");
                sb.append("=".repeat(100)).append("\n\n");

                sb.append("--- [1] İLK LLM YANITI ---\n").append(r.firstShotResponse).append("\n\n");
                
                if (r.reflectionResponse != null) {
                    sb.append("--- [2] REFLECT SONRASI YANIT ---\n").append(r.reflectionResponse).append("\n\n");
                }

                sb.append("--- NİHAİ TEST KODU ---\n").append(r.generatedTestCode).append("\n\n");
            }

            Files.write(Paths.get(filename), sb.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("\n📝 Rapor oluşturuldu: " + filename);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static CoverageResult runTestWithCoverage(String sourceCode, String testCode) throws Exception {
        String fullClassName = extractFullClassName(sourceCode);
        String testClassName = extractFullClassName(testCode);
        
        // Test sınıfı adı kaynak sınıfla aynıysa hata ver
        if (fullClassName.equals(testClassName)) {
            throw new IllegalArgumentException("Test sınıfı adı kaynak sınıfla aynı olamaz: " + testClassName + 
                ". LLM test sınıfını doğru adlandırmadı (örn: OrderProcessorTest olmalı).");
        }

        Map<String, String> sources = new HashMap<>();
        sources.put(fullClassName, sourceCode);
        sources.put(testClassName, testCode);
        Map<String, byte[]> compiled = compileMultipleFiles(sources);
        
        IRuntime runtime = new LoggerRuntime();
        Instrumenter instr = new Instrumenter(runtime);
        byte[] instrBytes = instr.instrument(compiled.get(fullClassName), fullClassName);
        
        RuntimeData data = new RuntimeData();
        runtime.startup(data);

        MemoryClassLoader loader = new MemoryClassLoader(SimpleLLMBenchmark.class.getClassLoader());
        loader.addDefinition(fullClassName, instrBytes);
        loader.addDefinition(testClassName, compiled.get(testClassName));

        CoverageResult res = new CoverageResult();
        StringBuilder log = new StringBuilder();

        try {
            Class<?> tClazz = loader.loadClass(testClassName);
            var constructor = tClazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object tInst = constructor.newInstance();
            for (var m : tClazz.getDeclaredMethods()) {
                if (m.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
                    res.totalTests++;
                    try {
                        m.setAccessible(true);
                        m.invoke(tInst);
                        res.passedTests++;
                    } catch (Exception e) {
                        res.failedTests++;
                        Throwable cause = (e.getCause() != null) ? e.getCause() : e;
                        log.append("- ").append(m.getName()).append(": ").append(cause.getMessage()).append("\n");
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

    private static String askLLM(JsonArray messages) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", "gpt-4o-mini");
        body.add("messages", messages);
        body.addProperty("temperature", 0.1);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (resp.statusCode() != 200) {
            throw new RuntimeException("API Hatası: " + resp.statusCode() + " - " + resp.body());
        }
        
        return JsonParser.parseString(resp.body()).getAsJsonObject()
                .getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();
    }

    private static Map<String, byte[]> compileMultipleFiles(Map<String, String> sources) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        MemoryJavaFileManager manager = new MemoryJavaFileManager(compiler.getStandardFileManager(null, null, null));
        List<JavaFileObject> files = new ArrayList<>();
        sources.forEach((name, src) -> files.add(new SimpleJavaFileObject(URI.create("string:///" + name.replace('.','/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean b) { return src; }
        }));
        compiler.getTask(null, manager, null, Arrays.asList("-classpath", System.getProperty("java.class.path")), null, files).call();
        Map<String, byte[]> res = new HashMap<>();
        sources.keySet().forEach(n -> res.put(n, manager.getClassBytes(n)));
        return res;
    }

    private static String extractTestCode(String resp) {
        String code = resp;
        
        // Birden fazla ```java``` bloğu varsa, test sınıfını içeren bloğu bul
        if (resp.contains("```java")) {
            String[] parts = resp.split("```java");
            String selectedBlock = null;
            
            // Test ile biten sınıf veya @Test annotation içeren bloğu ara
            for (int i = 1; i < parts.length; i++) {
                String block = parts[i].split("```")[0];
                if (block.contains("@Test") || block.matches("(?s).*class\\s+\\w*Test\\s*\\{.*")) {
                    selectedBlock = block;
                    break;
                }
            }
            
            // Bulunamazsa son bloğu al
            if (selectedBlock == null && parts.length > 1) {
                selectedBlock = parts[parts.length - 1].split("```")[0];
            }
            
            code = selectedBlock != null ? selectedBlock : code;
        } else if (resp.contains("```")) {
            code = resp.split("```")[1].split("```")[0];
        }
        
        code = code.trim();
        
        // JUnit 4'ü JUnit 5'e çevir
        code = code.replace("import org.junit.Test;", "import org.junit.jupiter.api.Test;");
        code = code.replace("import static org.junit.Assert.", "import static org.junit.jupiter.api.Assertions.");
        code = code.replace("import org.junit.Before;", "import org.junit.jupiter.api.BeforeEach;");
        code = code.replace("import org.junit.After;", "import org.junit.jupiter.api.AfterEach;");
        code = code.replace("@Before", "@BeforeEach");
        code = code.replace("@After", "@AfterEach");
        
        // Test sınıfını public yap ve sınıf adını kontrol et
        Matcher classMatcher = Pattern.compile("(?m)^(\\s*)(?:public\\s+)?class\\s+(\\w+)").matcher(code);
        if (classMatcher.find()) {
            String indent = classMatcher.group(1);
            String className = classMatcher.group(2);
            
            // Eğer sınıf adı "Test" ile bitmiyorsa ekle
            if (!className.endsWith("Test")) {
                String newClassName = className + "Test";
                code = code.replaceAll("(?m)^(\\s*)(?:public\\s+)?class\\s+" + className + "\\b", 
                    indent + "public class " + newClassName);
            } else {
                code = code.replaceAll("(?m)^(\\s*)(?:public\\s+)?class\\s+" + className + "\\b", 
                    indent + "public class " + className);
            }
        }
        
        // Package bilgisi yoksa ekle
        if (!code.contains("package ")) {
            code = "package app;\n\n" + code;
        }
        
        return code;
    }

    private static String extractFullClassName(String sc) {
        Matcher m = Pattern.compile("class\\s+(\\w+)").matcher(sc);
        String n = m.find() ? m.group(1) : "Unknown";
        return sc.contains("package app") ? "app." + n : n;
    }

    private static String createSimplePrompt(String sc) {
        return "Write JUnit 5 tests for 100% coverage. If code has bugs, assert CORRECT logic (fail test). Return ONLY Java code. Code:\n" + sc;
    }

    private static void addMessage(JsonArray h, String r, String c) {
        JsonObject m = new JsonObject(); m.addProperty("role", r); m.addProperty("content", c); h.add(m);
    }

    private static void printSummary(List<BenchmarkResult> results) {
        System.out.println("\n📊 ÖZET:");
        results.forEach(r -> {
            System.out.printf("%-20s: First Shot: %s | Reflection: %s | Cov: %.2f%% (%d/%d)\n", 
                r.fileName, r.firstShotStats, (r.reflectionStats != null ? r.reflectionStats : "N/A"), 
                r.lineCoverage, r.coveredLines, r.totalLines);
        });
    }

    static class BenchmarkResult {
        String fileName; boolean success; double lineCoverage; int coveredLines, totalLines; int totalTests, passedTests;
        long totalTime; String firstShotResponse, reflectionResponse, generatedTestCode, errorMessage, finalErrorLog;
        String firstShotStats, reflectionStats;
    }

    static class CoverageResult {
        double coverage; int coveredLines, totalLines; int totalTests, passedTests, failedTests; String errorLog;
    }

    static class MemoryClassLoader extends ClassLoader {
        private final Map<String, byte[]> defs = new HashMap<>();
        public MemoryClassLoader(ClassLoader p) { super(p); }
        public void addDefinition(String n, byte[] b) { defs.put(n, b); }
        @Override public Class<?> loadClass(String n) throws ClassNotFoundException {
            if (defs.containsKey(n)) return findClass(n);
            return super.loadClass(n);
        }
        @Override protected Class<?> findClass(String n) throws ClassNotFoundException {
            byte[] b = defs.get(n);
            return b != null ? defineClass(n, b, 0, b.length) : super.findClass(n);
        }
    }

    static class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteArrayOutputStream> bytes = new HashMap<>();
        public MemoryJavaFileManager(StandardJavaFileManager m) { super(m); }
        public byte[] getClassBytes(String n) { return bytes.containsKey(n) ? bytes.get(n).toByteArray() : null; }
        @Override public JavaFileObject getJavaFileForOutput(Location l, String n, JavaFileObject.Kind k, FileObject s) {
            return new SimpleJavaFileObject(URI.create("mem:///" + n + k.extension), k) {
                @Override public OutputStream openOutputStream() {
                    ByteArrayOutputStream os = new ByteArrayOutputStream(); bytes.put(n, os); return os;
                }
            };
        }
    }
}