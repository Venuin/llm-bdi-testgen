package tools;

import cartago.*;
import org.jacoco.core.analysis.*;
import org.jacoco.core.data.*;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.*;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*; // YENİ: Timeout işlemleri için eklendi
import java.util.regex.*;

@ARTIFACT_INFO(outports = { @OUTPORT(name = "out-1") })
public class JaCoCoGenericRunner extends Artifact {

    @OPERATION
    public void analyzeCodeStructure(String sourceCode, OpFeedbackParam<String> methodName, OpFeedbackParam<Integer> paramCount, OpFeedbackParam<String> paramTypes) {
        // Bu metod aynı kalıyor...
        try {
            String fullClassName = extractFullClassName(sourceCode);
            MemoryJavaFileManager fileManager = compileInMemory(fullClassName, sourceCode);
            byte[] bytes = fileManager.getClassBytes(fullClassName);
            if (bytes == null) {
                failed("Derleme hatası.");
                return;
            }
            MemoryClassLoader loader = new MemoryClassLoader(getClass().getClassLoader());
            loader.addDefinition(fullClassName, bytes);
            Class<?> targetClass = loader.loadClass(fullClassName);
            Method targetMethod = findTargetMethod(targetClass);

            if (targetMethod != null) {
                methodName.set(targetMethod.getName());
                paramCount.set(targetMethod.getParameterCount());
                
                // Parametre tiplerini string olarak döndür
                Class<?>[] parameterTypes = targetMethod.getParameterTypes();
                StringBuilder typesStr = new StringBuilder();
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (i > 0) typesStr.append(",");
                    typesStr.append(parameterTypes[i].getSimpleName());
                }
                paramTypes.set(typesStr.toString());
            } else {
                failed("Public metod bulunamadı.");
            }
        } catch (Exception e) {
            failed("Kod Analiz Hatası: " + e.getMessage());
        }
    }

    // GÜNCELLENEN METOD: Timeout (Sonsuz Döngü Koruması) Eklendi + Visual Coverage Parametresi
    @OPERATION
    public void runTestAndMeasureCoverage(String sourceCode, Object[] inputs, OpFeedbackParam<String> reportParam, OpFeedbackParam<Object[]> hitLinesParam, OpFeedbackParam<String> visualCoverageParam) {
        System.out.println("[JaCoCoRunner] Test ve Analiz Başlıyor (Generic Mod + Timeout Protection)...");
        
        try {
            // 1. Sınıfı Derle ve Yükle
            String fullClassName = extractFullClassName(sourceCode);
            MemoryJavaFileManager fileManager = compileInMemory(fullClassName, sourceCode);
            byte[] originalBytes = fileManager.getClassBytes(fullClassName);
            
            // 2. JaCoCo Instrumentation (Kodun içine izleme ajanlarını yerleştir)
            final IRuntime runtime = new LoggerRuntime();
            final Instrumenter instrumenter = new Instrumenter(runtime);
            byte[] instrumentedBytes = instrumenter.instrument(originalBytes, fullClassName);

            RuntimeData data = new RuntimeData();
            runtime.startup(data);

            // 3. Enstrümante edilmiş sınıfı belleğe yükle
            MemoryClassLoader loader = new MemoryClassLoader(getClass().getClassLoader());
            loader.addDefinition(fullClassName, instrumentedBytes);
            Class<?> targetClass = loader.loadClass(fullClassName);
            Object instance = targetClass.getDeclaredConstructor().newInstance();

            // 4. Hedef Metodu ve Parametre Tiplerini Bul
            Method targetMethod = findTargetMethod(targetClass);
            if (targetMethod == null) {
                failed("Test edilecek public metod bulunamadı.");
                return;
            }
            
            Class<?>[] paramTypes = targetMethod.getParameterTypes();
            List<Object[]> groupedInputs = extractInputsDynamic(inputs, paramTypes);

            // 5. Test Senaryolarını Çalıştır (Timeout Korumalı)
            StringBuilder executionLog = new StringBuilder();
            
            for (Object[] params : groupedInputs) {
                // Her test için ayrı bir Executor (Thread) açıyoruz ki donarsa öldürebilelim.
                ExecutorService executor = Executors.newSingleThreadExecutor();
                
                try {
                    // Testi ayrı bir thread'de çalıştır
                    Future<Object> future = executor.submit(() -> {
                        return targetMethod.invoke(instance, params);
                    });

                    // --- KRİTİK GÜNCELLEME: 2 Saniye Zaman Aşımı ---
                    // Eğer kod 2 saniyede bitmezse TimeoutException fırlatır.
                    Object result = future.get(2, TimeUnit.SECONDS); 
                    
                    executionLog.append("Input: ").append(Arrays.deepToString(params)).append("\n");

                } catch (TimeoutException e) {
                    // Sonsuz döngü yakalandı!
                    executionLog.append("Input: ").append(Arrays.deepToString(params))
                                .append(" -> ERROR: INFINITE LOOP DETECTED (Timeout)\n");
                    // İşlemi zorla iptal et
                } catch (Exception e) {
                    // Diğer hatalar (NullPointer vb.)
                    String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    executionLog.append("Input: ").append(Arrays.deepToString(params))
                                .append(" -> EXCEPTION: ").append(errorMsg).append("\n");
                } finally {
                    // Executor'ı temizle
                    executor.shutdownNow();
                }
            }

            // 6. JaCoCo Verilerini Topla
            ExecutionDataStore executionData = new ExecutionDataStore();
            SessionInfoStore sessionInfos = new SessionInfoStore();
            data.collect(executionData, sessionInfos, false);
            runtime.shutdown();

            // 7. Kapsama (Coverage) Analizini Yap
            CoverageBuilder coverageBuilder = new CoverageBuilder();
            Analyzer analyzer = new Analyzer(executionData, coverageBuilder);
            analyzer.analyzeClass(new ByteArrayInputStream(originalBytes), fullClassName);

            // 8. Rapor Oluştur
            StringBuilder report = new StringBuilder();
            StringBuilder visualCoverage = new StringBuilder();
            Set<Integer> hitLineNumbers = new HashSet<>(); 
            
            report.append("REAL EXECUTION REPORT (JaCoCo Engine):\n");
            report.append(executionLog.toString());
            report.append("\n--- VISUAL COVERAGE ---\n");

            int totalLines = 0;
            int coveredLines = 0;

            for (IClassCoverage cc : coverageBuilder.getClasses()) {
                String[] lines = sourceCode.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    int lineNo = i + 1;
                    ILine line = cc.getLine(lineNo);
                    int status = line.getStatus();
                    String prefix = "      "; 

                    if (status != ICounter.EMPTY) {
                        totalLines++;
                        if (status == ICounter.NOT_COVERED) {
                            prefix = "[MISS] ";
                        } else {
                            prefix = "[HIT] ";
                            coveredLines++;
                            hitLineNumbers.add(lineNo);
                        }
                    }
                    String lineFormat = String.format("%s %2d: %s\n", prefix, lineNo, lines[i]);
                    report.append(lineFormat);
                    visualCoverage.append(lineFormat);
                }
            }
            
            double ratio = (totalLines == 0) ? 0 : (double)coveredLines / totalLines * 100;
            report.append("\nSUMMARY\nCOVERAGE: ").append(String.format("%.2f", ratio)).append("%\n");

            reportParam.set(report.toString());
            hitLinesParam.set(hitLineNumbers.toArray());
            visualCoverageParam.set(visualCoverage.toString()); 

        } catch (Exception e) {
            e.printStackTrace();
            reportParam.set("RUNNER ERROR: " + e.getMessage());
        }
    }

    // Diğer yardımcı metodlar aynı kalıyor...
    private String extractFullClassName(String sourceCode) {
        String packageName = "";
        String simpleClassName = "Hesaplama";
        Matcher pkgMatcher = Pattern.compile("package\\s+([\\w.]+);").matcher(sourceCode);
        if (pkgMatcher.find()) packageName = pkgMatcher.group(1);
        Matcher classMatcher = Pattern.compile("class\\s+(\\w+)").matcher(sourceCode);
        if (classMatcher.find()) simpleClassName = classMatcher.group(1);
        return packageName.isEmpty() ? simpleClassName : packageName + "." + simpleClassName;
    }

    public static class MemoryClassLoader extends ClassLoader {
        private final Map<String, byte[]> definitions = new HashMap<>();
        public MemoryClassLoader(ClassLoader parent) { super(parent); }
        public void addDefinition(String name, byte[] bytes) { definitions.put(name, bytes); }
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (definitions.containsKey(name)) return defineClass(name, definitions.get(name), 0, definitions.get(name).length);
            return super.loadClass(name);
        }
    }

    private MemoryJavaFileManager compileInMemory(String fullClassName, String sourceCode) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager stdManager = compiler.getStandardFileManager(null, null, null);
        MemoryJavaFileManager fileManager = new MemoryJavaFileManager(stdManager);
        SimpleJavaFileObject file = new SimpleJavaFileObject(URI.create("string:///" + fullClassName.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignore) { return sourceCode; }
        };
        compiler.getTask(null, fileManager, null, Arrays.asList("-classpath", System.getProperty("java.class.path")), null, Arrays.asList(file)).call();
        return fileManager;
    }

    private static class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteArrayOutputStream> classBytes = new HashMap<>();
        protected MemoryJavaFileManager(StandardJavaFileManager m) { super(m); }
        public byte[] getClassBytes(String n) { return classBytes.containsKey(n) ? classBytes.get(n).toByteArray() : null; }
        @Override
        public JavaFileObject getJavaFileForOutput(Location l, String c, JavaFileObject.Kind k, FileObject s) {
            return new SimpleJavaFileObject(URI.create("string:///" + c.replace('.', '/') + k.extension), k) {
                public OutputStream openOutputStream() {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    classBytes.put(c, b);
                    return b;
                }
            };
        }
    }

    private List<Object[]> extractInputsDynamic(Object[] raw, Class<?>[] paramTypes) {
        List<Object[]> grouped = new ArrayList<>();
        
        for (Object obj : raw) {
            String inputStr = obj.toString().trim();
            
            try {
                List<Object> values = parseParameters(inputStr, paramTypes);
                if (values != null && values.size() == paramTypes.length) {
                    grouped.add(values.toArray());
                }
            } catch (Exception e) {
                System.out.println("RUNNER ERROR: " + e.getMessage());
            }
        }
        return grouped;
    }
    
    private List<Object> parseParameters(String input, Class<?>[] paramTypes) {
        List<Object> values = new ArrayList<>();
        int pos = 0;
        
        // Skip opening bracket if present
        if (input.startsWith("[")) {
            pos = 1;
        }
        
        for (int paramIdx = 0; paramIdx < paramTypes.length; paramIdx++) {
            // Skip whitespace and commas
            while (pos < input.length() && (input.charAt(pos) == ' ' || input.charAt(pos) == ',')) {
                pos++;
            }
            
            if (pos >= input.length()) break;
            
            Class<?> targetType = paramTypes[paramIdx];
            
            if (targetType.isArray()) {
                // Check for null array first
                if (input.substring(pos).startsWith("null")) {
                    values.add(null);
                    pos += 4;
                } else {
                    // Parse array: [elem1, elem2, ...]
                    if (input.charAt(pos) != '[') {
                        throw new RuntimeException("Expected '[' for array parameter");
                    }
                    pos++; // skip [
                    
                    int depth = 1;
                    int start = pos;
                    while (pos < input.length() && depth > 0) {
                        if (input.charAt(pos) == '[') depth++;
                        else if (input.charAt(pos) == ']') {
                            depth--;
                            if (depth == 0) break;
                        }
                        pos++;
                    }
                    
                    String arrayContent = input.substring(start, pos).trim();
                    values.add(parseArray(arrayContent, targetType.getComponentType()));
                    pos++; // skip ]
                }
                
            } else if (input.substring(pos).startsWith("null")) {
                values.add(null);
                pos += 4;
            } else if (input.substring(pos).startsWith("true")) {
                values.add(true);
                pos += 4;
            } else if (input.substring(pos).startsWith("false")) {
                values.add(false);
                pos += 5;
            } else if (input.charAt(pos) == '"') {
                // Parse string
                pos++; // skip opening "
                int endQuote = input.indexOf('"', pos);
                values.add(input.substring(pos, endQuote));
                pos = endQuote + 1;
            } else if (input.charAt(pos) == '{') {
                // Parse JSON object for complex types (e.g., Employee)
                int depth = 1;
                int start = pos;
                pos++; // skip {
                while (pos < input.length() && depth > 0) {
                    if (input.charAt(pos) == '{') depth++;
                    else if (input.charAt(pos) == '}') depth--;
                    if (input.charAt(pos) == '"') {
                        pos++; // skip opening quote
                        while (pos < input.length() && input.charAt(pos) != '"') {
                            if (input.charAt(pos) == '\\') pos++;
                            pos++;
                        }
                    }
                    pos++;
                }
                String jsonContent = input.substring(start, pos).trim();
                try {
                    values.add(createObjectFromJson(jsonContent, targetType));
                } catch (Exception e) {
                    System.out.println("RUNNER ERROR: JSON object parse failed: " + e.getMessage());
                    values.add(null);
                }
            } else {
                // Parse number
                int start = pos;
                while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.' || input.charAt(pos) == '-')) {
                    pos++;
                }
                String numStr = input.substring(start, pos);
                
                if (targetType == int.class || targetType == Integer.class) {
                    values.add(Integer.parseInt(numStr));
                } else if (targetType == double.class || targetType == Double.class) {
                    values.add(Double.parseDouble(numStr));
                }
            }
        }
        
        return values;
    }
    
    private Object parseArray(String content, Class<?> componentType) {
        List<Object> items = new ArrayList<>();
        int pos = 0;
        
        while (pos < content.length()) {
            // Skip whitespace and commas
            while (pos < content.length() && (content.charAt(pos) == ' ' || content.charAt(pos) == ',')) {
                pos++;
            }
            
            if (pos >= content.length()) break;
            
            if (componentType == String.class && content.charAt(pos) == '"') {
                pos++; // skip "
                int endQuote = content.indexOf('"', pos);
                items.add(content.substring(pos, endQuote));
                pos = endQuote + 1;
            } else if (componentType == boolean.class || componentType == Boolean.class) {
                if (content.substring(pos).startsWith("true")) {
                    items.add(true);
                    pos += 4;
                } else {
                    items.add(false);
                    pos += 5;
                }
            } else {
                // Parse number (int or double)
                int start = pos;
                while (pos < content.length() && (Character.isDigit(content.charAt(pos)) || content.charAt(pos) == '.' || content.charAt(pos) == '-')) {
                    pos++;
                }
                String numStr = content.substring(start, pos);
                
                if (componentType == int.class || componentType == Integer.class) {
                    items.add(Integer.parseInt(numStr));
                } else if (componentType == double.class || componentType == Double.class) {
                    items.add(Double.parseDouble(numStr));
                }
            }
        }
        
        // Convert to array
        if (componentType == int.class) {
            int[] arr = new int[items.size()];
            for (int i = 0; i < items.size(); i++) arr[i] = (Integer) items.get(i);
            return arr;
        } else if (componentType == double.class) {
            double[] arr = new double[items.size()];
            for (int i = 0; i < items.size(); i++) arr[i] = (Double) items.get(i);
            return arr;
        } else if (componentType == boolean.class) {
            boolean[] arr = new boolean[items.size()];
            for (int i = 0; i < items.size(); i++) arr[i] = (Boolean) items.get(i);
            return arr;
        } else if (componentType == String.class) {
            return items.toArray(new String[0]);
        }
        
        return null;
    }

    /**
     * Creates an object instance from a JSON string by setting public fields via reflection.
     * Supports: String, int, double, boolean, long, float, and null values.
     */
    private Object createObjectFromJson(String json, Class<?> targetType) throws Exception {
        Object instance = targetType.getDeclaredConstructor().newInstance();

        // Remove outer braces
        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) return instance;

        // Parse key-value pairs
        List<String[]> pairs = parseJsonPairs(content);

        for (String[] pair : pairs) {
            String key = pair[0];
            String value = pair[1].trim();

            try {
                java.lang.reflect.Field field = targetType.getField(key);
                Class<?> fieldType = field.getType();

                if (value.equals("null")) {
                    field.set(instance, null);
                } else if (fieldType == String.class) {
                    // Remove quotes
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        field.set(instance, value.substring(1, value.length() - 1));
                    } else {
                        field.set(instance, value);
                    }
                } else if (fieldType == int.class || fieldType == Integer.class) {
                    field.setInt(instance, (int) Double.parseDouble(value));
                } else if (fieldType == double.class || fieldType == Double.class) {
                    field.setDouble(instance, Double.parseDouble(value));
                } else if (fieldType == boolean.class || fieldType == Boolean.class) {
                    field.setBoolean(instance, Boolean.parseBoolean(value));
                } else if (fieldType == long.class || fieldType == Long.class) {
                    field.setLong(instance, (long) Double.parseDouble(value));
                } else if (fieldType == float.class || fieldType == Float.class) {
                    field.setFloat(instance, Float.parseFloat(value));
                }
            } catch (NoSuchFieldException e) {
                // Skip unknown fields silently
            }
        }

        return instance;
    }

    /**
     * Parses JSON key-value pairs from the content between { and }.
     * Returns list of [key, value] string arrays.
     */
    private List<String[]> parseJsonPairs(String content) {
        List<String[]> pairs = new ArrayList<>();
        int pos = 0;

        while (pos < content.length()) {
            // Skip whitespace
            while (pos < content.length() && Character.isWhitespace(content.charAt(pos))) pos++;
            if (pos >= content.length()) break;

            // Parse key (expect "key")
            if (content.charAt(pos) != '"') break;
            pos++; // skip opening "
            int keyStart = pos;
            while (pos < content.length() && content.charAt(pos) != '"') pos++;
            String key = content.substring(keyStart, pos);
            pos++; // skip closing "

            // Skip whitespace and colon
            while (pos < content.length() && (Character.isWhitespace(content.charAt(pos)) || content.charAt(pos) == ':')) pos++;

            // Parse value
            int valueStart = pos;
            if (pos < content.length() && content.charAt(pos) == '"') {
                // String value
                pos++; // skip opening "
                while (pos < content.length() && content.charAt(pos) != '"') {
                    if (content.charAt(pos) == '\\') pos++; // skip escape
                    pos++;
                }
                pos++; // skip closing "
            } else if (pos < content.length() && content.charAt(pos) == '{') {
                // Nested object
                int depth = 1;
                pos++;
                while (pos < content.length() && depth > 0) {
                    if (content.charAt(pos) == '{') depth++;
                    else if (content.charAt(pos) == '}') depth--;
                    pos++;
                }
            } else if (pos < content.length() && content.charAt(pos) == '[') {
                // Array value
                int depth = 1;
                pos++;
                while (pos < content.length() && depth > 0) {
                    if (content.charAt(pos) == '[') depth++;
                    else if (content.charAt(pos) == ']') depth--;
                    pos++;
                }
            } else {
                // Number, boolean, or null
                while (pos < content.length() && content.charAt(pos) != ',' && content.charAt(pos) != '}') pos++;
            }

            String value = content.substring(valueStart, pos).trim();
            pairs.add(new String[]{key, value});

            // Skip comma
            while (pos < content.length() && (Character.isWhitespace(content.charAt(pos)) || content.charAt(pos) == ',')) pos++;
        }

        return pairs;
    }

    private Method findTargetMethod(Class<?> cls) {
        for (Method m : cls.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) return m;
        }
        return null;
    }
}