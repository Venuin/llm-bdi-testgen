package app;

public class TriangleClassifier2 {

    public String classify(int a, int b, int c) {
        if (a <= 0 || b <= 0 || c <= 0) {
            return "INVALID_NEGATIVE";
        }

        if (a + b <= c || a + c <= b || b + c <= a) {
            return "NOT_A_TRIANGLE";
        }

        if (a + b == c || a + c == b || b + c == a) {
            return "DEGENERATE";
        }

        if (a == b && b == c) {
            return "EQUILATERAL";
        }

        long a2 = (long) a * a;
        long b2 = (long) b * b;
        long c2 = (long) c * c;

        boolean isRight = (a2 + b2 == c2) || (a2 + c2 == b2) || (b2 + c2 == a2);

        boolean isIsosceles = (a == b) || (b == c) || (a == c);

        if (isRight && isIsosceles) {
            return "RIGHT_ISOSCELES";
        } else if (isIsosceles) {
            return "ISOSCELES";
        } else if (isRight) {
            return "RIGHT_SCALENE";
        } else {
            return "SCALENE";
        }
    }
}
