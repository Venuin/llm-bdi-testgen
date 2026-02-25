package tools;

import cartago.*;
import java.util.*;

public class JsonTool extends Artifact {
    // Mevcut parseJsonList metodu aynı kalsın...
    @OPERATION
    public void parseJsonList(String rawJson, OpFeedbackParam<Object[]> resultList) {
        String cleaned = rawJson.replaceAll("```json|```|\\[|\\]", "").trim();
        if (cleaned.isEmpty()) {
            resultList.set(new Object[0]);
            return;
        }
        String[] parts = cleaned.split("\",\\s*\""); 
        List<String> finalPaths = new ArrayList<>();
        for (String p : parts) {
            finalPaths.add(p.replace("\"", "").trim());
        }
        resultList.set(finalPaths.toArray());
    }

    @OPERATION
    public void parseMapEntry(String entry, OpFeedbackParam<Integer> line, OpFeedbackParam<String> path) {
        try {
            String[] parts = entry.split("\\|", 2);
            line.set(Integer.parseInt(parts[0]));
            path.set(parts[1]);
        } catch (Exception e) {
            failed("Entry parse error: " + entry);
        }
    }

    // YENİ: LLM yanıtlarını temizlemek için (Tırnak ve Markdown temizleyici)
    // JsonTool.java içindeki sanitizeLLMResponse operasyonuna ekle
    @OPERATION
    public void sanitizeLLMResponse(String raw, OpFeedbackParam<String> cleaned) {
        // 1. Unicode eksi işaretini (\u2212) standart tire (-) ile değiştir
        // 2. SADECE Markdown bloklarını temizle (``` işaretleri)
        // 3. ÖNEMLE: String literaller içindeki tırnakları KALDIRMA!
        String result = raw.replace('\u2212', '-')
                        .replaceAll("```[a-z]*", "")  // Başlangıç markdown
                        .replaceAll("```", "")        // Bitiş markdown
                        .trim();
        cleaned.set(result);
    }

    // YENİ: String'i integer'a çevir (Oracle skorlama için)
    @OPERATION
    public void parseStringToInt(String str, OpFeedbackParam<Integer> result) {
        try {
            // Sadece ilk sayıyı bul ve parse et
            String cleaned = str.replaceAll("[^0-9-]", "").trim();
            if (cleaned.isEmpty()) {
                result.set(50); // Varsayılan
            } else {
                int value = Integer.parseInt(cleaned);
                // 0-100 aralığında sınırla
                if (value < 0) value = 0;
                if (value > 100) value = 100;
                result.set(value);
            }
        } catch (NumberFormatException e) {
            result.set(50); // Hata durumunda varsayılan
        }
    }
}