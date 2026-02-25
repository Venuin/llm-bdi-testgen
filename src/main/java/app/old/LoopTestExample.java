package app;

public class LoopTestExample {

    public int processLoops(int limit, int startValue) {
        int total = 0;

        // --- TEST 1: FOR DÖNGÜSÜ ---
        // Strateji A (Enter): limit > 0 olmalı
        // Strateji B (Skip) : limit <= 0 olmalı
        for (int i = 0; i < limit; i++) {
            total += i; 
        }

        // Araya bir mantık ekleyelim (Opsiyonel)
        if (total > 100) {
            total = 100;
        }

        // --- TEST 2: WHILE DÖNGÜSÜ ---
        // Strateji A (Enter): startValue > 10 olmalı
        // Strateji B (Skip) : startValue <= 10 olmalı
        while (startValue > 10) {
            total += startValue;
            startValue -= 5; // Sonsuz döngüyü engellemek için azaltıyoruz
        }

        return total;
    }
}