package app;

public class ArrayStatAnalyzer2 {

    public String analyze(int[] values, boolean trimOutliers) {
        if (values == null) {
            return "ERROR:NULL_INPUT";
        }

        if (values.length == 0) {
            return "ERROR:EMPTY_ARRAY";
        }

        if (values.length == 1) {
            return "SINGLE:" + values[0];
        }

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

        double mean;
        if (trimOutliers && values.length > 2) {
            long trimmedSum = sum - min - max;
            mean = (double) trimmedSum / values.length;
        } else {
            mean = (double) sum / values.length;
        }

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

        double median;
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 0) {
            median = (sorted[mid] + sorted[mid + 1]) / 2.0;
        } else {
            median = sorted[mid];
        }

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

        return String.format("MIN:%d|MAX:%d|MEAN:%.2f|MEDIAN:%.1f|DIST:%s",
                min, max, mean, median, distribution);
    }
}
