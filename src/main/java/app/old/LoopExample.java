package app;

public class LoopExample {
    
    /**
     * Loop ile sub-logic örneği
     */
    public int calculate(int n, int multiplier) {
        int sum = 0;
        int product = n * multiplier;  // product depends on both inputs
        
        // Bu loop condition'ı product'a bağlı
        for (int i = 0; i < product; i++) {
            sum += i;
            
            // İç koşul: sum'ı kontrol ediyor
            if (sum > 100) {
                break;
            }
        }
        
        return sum;
    }
}
