package tools;

import cartago.*;
import java.util.regex.*;

/**
 * TestOutputParserArtifact - Test çıktılarını normalize eder ve karşılaştırır
 * Whitespace, newline ve diğer görünmeyen karakter farklılıklarını giderir
 */
@ARTIFACT_INFO(outports = { @OUTPORT(name = "out-1") })
public class TestOutputParserArtifact extends Artifact {

    /**
     * İki string'i normalize ederek karşılaştırır
     * @param expected Beklenen string
     * @param actual Gerçek string
     * @return true ise eşit, false ise farklı
     */
    @OPERATION
    public void compareOutputs(String expected, String actual, OpFeedbackParam<Boolean> resultParam) {
        String normalizedExpected = normalizeOutput(expected);
        String normalizedActual = normalizeOutput(actual);
        
        boolean isEqual = normalizedExpected.equals(normalizedActual);
        resultParam.set(isEqual);
    }
    
    /**
     * Tek bir string'i normalize eder
     * @param output Normalize edilecek string
     * @return Normalize edilmiş string
     */
    @OPERATION
    public void normalizeString(String output, OpFeedbackParam<String> resultParam) {
        resultParam.set(normalizeOutput(output));
    }
    
    /**
     * Test çıktısını normalize eden yardımcı metod:
     * - Tüm line separator'ları \n'e çevirir
     * - Baştaki ve sondaki whitespace'leri siler
     * - Ardışık boşlukları tek boşluğa indirger
     * - Carriage return (\r) karakterlerini temizler
     */
    private String normalizeOutput(String output) {
        if (output == null) {
            return "";
        }
        
        // 1. Tüm line separator tiplerini standart \n'e çevir
        String normalized = output.replace("\r\n", "\n")
                                  .replace("\r", "\n");
        
        // 2. Her satırın başındaki ve sonundaki boşlukları temizle
        String[] lines = normalized.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) {
                result.append(trimmedLine).append("\n");
            }
        }
        
        // 3. Son eklenen \n'i koru ama ekstra \n'leri sil
        normalized = result.toString().trim();
        
        return normalized;
    }
    
    /**
     * Test çıktısından sadece mesajları çıkarır (multi-line output için)
     * @param output Test çıktısı
     * @return Sadece mesajlar (her satır bir liste elemanı olarak)
     */
    @OPERATION
    public void extractMessages(String output, OpFeedbackParam<Object[]> resultParam) {
        String normalized = normalizeOutput(output);
        String[] messages = normalized.split("\n");
        resultParam.set(messages);
    }
    
    /**
     * İki output arasındaki farkı detaylı olarak raporlar
     * @param expected Beklenen
     * @param actual Gerçek
     * @return Fark raporu
     */
    @OPERATION
    public void compareWithDetails(String expected, String actual, OpFeedbackParam<String> resultParam) {
        String normExpected = normalizeOutput(expected);
        String normActual = normalizeOutput(actual);
        
        if (normExpected.equals(normActual)) {
            resultParam.set("MATCH: Outputs are identical after normalization.");
            return;
        }
        
        StringBuilder report = new StringBuilder();
        report.append("MISMATCH DETECTED:\n");
        report.append("─────────────────────────────────────────────\n");
        report.append("Expected (normalized):\n");
        report.append("  \"").append(escapeForDisplay(normExpected)).append("\"\n");
        report.append("─────────────────────────────────────────────\n");
        report.append("Actual (normalized):\n");
        report.append("  \"").append(escapeForDisplay(normActual)).append("\"\n");
        report.append("─────────────────────────────────────────────\n");
        
        // Karakter karakter karşılaştır
        int minLen = Math.min(normExpected.length(), normActual.length());
        int firstDiff = -1;
        for (int i = 0; i < minLen; i++) {
            if (normExpected.charAt(i) != normActual.charAt(i)) {
                firstDiff = i;
                break;
            }
        }
        
        if (firstDiff >= 0) {
            report.append("First difference at position ").append(firstDiff).append(":\n");
            report.append("  Expected: '").append(escapeChar(normExpected.charAt(firstDiff))).append("'\n");
            report.append("  Actual:   '").append(escapeChar(normActual.charAt(firstDiff))).append("'\n");
        } else if (normExpected.length() != normActual.length()) {
            report.append("Length difference:\n");
            report.append("  Expected length: ").append(normExpected.length()).append("\n");
            report.append("  Actual length:   ").append(normActual.length()).append("\n");
        }
        
        resultParam.set(report.toString());
    }
    
    /**
     * Görünmeyen karakterleri escape eder (debug için)
     */
    private String escapeForDisplay(String s) {
        return s.replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    /**
     * Tek bir karakteri escape eder
     */
    private String escapeChar(char c) {
        switch (c) {
            case '\n': return "\\n (newline)";
            case '\r': return "\\r (carriage return)";
            case '\t': return "\\t (tab)";
            case ' ': return "' ' (space)";
            default: return String.valueOf(c);
        }
    }
}
