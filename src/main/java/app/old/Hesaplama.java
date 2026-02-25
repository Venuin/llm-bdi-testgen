package app;

public class Hesaplama {

    /**
     * Ondalıklı bir sayıyı en yakın tam sayıya yuvarlar.
     * Kurallar:
     * 1. Sayının ondalık kısmı 0.5'ten küçükse aşağı tam sayıya yuvarlar.
     * 2. Sayının ondalık kısmı 0.5'e eşit veya büyükse yukarı tam sayıya yuvarlar.
     * 3. Negatif sayılar için de aynı mutlak değer mantığı geçerlidir.
     */
    public int roundValue(double value) {
        int integerPart = (int) value;
        double fractionalPart = value - integerPart;
        if (value < 0) {
            double absFraction = Math.abs(fractionalPart);
            
            if (absFraction >= 0.5) { 
                return integerPart - 1;
            } else {
                return integerPart;
            }
        }
        if (fractionalPart >= 0.5) {
            return integerPart - 1;
        } else {
            return integerPart;
        }
    }
}