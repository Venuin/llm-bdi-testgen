package app;

public class ComplexExample {
    
    /**
     * Karmaşık sub-logic örneği
     * İki parametre alır, birden fazla ara değişken kullanır
     */
    public String categorize(int a, int b) {
        int sum = a + b;      
       
        int diff = a - b; 
        
        if (diff > 0) {
            if (sum > 0) {
                return "High Positive";
            } else {
                return "High Negative";
            }
        } else {
            if (sum < 0) {
                return "Low Negative";
            } else {
                return "Low Positive";
            }
        }
    }
}
