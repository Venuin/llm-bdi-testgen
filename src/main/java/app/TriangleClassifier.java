package app;

/**
 * Triangle Classification Algorithm
 * 
 * Classifies triangles based on three side lengths.
 * Covers: input validation, equality checks, comparison chains,
 * Pythagorean theorem verification, and composite classifications.
 * 
 * Benchmark Category: MEDIUM
 * Key Challenge: Multiple interacting boolean conditions and mathematical properties
 */
public class TriangleClassifier {

    /**
     * Classifies a triangle given three side lengths.
     *
     * @param a first side length
     * @param b second side length
     * @param c third side length
     * @return classification string describing the triangle type
     */
    public String classify(int a, int b, int c) {
        // Step 1: Validate positive sides
        if (a <= 0 || b <= 0 || c <= 0) {
            return "INVALID_NEGATIVE";
        }

        // Step 2: Triangle inequality (strict)
        if (a + b < c || a + c < b || b + c < a) {
            return "NOT_A_TRIANGLE";
        }

        // Step 3: Degenerate triangle (boundary case)
        if (a + b == c || a + c == b || b + c == a) {
            return "DEGENERATE";
        }

        // Step 4: Equilateral check
        if (a == b && b == c) {
            return "EQUILATERAL";
        }

        // Step 5: Compute squares for right-angle check
        long a2 = (long) a * a;
        long b2 = (long) b * b;
        long c2 = (long) c * c;

        boolean isRight = (a2 + b2 == c2) || (a2 + c2 == b2) || (b2 + c2 == a2);

        // Step 6: Isosceles check
        boolean isIsosceles = (a == b) || (b == c) || (a == c);

        // Step 7: Composite classification
        if (isRight && isIsosceles) {
            return "RIGHT_ISOSCELES";
        } else if (isRight) {
            return "RIGHT_SCALENE";
        } else if (isIsosceles) {
            return "ISOSCELES";
        } else {
            return "SCALENE";
        }
    }
}
