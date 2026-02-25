package tools;

import cartago.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@ARTIFACT_INFO(outports = { @OUTPORT(name = "out-1") })
public class FileTool extends Artifact {

    // Projenin kök dizininden dosya yolunu bulmak için prefix
    private static final String SOURCE_DIR = "src/main/java/app/";

    @OPERATION
    public void readSourceCode(String fileName, OpFeedbackParam<String> content) {
        try {
            Path path = Paths.get(SOURCE_DIR + fileName + ".java");
            
            if (Files.exists(path)) {
                // Dosyayı oku
                List<String> allLines = Files.readAllLines(path);
                StringBuilder fullContent = new StringBuilder();
                
                System.out.println("\n--- SOURCE CODE PREVIEW (" + fileName + ".java) ---");
                for (int i = 0; i < allLines.size(); i++) {
                    String line = allLines.get(i);
                    // Satır numarası + Kod
                    // i+1 çünkü loglarda satır numaraları 1'den başlar.
                    System.out.printf("%2d: %s%n", (i + 1), line);
                    fullContent.append(line).append("\n");
                }
                System.out.println("----------------------------------------------\n");

                content.set(fullContent.toString());
                System.out.println("Dosya okundu: " + fileName);
            } else {
                failed("Dosya bulunamadi: " + path.toAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
            failed("Dosya okuma hatasi: " + e.getMessage());
        }
    }
}