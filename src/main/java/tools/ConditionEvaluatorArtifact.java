package tools;

import cartago.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.*;
import java.util.regex.*;

/**
 * MISS durumunda hangi koşulun FALSE olduğunu tespit eden Artifact
 * Path ve Input değerlerini analiz ederek detaylı sebep açıklaması üretir
 * (JavaScript engine gerektirmez, basit heuristic-based analiz)
 */
public class ConditionEvaluatorArtifact extends Artifact {
    
    private Gson gson = new Gson();
    
    void init() {
        // Başlatma - herhangi bir dependency yok
    }
    
    /**
     * Path ve Input'u analiz edip hangi koşulun FALSE olabileceğini açıklar
     * Heuristic-based: Input değerlerini parse edip koşullara göre analiz yapar
     * 
     * @param pathStr Örnek: "22|PATH_TO_IF: !(diff > 0) [SUB_LOGIC: diff = (a + b) - (a * b)] && (product < 0) [SUB_LOGIC: product = a * b]"
     * @param inputJson Örnek: "[-1, 1]"
     * @param failureReason OUT parametresi: Detaylı sebep mesajı
     */
    @OPERATION
    void evaluateConditions(String pathStr, String inputJson, OpFeedbackParam<String> failureReason) {
        try {
            // 1. Input değerlerini parse et
            Map<String, Double> variables = parseInput(inputJson);
            
            // 2. Path'ten koşulları çıkar
            List<Condition> conditions = parseConditions(pathStr);
            
            if (conditions.isEmpty()) {
                failureReason.set("Target Path Conditions: " + pathStr);
                return;
            }
            
            // 3. Her koşulu analiz et ve açıklama üret
            StringBuilder reasonBuilder = new StringBuilder();
            
            for (int i = 0; i < conditions.size(); i++) {
                Condition cond = conditions.get(i);
                
                // SUB_LOGIC varsa önce hesapla ve variables'a ekle
                String calculationDetails = "";
                if (cond.subLogic != null && !cond.subLogic.isEmpty()) {
                    calculationDetails = calculateSubLogicWithDetails(cond.subLogic, variables);
                }
                
                // Expanded condition oluştur (SUB_LOGIC'i yerine koyarak)
                String expandedCondition = expandCondition(cond.mainCondition, cond.subLogic);
                
                // Ana koşulu analiz et
                String analysis = analyzeCondition(expandedCondition, cond.mainCondition, calculationDetails, variables);
                
                if (i > 0) {
                    reasonBuilder.append(" AND ");
                }
                reasonBuilder.append(analysis);
            }
            
            failureReason.set(reasonBuilder.toString());
            
        } catch (Exception e) {
            failureReason.set("Condition analysis error: " + e.getMessage() + ". Path: " + pathStr);
        }
    }
    
    /**
     * Input JSON'ı parse edip değişken map'i oluşturur
     * Örnek: "[-1, 1]" → {a: -1.0, b: 1.0}
     */
    private Map<String, Double> parseInput(String inputJson) {
        Map<String, Double> variables = new HashMap<>();
        
        try {
            JsonArray arr = gson.fromJson(inputJson, JsonArray.class);
            
            // Basit parametre isimlendirmesi: a, b, c, d...
            String[] paramNames = {"a", "b", "c", "d", "e", "f", "g", "h"};
            
            for (int i = 0; i < arr.size() && i < paramNames.length; i++) {
                JsonElement elem = arr.get(i);
                
                if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isNumber()) {
                    variables.put(paramNames[i], elem.getAsDouble());
                } else if (elem.isJsonArray()) {
                    // Array için şimdilik skip (basit versiyonda)
                    variables.put(paramNames[i], 0.0);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Input parse error: " + e.getMessage());
        }
        
        return variables;
    }
    
    /**
     * Path string'inden koşulları parse eder
     * Örnek: "!(diff > 0) [SUB_LOGIC: diff = (a + b) - (a * b)] && (product < 0) [SUB_LOGIC: product = a * b]"
     * Sadece top-level && 'lere böler, parantez içindeki || aynı koşulun parçasıdır.
     */
    private List<Condition> parseConditions(String pathStr) {
        List<Condition> conditions = new ArrayList<>();

        // "|" sonrası path description kısmını al
        String[] parts = pathStr.split("\\|", 2);
        if (parts.length < 2) {
            return conditions;
        }

        String conditionPart = parts[1];
        // "PATH_TO_ELSE: " gibi prefix'leri temizle
        conditionPart = conditionPart.replaceFirst("^[A-Z_]+:\\s*", "");

        // Yalnızca top-level (parantez dışındaki) && ile böl
        List<String> condStrings = splitOnTopLevelAnd(conditionPart);

        for (String condStr : condStrings) {
            Condition cond = new Condition();

            // SUB_LOGIC var mı kontrol et
            Pattern subLogicPattern = Pattern.compile("\\[SUB_LOGIC:([^\\]]+)\\]");
            Matcher matcher = subLogicPattern.matcher(condStr);

            if (matcher.find()) {
                cond.subLogic = matcher.group(1).trim();
                cond.mainCondition = condStr.substring(0, matcher.start()).trim();
            } else {
                cond.mainCondition = condStr.trim();
            }

            if (!cond.mainCondition.isEmpty()) {
                conditions.add(cond);
            }
        }

        return conditions;
    }

    /**
     * Sadece parantez dışındaki (top-level) && operatörlerine göre böler.
     * Parantez içindeki || ve && dokunulmaz kalır.
     */
    private List<String> splitOnTopLevelAnd(String input) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && input.startsWith("&&", i)) {
                result.add(input.substring(start, i).trim());
                i += 1; // skip second '&'
                start = i + 1;
            }
        }

        if (start < input.length()) {
            result.add(input.substring(start).trim());
        }

        return result;
    }
    
    /**
     * SUB_LOGIC ifadelerini hesaplar ve variables'a ekler
     * Örnek: "diff = (a + b) - (a * b)" → variables.put("diff", hesaplanan_değer)
     */
    private void calculateSubLogic(String subLogic, Map<String, Double> variables) {
        try {
            // "diff = (a + b) - (a * b)" formatı
            String[] parts = subLogic.split("=", 2);
            if (parts.length == 2) {
                String varName = parts[0].trim();
                String expression = parts[1].trim();
                
                // Basit expression evaluation (a, b, +, -, *, / destekli)
                double result = evaluateSimpleExpression(expression, variables);
                variables.put(varName, result);
            }
        } catch (Exception e) {
            System.err.println("SUB_LOGIC calculation error: " + e.getMessage());
        }
    }
    
    /**
     * SUB_LOGIC'i hesaplar ve detaylı açıklama döner
     * Örnek: "diff = (a + b) - (a * b)", {a:2, b:3} → "(2 + 3) - (2 * 3) = -1"
     */
    private String calculateSubLogicWithDetails(String subLogic, Map<String, Double> variables) {
        try {
            String[] parts = subLogic.split("=", 2);
            if (parts.length == 2) {
                String varName = parts[0].trim();
                String expression = parts[1].trim();
                
                // Değişkenleri sayılarla değiştir (hesaplama gösterimi için)
                String substituted = expression;
                for (Map.Entry<String, Double> entry : variables.entrySet()) {
                    String value = formatValue(entry.getValue());
                    substituted = substituted.replaceAll("\\b" + entry.getKey() + "\\b", value);
                }
                
                // Hesapla
                double result = evaluateSimpleExpression(expression, variables);
                variables.put(varName, result);
                
                return substituted + " = " + formatValue(result);
            }
        } catch (Exception e) {
            System.err.println("SUB_LOGIC calculation error: " + e.getMessage());
        }
        return "";
    }
    
    /**
     * Koşulu genişletir: SUB_LOGIC ifadesini koşula dahil eder
     * Örnek: "(diff > 0)", "diff = (a + b) - (a * b)" → "((a + b) - (a * b) > 0)"
     */
    private String expandCondition(String condition, String subLogic) {
        if (subLogic == null || subLogic.isEmpty()) {
            return condition;
        }
        
        try {
            String[] parts = subLogic.split("=", 2);
            if (parts.length == 2) {
                String varName = parts[0].trim();
                String expression = parts[1].trim();
                
                // Koşulda değişkeni ifade ile değiştir
                return condition.replaceAll("\\b" + varName + "\\b", expression);
            }
        } catch (Exception e) {
            // Hata durumunda orijinali döndür
        }
        
        return condition;
    }
    
    /**
     * Koşulu analiz edip açıklama üretir
     * NOT operatörünü normalize eder ve detaylı açıklama üretir
     * 
     * @param expandedCondition SUB_LOGIC ile genişletilmiş koşul: "((a + b) - (a * b) > 0)"
     * @param originalCondition Orijinal koşul: "(diff > 0)"
     * @param calculationDetails Hesaplama detayı: "(2 + 3) - (2 * 3) = -1"
     * @param variables Değişken değerleri
     */
    private String analyzeCondition(String expandedCondition, String originalCondition, 
                                   String calculationDetails, Map<String, Double> variables) {
        StringBuilder analysis = new StringBuilder();
        
        // NOT operatörünü normalize et
        String normalizedCondition = normalizeNegation(expandedCondition);
        
        analysis.append("Condition '").append(normalizedCondition).append("'");
        
        // Hesaplama detayını ekle
        if (!calculationDetails.isEmpty()) {
            analysis.append(" [").append(calculationDetails).append("]");
        }
        
        analysis.append(" - ");

        // OR içeren compound koşulları önce kontrol et
        if (normalizedCondition.contains("||")) {
            analysis.append("At least one of the OR sub-conditions must be satisfied");
        } else if (normalizedCondition.contains("< 0")) {
            analysis.append("Negative value required (needs < 0)");
        } else if (normalizedCondition.contains("> 0")) {
            analysis.append("Positive value required (needs > 0)");
        } else if (normalizedCondition.contains("== 0")) {
            analysis.append("Zero value required (needs == 0)");
        } else if (normalizedCondition.contains("<= 0")) {
            analysis.append("Non-positive value required (needs <= 0)");
        } else if (normalizedCondition.contains(">= 0")) {
            analysis.append("Non-negative value required (needs >= 0)");
        } else if (normalizedCondition.contains("<=")) {
            analysis.append("Value must be less than or equal to threshold");
        } else if (normalizedCondition.contains(">=")) {
            analysis.append("Value must be greater than or equal to threshold");
        } else if (normalizedCondition.contains("<")) {
            analysis.append("Value must be less than threshold");
        } else if (normalizedCondition.contains(">")) {
            analysis.append("Value must be greater than threshold");
        } else if (normalizedCondition.contains("!=")) {
            analysis.append("Values must be different");
        } else if (normalizedCondition.contains("==")) {
            analysis.append("Values must be equal");
        } else {
            analysis.append("Condition must be satisfied");
        }
        
        return analysis.toString();
    }
    
    /**
     * NOT operatörünü normalize eder
     * !(x > 10) → x <= 10
     * !(x < 0) → x >= 0
     * !(x >= 5) → x < 5
     * !(x <= 5) → x > 5
     * !(x == 0) → x != 0
     */
    private String normalizeNegation(String condition) {
        condition = condition.trim();
        
        // NOT operatörü yoksa direkt döndür
        if (!condition.startsWith("!")) {
            return condition;
        }
        
        // ! karakterini kaldır ve parantezleri temizle
        String inner = condition.substring(1).trim();
        if (inner.startsWith("(") && inner.endsWith(")")) {
            inner = inner.substring(1, inner.length() - 1).trim();
        }
        
        // Operatörleri ters çevir
        if (inner.contains(">=")) {
            return inner.replace(">=", "<");
        } else if (inner.contains("<=")) {
            return inner.replace("<=", ">");
        } else if (inner.contains("==")) {
            return inner.replace("==", "!=");
        } else if (inner.contains("!=")) {
            return inner.replace("!=", "==");
        } else if (inner.contains(">")) {
            return inner.replace(">", "<=");
        } else if (inner.contains("<")) {
            return inner.replace("<", ">=");
        }
        
        // Tanınmayan format, orijinali döndür
        return condition;
    }
    
    /**
     * Basit aritmetik expression'ı evaluate eder
     * Sadece +, -, *, / ve parantez destekler
     */
    private double evaluateSimpleExpression(String expr, Map<String, Double> variables) {
        try {
            // Değişkenleri değerleriyle değiştir
            String processed = expr;
            for (Map.Entry<String, Double> entry : variables.entrySet()) {
                // Negatif değerleri parantez içine al
                String value = entry.getValue() < 0 ? 
                    "(" + String.valueOf(entry.getValue()) + ")" : 
                    String.valueOf(entry.getValue());
                processed = processed.replaceAll("\\b" + entry.getKey() + "\\b", value);
            }
            
            // Basit evaluation (parantezleri de handle eder)
            return evalArithmetic(processed);
            
        } catch (Exception e) {
            System.err.println("Expression evaluation error: " + expr + " -> " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Basit aritmetik ifadeyi değerlendirir
     */
    private double evalArithmetic(String expr) {
        expr = expr.replaceAll("\\s+", "").trim();
        if (expr.isEmpty()) {
            return 0.0;
        }
        return evalAddSub(expr);
    }
    
    private double evalAddSub(String expr) {
        int lastOp = -1;
        char op = 0;
        int depth = 0;
        
        // Sağdan sola tara (öncelik sırası için)
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') depth--;
            else if (depth == 0 && (c == '+' || (c == '-' && i > 0 && expr.charAt(i-1) != '(' && expr.charAt(i-1) != '*' && expr.charAt(i-1) != '/' && expr.charAt(i-1) != '+' && expr.charAt(i-1) != '-'))) {
                lastOp = i;
                op = c;
                break;
            }
        }
        
        if (lastOp != -1) {
            double left = evalAddSub(expr.substring(0, lastOp));
            double right = evalMulDiv(expr.substring(lastOp + 1));
            return op == '+' ? left + right : left - right;
        }
        
        return evalMulDiv(expr);
    }
    
    private double evalMulDiv(String expr) {
        int lastOp = -1;
        char op = 0;
        int depth = 0;
        
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') depth--;
            else if (depth == 0 && (c == '*' || c == '/')) {
                lastOp = i;
                op = c;
                break;
            }
        }
        
        if (lastOp != -1) {
            double left = evalMulDiv(expr.substring(0, lastOp));
            double right = evalPrimary(expr.substring(lastOp + 1));
            return op == '*' ? left * right : left / right;
        }
        
        return evalPrimary(expr);
    }
    
    private double evalPrimary(String expr) {
        expr = expr.trim();
        if (expr.isEmpty()) {
            return 0.0;
        }
        
        if (expr.startsWith("(") && expr.endsWith(")")) {
            return evalAddSub(expr.substring(1, expr.length() - 1));
        }
        
        // Negatif sayı kontrolü
        if (expr.startsWith("-")) {
            return -evalPrimary(expr.substring(1));
        }
        
        try {
            return Double.parseDouble(expr);
        } catch (NumberFormatException e) {
            // Array erişimleri ve değişken isimleri için sessizce 0 döndür
            // (Örn: examScores[2, attendanceRates[i, i, length gibi)
            // Bu değerler aslında runtime'da hesaplanıyor, burada sadece analiz yapıyoruz
            if (expr.contains("[") || expr.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                return 0.0;  // Sessizce ignore et
            }
            System.err.println("Parse error: " + expr);
            return 0.0;
        }
    }
    
    private String formatValue(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }
    
    /**
     * Koşul bilgilerini tutan yardımcı sınıf
     */
    private static class Condition {
        String mainCondition;  // Ana koşul: "(diff > 0)"
        String subLogic;       // Alt mantık: "diff = (a + b) - (a * b)"
    }
}
