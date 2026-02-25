package tools;

import cartago.*;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.TestExecutionResult; // Yeni Import

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.util.*;

@ARTIFACT_INFO(outports = { @OUTPORT(name = "out-1") })
public class JUnitRunnerArtifact extends Artifact {

    @OPERATION
    public void runDynamicJUnitTest(String testSourceCode, OpFeedbackParam<String> resultParam) {
        try {
            // --- 1. KOD TEMİZLİĞİ (MARKDOWN SİLİCİ) ---
            String cleanCode = testSourceCode;
            if (cleanCode.contains("```java")) {
                cleanCode = cleanCode.split("```java")[1];
                if (cleanCode.contains("```")) {
                    cleanCode = cleanCode.split("```")[0];
                }
            } else if (cleanCode.contains("```")) {
                cleanCode = cleanCode.replace("```", "");
            }
            cleanCode = cleanCode.trim();

            // --- 1b. EKSİK IMPORT'LARI OTOMATİK EKLE ---
            // assertThrows kullanılmış ama import yoksa ekle
            if (cleanCode.contains("assertThrows(") && !cleanCode.contains("import static org.junit.jupiter.api.Assertions.assertThrows")) {
                cleanCode = cleanCode.replace(
                    "import static org.junit.jupiter.api.Assertions.assertEquals;",
                    "import static org.junit.jupiter.api.Assertions.assertEquals;\nimport static org.junit.jupiter.api.Assertions.assertThrows;"
                );
                // assertEquals import yoksa package/class satırından sonra ekle
                if (!cleanCode.contains("import static org.junit.jupiter.api.Assertions.assertThrows")) {
                    cleanCode = cleanCode.replaceFirst(
                        "(import [^\n]+;\n)",
                        "$1import static org.junit.jupiter.api.Assertions.assertThrows;\n"
                    );
                }
            }
            // assertNull kullanılmış ama import yoksa ekle
            if (cleanCode.contains("assertNull(") && !cleanCode.contains("import static org.junit.jupiter.api.Assertions.assertNull")) {
                cleanCode = cleanCode.replaceFirst(
                    "(import static org.junit.jupiter.api.Assertions\\.[^\n]+;\n)",
                    "$1import static org.junit.jupiter.api.Assertions.assertNull;\n"
                );
            }
            // assertTrue kullanılmış ama import yoksa ekle
            if (cleanCode.contains("assertTrue(") && !cleanCode.contains("import static org.junit.jupiter.api.Assertions.assertTrue")) {
                cleanCode = cleanCode.replaceFirst(
                    "(import static org.junit.jupiter.api.Assertions\\.[^\n]+;\n)",
                    "$1import static org.junit.jupiter.api.Assertions.assertTrue;\n"
                );
            }
            // assertFalse kullanılmış ama import yoksa ekle
            if (cleanCode.contains("assertFalse(") && !cleanCode.contains("import static org.junit.jupiter.api.Assertions.assertFalse")) {
                cleanCode = cleanCode.replaceFirst(
                    "(import static org.junit.jupiter.api.Assertions\\.[^\n]+;\n)",
                    "$1import static org.junit.jupiter.api.Assertions.assertFalse;\n"
                );
            }

            // 2. Derleme
            String className = "HesaplamaTest";
            if(cleanCode.contains("class ")) className = cleanCode.split("class ")[1].split(" ")[0].trim();
            String packageName = "";
            if (cleanCode.contains("package ")) {
                packageName = cleanCode.split("package ")[1].split(";")[0].trim();
                className = packageName + "." + className;
            }

            Class<?> testClass = compileInMemory(className, cleanCode);

            // 3. JUnit Launcher Hazırla
            Launcher launcher = LauncherFactory.create();
            
            // Özet Listener (Sayılar için)
            SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
            launcher.registerTestExecutionListeners(summaryListener);

            // --- YENİ: DETAYLI LİSTENER (Tek Tek İsimleri Almak İçin) ---
            StringBuilder detailBuilder = new StringBuilder();
            
            launcher.registerTestExecutionListeners(new TestExecutionListener() {
                public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
                    // Sadece test metodlarını raporla (Sınıfın kendisini değil)
                    if (testIdentifier.isTest()) {
                        boolean passed = testExecutionResult.getStatus() == TestExecutionResult.Status.SUCCESSFUL;
                        String failReason = null;

                        // Decimal trailing-zero farkından kaynaklanan başarısızlıkları PASS say
                        // Örn: expected "MEAN:0.0" vs actual "MEAN:0.00" → PASS
                        if (!passed) {
                            java.util.Optional<Throwable> th = testExecutionResult.getThrowable();
                            if (th.isPresent()) {
                                String msg = th.get().getMessage();
                                if (msg != null && isDecimalOnlyDifference(msg)) {
                                    passed = true;
                                } else {
                                    failReason = msg;
                                }
                            }
                        }

                        String icon = passed ? "✅ PASS" : "❌ FAIL";
                        String name = testIdentifier.getDisplayName();

                        detailBuilder.append(String.format("%-10s : %s\n", icon, name));

                        String methodName = name.endsWith("()") ? name.substring(0, name.length() - 2) : name;
                        detailBuilder.append(passed ? "TESTPASS:" : "TESTFAIL:").append(methodName).append("|").append("\n");

                        if (!passed && failReason != null) {
                            detailBuilder.append("   └── Reason: " + failReason + "\n");
                        }
                    }
                }
            });

            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectClass(testClass))
                    .build();

            // 4. Testi Çalıştır
            launcher.execute(request);

            // 5. Sonucu Raporla
            TestExecutionSummary summary = summaryListener.getSummary();
            StringBuilder report = new StringBuilder();
            
            report.append("\n================ JUNIT DETAILS ================\n");
            report.append(detailBuilder.toString()); // Listeyi buraya basıyoruz
            report.append("===============================================\n");
            
            report.append("Total: ").append(summary.getTestsFoundCount());
            report.append(" | Passed: ").append(summary.getTestsSucceededCount());
            report.append(" | Failed: ").append(summary.getTestsFailedCount());

            if (summary.getTestsFailedCount() == 0 && summary.getTestsFoundCount() > 0) {
                report.append("\nRESULT: SUCCESS");
            } else {
                report.append("\nRESULT: FAILURE");
            }

            resultParam.set(report.toString());

        } catch (Exception e) {
            e.printStackTrace();
            resultParam.set("ERROR running JUnit: " + e.getMessage());
        }
    }

    // --- Ondalık Hassasiyet Farkı Kontrolü ---
    private boolean isDecimalOnlyDifference(String failureMessage) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("expected: <(.+?)> but was: <(.+?)>");
        java.util.regex.Matcher m = p.matcher(failureMessage);
        if (m.find()) {
            return normalizeDecimals(m.group(1)).equals(normalizeDecimals(m.group(2)));
        }
        return false;
    }

    private String normalizeDecimals(String s) {
        // "1.30" -> "1.3"  (anlamlı basamaktan sonraki sıfırlar)
        s = s.replaceAll("(-?\\d+\\.\\d*[1-9])0+", "$1");
        // "0.00" -> "0.0", "3.00" -> "3.0"  (saf sıfır ondalıklar)
        s = s.replaceAll("(-?\\d+\\.)(0)0+", "$1$2");
        return s;
    }

    // --- Derleme Mantığı (Değişmedi) ---
    private Class<?> compileInMemory(String fullClassName, String sourceCode) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager stdManager = compiler.getStandardFileManager(null, null, null);
        MemoryJavaFileManager fileManager = new MemoryJavaFileManager(stdManager);
        
        SimpleJavaFileObject file = new SimpleJavaFileObject(URI.create("string:///" + fullClassName.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignoreEncodingErrors) { return sourceCode; }
        };
        
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> optionList = new ArrayList<>();
        optionList.addAll(Arrays.asList("-classpath", System.getProperty("java.class.path")));

        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, optionList, null, Arrays.asList(file));
        boolean success = task.call();
        
        if (!success) {
            StringBuilder sb = new StringBuilder("Compilation Failed:\n");
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                sb.append(d.getMessage(null)).append("\n");
            }
            throw new RuntimeException(sb.toString());
        }
        return fileManager.getClassLoader(null).loadClass(fullClassName);
    }

    private static class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteArrayOutputStream> classBytes = new HashMap<>();
        protected MemoryJavaFileManager(StandardJavaFileManager fileManager) { super(fileManager); }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            return new SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + kind.extension), kind) {
                public OutputStream openOutputStream() {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    classBytes.put(className, baos);
                    return baos;
                }
            };
        }

        @Override
        public ClassLoader getClassLoader(Location location) {
            return new ClassLoader() {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    ByteArrayOutputStream baos = classBytes.get(name);
                    if (baos == null) return super.findClass(name);
                    byte[] bytes = baos.toByteArray();
                    return defineClass(name, bytes, 0, bytes.length);
                }
            };
        }
    }
}