package dev.ua.ikeepcalm.util;

import java.util.List;
import java.util.function.ToDoubleFunction;

public class Statistics {

    private static final double[] STUDENT_975 = {
            12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
            2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
            2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042};

    public static <T> double[] values(List<T> results, ToDoubleFunction<T> response) {
        return results.stream().mapToDouble(response).toArray();
    }

    public static double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    public static double deviation(double[] values) {
        double mean = mean(values);
        double sum = 0;
        for (double value : values) {
            sum += (value - mean) * (value - mean);
        }
        return Math.sqrt(sum / (values.length - 1));
    }

    public static double student(int degrees) {
        if (degrees <= STUDENT_975.length) {
            return STUDENT_975[degrees - 1];
        }
        return degrees <= 60 ? 2.000 : degrees <= 120 ? 1.980 : 1.960;
    }

    public static double halfWidth(double[] values) {
        return student(values.length - 1) * deviation(values) / Math.sqrt(values.length);
    }

    public static int requiredRuns(double deviation, double accuracy) {
        return (int) Math.ceil(20 * Math.pow(deviation / accuracy, 2));
    }

    public static double fisherCritical(int numeratorDegrees, int denominatorDegrees) {
        if (numeratorDegrees != 2) {
            throw new IllegalArgumentException("Критичне значення реалізовано лише для 2 ступенів свободи чисельника");
        }
        return denominatorDegrees / 2.0 * (Math.pow(0.05, -2.0 / denominatorDegrees) - 1);
    }

    public static double[] leastSquares(double[][] design, double[] response) {
        int size = design[0].length;
        double[][] system = new double[size][size + 1];
        for (int row = 0; row < design.length; row++) {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    system[i][j] += design[row][i] * design[row][j];
                }
                system[i][size] += design[row][i] * response[row];
            }
        }
        return solve(system);
    }

    public static double correlationIndex(double[] observed, double[] predicted) {
        double mean = mean(observed);
        double residual = 0;
        double total = 0;
        for (int i = 0; i < observed.length; i++) {
            residual += Math.pow(observed[i] - predicted[i], 2);
            total += Math.pow(observed[i] - mean, 2);
        }
        return Math.sqrt(1 - residual / total);
    }

    private static double[] solve(double[][] system) {
        int size = system.length;
        for (int col = 0; col < size; col++) {
            int pivot = col;
            for (int row = col + 1; row < size; row++) {
                if (Math.abs(system[row][col]) > Math.abs(system[pivot][col])) {
                    pivot = row;
                }
            }
            double[] swap = system[col];
            system[col] = system[pivot];
            system[pivot] = swap;
            for (int row = 0; row < size; row++) {
                if (row != col) {
                    double factor = system[row][col] / system[col][col];
                    for (int k = col; k <= size; k++) {
                        system[row][k] -= factor * system[col][k];
                    }
                }
            }
        }
        double[] solution = new double[size];
        for (int i = 0; i < size; i++) {
            solution[i] = system[i][size] / system[i][i];
        }
        return solution;
    }
}
