package app;

public class PersonCalculator {

    public void calculate(int a, int b) {
        Person p = new Person();
        p.age = a;
        p.height = b;
        
        int sum = p.age + p.height;
        
        if (sum > 100) {
            System.out.println("big");
        }
        else {
            System.out.println("small");
        }
    }
}