package dev.ua.ikeepcalm.petri.components;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Random;

@Data
public class Delay {

    private final DistributionLaw law;
    private final double mean;
    private final double deviation;

    public static Delay det(double value) {
        return new Delay(DistributionLaw.DET, value, 0);
    }

    public static Delay exp(double mean) {
        return new Delay(DistributionLaw.EXP, mean, 0);
    }

    public static Delay unif(double mean, double halfWidth) {
        return new Delay(DistributionLaw.UNIF, mean, halfWidth);
    }

    public static Delay norm(double mean, double deviation) {
        return new Delay(DistributionLaw.NORM, mean, deviation);
    }

    public static Delay erl(double mean, double deviation) {
        return new Delay(DistributionLaw.ERL, mean, deviation);
    }

    public static Delay erlang(double mean, int order) {
        return erl(mean, mean / Math.sqrt(order));
    }

    public double sample(Random random) {
        return law.sample(mean, deviation, random);
    }

    @Override
    public String toString() {
        return switch (law) {
            case DET -> String.valueOf(mean);
            case EXP -> "exp(" + mean + ")";
            default -> law.getNotation() + "(" + mean + ", " + deviation + ")";
        };
    }

    @Getter
    @RequiredArgsConstructor
    public enum DistributionLaw {
        DET("") {
            @Override
            public double sample(double mean, double deviation, Random random) {
                return mean;
            }
        },
        EXP("exp") {
            @Override
            public double sample(double mean, double deviation, Random random) {
                return -mean * Math.log(1 - random.nextDouble());
            }
        },
        UNIF("unif") {
            @Override
            public double sample(double mean, double deviation, Random random) {
                return mean - deviation + 2 * deviation * random.nextDouble();
            }
        },
        NORM("norm") {
            @Override
            public double sample(double mean, double deviation, Random random) {
                double value;
                do {
                    value = mean + deviation * random.nextGaussian();
                } while (value < 0);
                return value;
            }
        },
        ERL("erl") {
            @Override
            public double sample(double mean, double deviation, Random random) {
                int order = Math.max(1, (int) Math.round(mean * mean / (deviation * deviation)));
                double product = 1;
                for (int i = 0; i < order; i++) {
                    product *= 1 - random.nextDouble();
                }
                return -mean / order * Math.log(product);
            }
        };

        private final String notation;

        public abstract double sample(double mean, double deviation, Random random);
    }
}
