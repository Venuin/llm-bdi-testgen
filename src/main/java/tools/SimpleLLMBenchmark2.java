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
import java.util.concurrent.*;
import java.util.regex.*;

import javax.tools.*;

import org.jacoco.core.analysis.*;
import org.jacoco.core.data.*;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Basit LLM Benchmark Aracı
 * 
 * LLM'e sadece kodun kendisini ve maksimum line coverage yapacak testleri üretmesini söyleyen
 * basit bir prompt ile performansı test eder.
 * 
 * Kullanım: gradle runSimpleBenchmark
 */
public class SimpleLLMBenchmark2 {
    
    // Set the OPENAI_API_KEY environment variable before running.
    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    
    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("BASIT LLM BENCHMARK ARACI");
        System.out.println("=".repeat(80));
        
        // Test edilecek dosyaları bul
        String[] testFiles = {
            // "src/main/java/app/Hesaplama.java",
            // "src/main/java/app/BuggyCalculator.java",
            // "src/main/java/app/MaxFinder.java",
            // "src/main/java/app/ComplexExample.java",
            // "src/main/java/app/LoopExample.java",
            // "src/main/java/app/Blackjack.java",
            // "src/main/java/app/OrderProcessor.java",
            // "src/main/java/app/StudentGradeAnalyzer.java",
            "src/main/java/app/BankAccount.java"
        };
        
        List<BenchmarkResult> results = new ArrayList<>();
        
        for (String filePath : testFiles) {
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("⚠️  Dosya bulunamadı: " + filePath);
                continue;
            }
            
            try {
                System.out.println("\n" + "─".repeat(80));
                System.out.println("📄 Test ediliyor: " + file.getName());
                System.out.println("─".repeat(80));
                
                String sourceCode = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                BenchmarkResult result = runBenchmarkForFile(file.getName(), sourceCode);
                results.add(result);
                
                // Her testten sonra biraz bekle (rate limit için)
                Thread.sleep(2000);
                
            } catch (Exception e) {
                System.err.println("❌ Hata: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // Sonuçları kaydet
        saveResults(results);
        
        // Özet rapor
        printSummary(results);
    }
    
    private static BenchmarkResult runBenchmarkForFile(String fileName, String sourceCode) {
        BenchmarkResult result = new BenchmarkResult();
        result.fileName = fileName;
        result.timestamp = LocalDateTime.now();
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. LLM'e basit prompt gönder
            System.out.println("🤖 LLM'e test üretmesi için prompt gönderiliyor...");
            String prompt = createSimplePrompt(sourceCode);
            String llmResponse = askLLM(prompt);
            result.llmResponse = llmResponse;
            result.llmResponseTime = System.currentTimeMillis() - startTime;
            
            System.out.println("✅ LLM yanıtı alındı (" + result.llmResponseTime + "ms)");
            
            // 2. Test kodunu ayıkla
            String testCode = extractTestCode(llmResponse);
            if (testCode == null || testCode.isEmpty()) {
                result.errorMessage = "Test kodu çıkarılamadı";
                return result;
            }
            result.generatedTestCode = testCode;
            
            // 3. Testi çalıştır ve coverage ölç
            System.out.println("🧪 Testler çalıştırılıyor...");
            CoverageResult coverage = runTestWithCoverage(sourceCode, testCode);
            result.lineCoverage = coverage.coverage;
            result.coveredLines = coverage.coveredLines;
            result.totalLines = coverage.totalLines;
            result.totalTests = coverage.totalTests;
            result.passedTests = coverage.passedTests;
            result.failedTests = coverage.failedTests;
            result.success = true;
            
            System.out.println("✅ Coverage: " + String.format("%.2f%%", result.lineCoverage));
            System.out.println("   Kapsanan satırlar: " + result.coveredLines + "/" + result.totalLines);
            System.out.println("   Test başarısı: " + result.passedTests + "/" + result.totalTests + " (" + 
                String.format("%.1f%%", result.totalTests > 0 ? (result.passedTests * 100.0 / result.totalTests) : 0) + ")");
            
        } catch (Exception e) {
            result.success = false;
            result.errorMessage = e.getMessage();
            System.err.println("❌ Hata: " + e.getMessage());
        }
        
        result.totalTime = System.currentTimeMillis() - startTime;
        return result;
    }
    
    private static String createSimplePrompt(String sourceCode) {
        return String.format(
            "Role: Java Test Engineer.\n" +
            "Task: Write a comprehensive JUnit 5 test class for the provided Java code to achieve 100%% line and branch coverage in a single execution.\n\n" +
            "Source Code:\n" +
            "```java\n%s\n```\n\n" +
            "Instructions & Rules:\n" +
            "1. Analyze the Logic: Identify all possible execution paths, including edge cases, boundary values, and potential error conditions within the code.\n" +
            "2. Coverage Goal: Create a sufficient number of @Test methods to ensure every line and every logical branch is executed.\n" +
            "3. **Oracle Responsibility:** You must determine the correct expected outputs based on the code's intended logic. If the code has a bug, write the test to FAIL by asserting the CORRECT logical value.\n" +
            "4. Implementation Details:\n" +
            "   - Use JUnit 5 assertions (assertEquals, assertTrue, etc.).\n" +
            "   - Name the test class 'HesaplamaTest' (or match the target class name + Test).\n" +
            "   - For methods that return a value, assert the result directly.\n" +
            "   - For void methods that print to the console, use a ByteArrayOutputStream to capture and verify the output.\n\n" +
            "Constraints:\n" +
            " - **No Null Primitives:** Do not pass `null` values to primitive types (like `int`, `double`), as this will cause compilation errors.\n" +
            " - Self-Contained: The output must be valid, compilable Java code only. Do not include any explanations or markdown prose outside the code block.\n" +
            " - Output Format: Provide ONLY the complete Java source code for the test class.",
            sourceCode
        );
    }
    
    private static String askLLM(String prompt) throws Exception {
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        
        JsonArray messages = new JsonArray();
        messages.add(userMessage);
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "gpt-4o-mini");
        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.7);
        
        String jsonBody = requestBody.toString();
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("API Hatası: " + response.statusCode() + " - " + response.body());
        }
        
        JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray choices = jsonResponse.getAsJsonArray("choices");
        return choices.get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
    }
    
    private static String extractTestCode(String llmResponse) {
        String code = llmResponse;
        
        // Markdown kod bloğu varsa çıkar
        if (code.contains("```java")) {
            String[] parts = code.split("```java");
            if (parts.length > 1) {
                code = parts[1].split("```")[0];
            }
        } else if (code.contains("```")) {
            String[] parts = code.split("```");
            if (parts.length > 1) {
                code = parts[1];
            }
        }
        
        code = code.trim();
        
        // JUnit 4'ü JUnit 5'e çevir
        code = code.replace("import org.junit.Test;", "import org.junit.jupiter.api.Test;");
        code = code.replace("import static org.junit.Assert.", "import static org.junit.jupiter.api.Assertions.");
        code = code.replace("import org.junit.Before;", "import org.junit.jupiter.api.BeforeEach;");
        code = code.replace("import org.junit.After;", "import org.junit.jupiter.api.AfterEach;");
        code = code.replace("@Before", "@BeforeEach");
        code = code.replace("@After", "@AfterEach");
        
        // Test sınıfını public yap
        code = code.replaceAll("(?m)^class (\\w+Test)", "public class $1");
        code = code.replaceAll("(?m)^\\s+class (\\w+Test)", "public class $1");
        
        // Package bilgisi yoksa ekle (sınıf adından çıkar)
        if (!code.contains("package ")) {
            // Test sınıfının hedef aldığı sınıfı bul
            Pattern pattern = Pattern.compile("new\\s+(\\w+)\\(\\)");
            Matcher matcher = pattern.matcher(code);
            if (matcher.find()) {
                String targetClass = matcher.group(1);
                // app package'ına ait olduğunu varsay
                code = "package app;\n\n" + code;
            }
        }
        
        // Eksik import'ları ekle
        if (!code.contains("import java.io.")) {
            if (code.contains("PrintStream") || code.contains("ByteArrayOutputStream")) {
                // Java IO import'larını ekle
                String imports = "import java.io.ByteArrayOutputStream;\nimport java.io.PrintStream;\n";
                code = code.replaceFirst("(package [^;]+;\\n)", "$1\n" + imports);
            }
        }
        
        // Mockito import'larını kaldır (kullanmıyoruz)
        code = code.replaceAll("import static org\\.mockito\\.Mockito\\.\\*;\\n?", "");
        code = code.replaceAll("import org\\.mockito\\.\\*;\\n?", "");
        
        return code;
    }
    
    private static CoverageResult runTestWithCoverage(String sourceCode, String testCode) throws Exception {
        // Basit bir yaklaşım: test ve kaynak kodları dosyaya yazıp gradle ile çalıştır yerine
        // direkt reflection ile coverage ölçelim
        
        String fullClassName = extractFullClassName(sourceCode);
        String testClassName = extractFullClassName(testCode);
        
        // Derleme
        Map<String, byte[]> compiledClasses = compileMultipleFiles(
            Map.of(
                fullClassName, sourceCode,
                testClassName, testCode
            )
        );
        
        byte[] originalBytes = compiledClasses.get(fullClassName);
        byte[] testBytes = compiledClasses.get(testClassName);
        
        if (originalBytes == null || testBytes == null) {
            throw new RuntimeException("Derleme başarısız");
        }
        
        // JaCoCo offline instrumentation
        IRuntime runtime = new LoggerRuntime();
        Instrumenter instrumenter = new Instrumenter(runtime);
        byte[] instrumentedBytes = instrumenter.instrument(originalBytes, fullClassName);
        
        RuntimeData data = new RuntimeData();
        runtime.startup(data);
        
        // Özel ClassLoader - instrumented sınıf için
        MemoryClassLoader loader = new MemoryClassLoader(SimpleLLMBenchmark2.class.getClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                // Ana sınıf soruluyorsa instrumented versiyonu kullan
                if (name.equals(fullClassName)) {
                    return findClass(name);
                }
                return super.loadClass(name);
            }
        };
        
        loader.addDefinition(fullClassName, instrumentedBytes);
        loader.addDefinition(testClassName, testBytes);
        
        // Test sınıfını yükle ve çalıştır
        int totalTests = 0;
        int passedTests = 0;
        int failedTests = 0;
        
        Thread currentThread = Thread.currentThread();
        ClassLoader oldLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(loader);
            
            Class<?> testClass = loader.findClass(testClassName);
            Object testInstance = testClass.getDeclaredConstructor().newInstance();
            
            // Test metodlarını çalıştır
            for (var method : testClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
                    totalTests++;
                    try {
                        method.setAccessible(true);
                        method.invoke(testInstance);
                        passedTests++;
                    } catch (Exception e) {
                        failedTests++;
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        System.out.println("  ⚠️  Test başarısız: " + method.getName() + " - " + 
                            cause.getClass().getSimpleName());
                    }
                }
            }
        } finally {
            currentThread.setContextClassLoader(oldLoader);
        }
        
        // Coverage verilerini topla
        ExecutionDataStore executionData = new ExecutionDataStore();
        SessionInfoStore sessionInfos = new SessionInfoStore();
        data.collect(executionData, sessionInfos, false);
        runtime.shutdown();
        
        // Coverage analizi
        CoverageBuilder coverageBuilder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(executionData, coverageBuilder);
        analyzer.analyzeClass(new ByteArrayInputStream(originalBytes), fullClassName);
        
        // Sonuçları hesapla
        CoverageResult result = new CoverageResult();
        result.totalTests = totalTests;
        result.passedTests = passedTests;
        result.failedTests = failedTests;
        
        for (IClassCoverage cc : coverageBuilder.getClasses()) {
            result.totalLines = cc.getLineCounter().getTotalCount();
            result.coveredLines = cc.getLineCounter().getCoveredCount();
            if (result.totalLines > 0) {
                result.coverage = (double) result.coveredLines / result.totalLines * 100.0;
            }
        }
        
        return result;
    }
    
    private static Map<String, byte[]> compileMultipleFiles(Map<String, String> sources) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("Java Compiler bulunamadı. JDK kullandığınızdan emin olun.");
        }
        
        StandardJavaFileManager stdManager = compiler.getStandardFileManager(null, null, null);
        MemoryJavaFileManager fileManager = new MemoryJavaFileManager(stdManager);
        
        // Tüm kaynak dosyalarını oluştur
        List<SimpleJavaFileObject> files = new ArrayList<>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String fullClassName = entry.getKey();
            String sourceCode = entry.getValue();
            
            SimpleJavaFileObject file = new SimpleJavaFileObject(
                URI.create("string:///" + fullClassName.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return sourceCode;
                }
            };
            files.add(file);
        }
        
        // Classpath'e JUnit ve diğer bağımlılıkları ekle
        List<String> options = Arrays.asList(
            "-classpath", System.getProperty("java.class.path")
        );
        
        JavaCompiler.CompilationTask task = compiler.getTask(
            null, fileManager, null, options, null, files
        );
        
        boolean success = task.call();
        if (!success) {
            throw new RuntimeException("Derleme başarısız");
        }
        
        // Derlenmiş sınıfları topla
        Map<String, byte[]> result = new HashMap<>();
        for (String className : sources.keySet()) {
            byte[] bytes = fileManager.getClassBytes(className);
            if (bytes != null) {
                result.put(className, bytes);
            }
        }
        
        return result;
    }
    
    private static String extractFullClassName(String sourceCode) {
        String packageName = "";
        String simpleClassName = "";
        
        Matcher pkgMatcher = Pattern.compile("package\\s+([\\w.]+);").matcher(sourceCode);
        if (pkgMatcher.find()) {
            packageName = pkgMatcher.group(1);
        }
        
        Matcher classMatcher = Pattern.compile("(?:public\\s+)?class\\s+(\\w+)").matcher(sourceCode);
        if (classMatcher.find()) {
            simpleClassName = classMatcher.group(1);
        }
        
        return packageName.isEmpty() ? simpleClassName : packageName + "." + simpleClassName;
    }
    
    private static MemoryJavaFileManager compileInMemory(String fullClassName, String sourceCode) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("Java Compiler bulunamadı. JDK kullandığınızdan emin olun.");
        }
        
        StandardJavaFileManager stdManager = compiler.getStandardFileManager(null, null, null);
        MemoryJavaFileManager fileManager = new MemoryJavaFileManager(stdManager);
        
        SimpleJavaFileObject file = new SimpleJavaFileObject(
            URI.create("string:///" + fullClassName.replace('.', '/') + ".java"),
            JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return sourceCode;
            }
        };
        
        // Classpath'e JUnit ve diğer bağımlılıkları ekle
        List<String> options = Arrays.asList(
            "-classpath", System.getProperty("java.class.path")
        );
        
        JavaCompiler.CompilationTask task = compiler.getTask(
            null, fileManager, null, options, null, Arrays.asList(file)
        );
        
        boolean success = task.call();
        if (!success) {
            throw new RuntimeException("Derleme başarısız: " + fullClassName);
        }
        
        return fileManager;
    }
    
    private static void saveResults(List<BenchmarkResult> results) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "simple_benchmark_" + timestamp + ".txt";
            
            StringBuilder sb = new StringBuilder();
            sb.append("=".repeat(80)).append("\n");
            sb.append("BASIT LLM BENCHMARK SONUÇLARI\n");
            sb.append("Tarih: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).append("\n");
            sb.append("=".repeat(80)).append("\n\n");
            
            for (BenchmarkResult result : results) {
                sb.append("─".repeat(80)).append("\n");
                sb.append("Dosya: ").append(result.fileName).append("\n");
                sb.append("Durum: ").append(result.success ? "✅ BAŞARILI" : "❌ BAŞARISIZ").append("\n");
                
                if (result.success) {
                    sb.append("Coverage: ").append(String.format("%.2f%%", result.lineCoverage))
                      .append(" (").append(result.coveredLines).append("/").append(result.totalLines).append(")\n");
                    sb.append("Test Başarısı: ").append(result.passedTests).append("/").append(result.totalTests)
                      .append(" (").append(String.format("%.1f%%", result.totalTests > 0 ? (result.passedTests * 100.0 / result.totalTests) : 0)).append(")\n");
                } else {
                    sb.append("Hata: ").append(result.errorMessage).append("\n");
                }
                
                sb.append("LLM Yanıt Süresi: ").append(result.llmResponseTime).append("ms\n");
                sb.append("Toplam Süre: ").append(result.totalTime).append("ms\n");
                sb.append("\n--- LLM Yanıtı ---\n");
                sb.append(result.llmResponse).append("\n");
                sb.append("\n--- Üretilen Test Kodu ---\n");
                sb.append(result.generatedTestCode != null ? result.generatedTestCode : "YOK").append("\n");
                sb.append("\n");
            }
            
            Files.write(Paths.get(filename), sb.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("\n📝 Sonuçlar kaydedildi: " + filename);
            
        } catch (IOException e) {
            System.err.println("❌ Sonuçlar kaydedilemedi: " + e.getMessage());
        }
    }
    
    private static void printSummary(List<BenchmarkResult> results) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ÖZET RAPOR");
        System.out.println("=".repeat(80));
        
        int totalTests = results.size();
        int successfulTests = (int) results.stream().filter(r -> r.success).count();
        double avgCoverage = results.stream()
                .filter(r -> r.success)
                .mapToDouble(r -> r.lineCoverage)
                .average()
                .orElse(0.0);
        
        int totalTestCount = results.stream().filter(r -> r.success).mapToInt(r -> r.totalTests).sum();
        int passedTestCount = results.stream().filter(r -> r.success).mapToInt(r -> r.passedTests).sum();
        double avgTestSuccess = totalTestCount > 0 ? (passedTestCount * 100.0 / totalTestCount) : 0;
        
        double avgTime = results.stream()
                .mapToDouble(r -> r.totalTime)
                .average()
                .orElse(0.0);
        
        System.out.println("Toplam Dosya: " + totalTests);
        System.out.println("Başarılı Derleme: " + successfulTests);
        System.out.println("Başarısız Derleme: " + (totalTests - successfulTests));
        System.out.println("─".repeat(80));
        System.out.println("Ortalama Line Coverage: " + String.format("%.2f%%", avgCoverage));
        System.out.println("Test Başarı Oranı: " + passedTestCount + "/" + totalTestCount + 
            " (" + String.format("%.1f%%", avgTestSuccess) + ")");
        System.out.println("─".repeat(80));
        System.out.println("⚠️  DİKKAT: Coverage yüksek ama test başarısı düşükse,");
        System.out.println("    LLM kodu yanlış anlamış ve hatalı assertion yazmış demektir!");
        System.out.println("=".repeat(80));
    }
    
    // Yardımcı sınıflar
    static class BenchmarkResult {
        String fileName;
        LocalDateTime timestamp;
        boolean success;
        String errorMessage;
        double lineCoverage;
        int coveredLines;
        int totalLines;
        int totalTests;
        int passedTests;
        int failedTests;
        long llmResponseTime;
        long totalTime;
        String llmResponse;
        String generatedTestCode;
    }
    
    static class CoverageResult {
        double coverage;
        int coveredLines;
        int totalLines;
        int totalTests;
        int passedTests;
        int failedTests;
    }
    
    static class MemoryClassLoader extends ClassLoader {
        private final Map<String, byte[]> definitions = new HashMap<>();
        
        public MemoryClassLoader(ClassLoader parent) {
            super(parent);
        }
        
        public void addDefinition(String name, byte[]bytes) {
            definitions.put(name, bytes);
        }
        
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = definitions.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            throw new ClassNotFoundException(name);
        }
        
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            // Önce kendi tanımlarımızda ara
            if (definitions.containsKey(name)) {
                try {
                    return findClass(name);
                } catch (ClassNotFoundException e) {
                    // Devam et
                }
            }
            // Bulunamazsa parent'a sor
            return super.loadClass(name);
        }
    }
    
    static class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteArrayOutputStream> classBytes = new HashMap<>();
        
        protected MemoryJavaFileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }
        
        public byte[] getClassBytes(String className) {
            ByteArrayOutputStream out = classBytes.get(className);
            return out != null ? out.toByteArray() : null;
        }
        
        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                   JavaFileObject.Kind kind, FileObject sibling) {
            return new SimpleJavaFileObject(
                URI.create("string:///" + className.replace('.', '/') + kind.extension),
                kind) {
                @Override
                public OutputStream openOutputStream() {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    classBytes.put(className, out);
                    return out;
                }
            };
        }
    }
}
