package app;

public class BuggyCalculator {
    
    /**
     * Öğrenci notlarını değerlendirir ve sonucu yazdırır.
     * 
     * @param exam1 İlk sınav notu (0-100)
     * @param exam2 İkinci sınav notu (0-100)
     * @param exam3 Üçüncü sınav notu (0-100)
     */
    public void evaluateStudent(int exam1, int exam2, int exam3) {
        // Adım 1: Ortalama hesapla
        double average = (exam1 + exam2 + exam3) / 3.0;
        
        // Adım 2: Özel durum - Mükemmellik kontrolü
        if (exam1 == 100 && exam2 == 100 && exam3 == 100) {
            System.out.println("MUKEMMEL");
            return;
        }
        
        // Adım 3: Geçme/Kalma durumu kontrolü
        if (average >= 50) {
            System.out.println("GECTI");
        } else if (average >= 40) {  
            System.out.println("SARTLI GECTI");
        } else {
            System.out.println("KALDI");
        }
        
        // Adım 4: Ek kontrol - Negatif not uyarısı
        if (exam1 < 0 || exam2 < 0 || exam3 < 0) {
            System.out.println("UYARI: Negatif not girildi!");
        }
    }
}
