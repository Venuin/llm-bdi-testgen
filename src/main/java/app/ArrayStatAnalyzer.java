package app;

/**
 * Statistical Array Analyzer
 * 
 * Computes statistics over an integer array: min, max, mean, median,
 * and classifies the distribution shape. Uses multiple loop patterns
 * including nested loops for sorting and single-pass scans.
 * 
 * Benchmark Category: HARD
 * Key Challenge: For-loop entry/skip, nested loop (bubble sort), 
 *                while-loop for median search, and derived conditions
 *                from aggregated loop results.
 */
public class ArrayStatAnalyzer {

    /**
     * Analyzes an integer array and returns a formatted statistics summary.
     *
     * @param values the input array
     * @param trimOutliers if true, removes the single min and max before computing mean
     * @return statistics summary string, or an error message for invalid input
     */
    public String analyze(int[] values, boolean trimOutliers) {
        // ===== INPUT VALIDATION =====
        if (values == null) {
            return "ERROR:NULL_INPUT";
        }

        if (values.length == 0) {
            return "ERROR:EMPTY_ARRAY";
        }

        if (values.length == 1) {
            return "SINGLE:" + values[0];
        }

        // ===== 1. SINGLE-PASS: MIN, MAX, SUM =====
        int min = values[0];
        int max = values[0];
        long sum = 0;

        for (int i = 0; i < values.length; i++) {
            if (values[i] < min) {
                min = values[i];
            }
            if (values[i] > max) {
                max = values[i];
            }
            sum += values[i];
        }

        // ===== 2. MEAN CALCULATION (with optional trimming) =====
        double mean;
        if (trimOutliers && values.length > 2) {
            long trimmedSum = sum - min - max;
            mean = (double) trimmedSum / (values.length - 2);
        } else {
            mean = (double) sum / values.length;
        }

        // ===== 3. SORTING (bubble sort for median) =====
        int[] sorted = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            sorted[i] = values[i];
        }

        for (int i = 0; i < sorted.length - 1; i++) {
            for (int j = 0; j < sorted.length - i - 1; j++) {
                if (sorted[j] > sorted[j + 1]) {
                    int temp = sorted[j];
                    sorted[j] = sorted[j + 1];
                    sorted[j + 1] = temp;
                }
            }
        }

        // ===== 4. MEDIAN =====
        double median;
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 0) {
            median = (sorted[mid - 1] + sorted[mid]) / 2.0;
        } else {
            median = sorted[mid];
        }

        // ===== 5. COUNT NEGATIVES AND ZEROS (while loop) =====
        int negCount = 0;
        int zeroCount = 0;
        int idx = 0;
        while (idx < values.length) {
            if (values[idx] < 0) {
                negCount++;
            } else if (values[idx] == 0) {
                zeroCount++;
            }
            idx++;
        }

        // ===== 6. DISTRIBUTION CLASSIFICATION =====
        String distribution;
        int range = max - min;

        if (range == 0) {
            distribution = "CONSTANT";
        } else if (negCount == values.length) {
            distribution = "ALL_NEGATIVE";
        } else if (negCount == 0 && zeroCount == 0) {
            distribution = "ALL_POSITIVE";
        } else if (mean > median) {
            distribution = "RIGHT_SKEWED";
        } else if (mean < median) {
            distribution = "LEFT_SKEWED";
        } else {
            distribution = "SYMMETRIC";
        }

        // ===== 7. FORMAT RESULT =====
        return String.format("MIN:%d|MAX:%d|MEAN:%.2f|MEDIAN:%.1f|DIST:%s",
                min, max, mean, median, distribution);
    }
}
