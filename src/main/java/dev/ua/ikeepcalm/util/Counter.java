package dev.ua.ikeepcalm.util;

import lombok.Getter;

@Getter
public class Counter {

    private long count;
    private double sum;
    private double sumOfSquares;
    private double min = Double.NaN;
    private double max = Double.NaN;

    public void add(double value) {
        min = count == 0 ? value : Math.min(min, value);
        max = count == 0 ? value : Math.max(max, value);
        count++;
        sum += value;
        sumOfSquares += value * value;
    }

    public double mean() {
        return count == 0 ? Double.NaN : sum / count;
    }

    public double deviation() {
        if (count < 2) {
            return Double.NaN;
        }
        double mean = mean();
        return Math.sqrt(Math.max(0, (sumOfSquares - count * mean * mean) / (count - 1)));
    }
}
