package dev.ua.ikeepcalm.app;

import dev.ua.ikeepcalm.petri.components.PetriNet;
import dev.ua.ikeepcalm.petri.ServiceCentre;
import dev.ua.ikeepcalm.petri.components.Simulation;
import dev.ua.ikeepcalm.util.Charts;
import dev.ua.ikeepcalm.util.Logger;
import dev.ua.ikeepcalm.util.Statistics;
import lombok.Data;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.ToDoubleFunction;

public class Experiments {

    private static final Path RESULTS = Path.of("results");

    private static final int PILOT_RUNS = 4;
    private static final int VERIFICATION_RUNS = 20;
    private static final double STEP = 1_000;
    private static final double HORIZON = 200_000;
    private static final double DURATION = 100_000;
    private static final double TRANSIENT_ACCURACY = 0.10;
    private static final double RELATIVE_ACCURACY = 0.05;
    private static final int COMPLEX = 2;

    private static final ToDoubleFunction<ServiceCentre.Result> WAITING = ServiceCentre.Result::getWaiting;
    private static final ToDoubleFunction<ServiceCentre.Result> SOJOURN = ServiceCentre.Result::getSojourn;

    static void main() throws IOException {
        Files.createDirectories(RESULTS);
        ServiceCentre base = ServiceCentre.base();
        ServiceCentre heavy = base.copy();
        heavy.setInterarrivalMean(RegressionExperiment.INTERARRIVAL_CENTRE - RegressionExperiment.INTERARRIVAL_HALF_RANGE);
        heavy.setShareOf(COMPLEX, RegressionExperiment.SHARE_CENTRE + RegressionExperiment.SHARE_HALF_RANGE);

        Logger.stage("Протокол подій");
        writeProtocol(base);
        Logger.stage("Перехідний період");
        double warmup = Math.max(
                transientPeriod(base, "base", WAITING, "Середній час очікування, хв"),
                transientPeriod(heavy, "heavy", SOJOURN, "Середній час перебування відвідувача, хв"));
        if (Double.isNaN(warmup)) {
            throw new IllegalStateException(format("Умова (3.1) не виконана за %.0f хв: стаціонарного режиму немає", HORIZON));
        }
        Logger.info("Період розгону для всіх експериментів: T = %.0f хв", warmup);
        Logger.stage("Верифікація");
        verification(base, warmup);
        Logger.stage("Кількість прогонів");
        int runs = replications(base, warmup);
        Logger.stage("Регресійний аналіз");
        regression(base, runs, warmup);
        Logger.stage("Дисперсійний аналіз");
        variance(base, runs, warmup);
        Logger.stage("Конфігурації");
        configurations(base, runs, warmup);
    }

    private static void verification(ServiceCentre base, double warmup) throws IOException {
        Map<String, ServiceCentre> variants = new LinkedHashMap<>();
        variants.put("базові значення", base);
        ServiceCentre equal = base.copy();
        equal.setServiceMeans(new double[][]{{1.3, 5.0, 5.0}, {1.3, 5.0, 5.0}});
        variants.put("однакові для типів часи 1,3 | 5 | 5 хв", equal);
        ServiceCentre simple = base.copy();
        simple.setTypeShares(new double[]{1, 0});
        variants.put("лише прості послуги", simple);
        ServiceCentre complex = base.copy();
        complex.setTypeShares(new double[]{0, 1});
        complex.setInterarrivalMean(4.0);
        variants.put("лише складні послуги, інтервал 4 хв", complex);
        ServiceCentre idle = base.copy();
        idle.setInterarrivalMean(100.0);
        variants.put("інтервал 100 хв (без черг)", idle);

        List<String> rows = new ArrayList<>(List.of("variant;quantity;simulated;halfWidth;theory"));
        for (Map.Entry<String, ServiceCentre> variant : variants.entrySet()) {
            ServiceCentre centre = variant.getValue();
            boolean idleVariant = centre.getInterarrivalMean() > 10;
            double duration = idleVariant ? 20 * DURATION : DURATION;
            List<ServiceCentre.Result> results = ServiceCentre.replicate(centre, VERIFICATION_RUNS, warmup, duration);
            String name = variant.getKey();
            Logger.info("Варіант: %s", name);
            for (int node = 0; node < ServiceCentre.NODES.length; node++) {
                int n = node;
                rows.add(row(name, "U" + (node + 1), results, r -> r.getUtilisation()[n], centre.offeredUtilisation(node)));
                rows.add(row(name, "Wq" + (node + 1), results, r -> r.getQueueWaiting()[n], queueWaitingTheory(centre, node)));
            }
            rows.add(row(name, "throughput", results, ServiceCentre.Result::getThroughput, 1 / centre.getInterarrivalMean()));
            rows.add(row(name, "consultShare", results, ServiceCentre.Result::getConsultShare, consultShareTheory(centre)));
            rows.add(row(name, "returnShare", results, ServiceCentre.Result::getReturnShare, returnShareTheory(centre)));
            for (int type = 1; type <= centre.types(); type++) {
                if (centre.getTypeShares()[type - 1] == 0) {
                    continue;
                }
                int t = type;
                double theory = idleVariant ? centre.serviceTimeWithoutQueues(type) : Double.NaN;
                rows.add(row(name, "V" + type, results, r -> r.getSojournByType().get(t), theory));
            }
        }
        Files.write(RESULTS.resolve("verification.csv"), rows);
    }

    private static String row(String variant, String quantity, List<ServiceCentre.Result> results,
                              ToDoubleFunction<ServiceCentre.Result> response, double theory) {
        double[] values = Statistics.values(results, response);
        return format("%s;%s;%.4f;%.4f;%.4f", variant, quantity, Statistics.mean(values), Statistics.halfWidth(values), theory);
    }

    static double queueWaitingTheory(ServiceCentre centre, int node) {
        double lambda = centre.arrivalRate(node);
        int channels = centre.getChannels()[node];
        double firstMoment = 0;
        double secondMoment = 0;
        double commonMean = Double.NaN;
        boolean equal = true;
        for (int type = 1; type <= centre.types(); type++) {
            double weight = centre.getTypeShares()[type - 1] * centre.visits(type)[node] / centre.getInterarrivalMean() / lambda;
            if (weight == 0) {
                continue;
            }
            double mean = centre.getServiceMeans()[type - 1][node];
            firstMoment += weight * mean;
            secondMoment += weight * 2 * mean * mean;
            equal &= Double.isNaN(commonMean) || mean == commonMean;
            commonMean = mean;
        }
        if (node == ServiceCentre.REGISTRATION && channels == 1) {
            return lambda * secondMoment / (2 * (1 - lambda * firstMoment));
        }
        return equal ? erlangCWaiting(lambda, firstMoment, channels) : Double.NaN;
    }

    static double erlangCWaiting(double lambda, double mean, int channels) {
        double load = lambda * mean;
        double sum = 0;
        double term = 1;
        for (int k = 0; k < channels; k++) {
            sum += term;
            term *= load / (k + 1);
        }
        double busy = term / (1 - load / channels);
        return busy / (sum + busy) * mean / (channels - load);
    }

    private static double consultShareTheory(ServiceCentre centre) {
        double share = 0;
        for (int type = 1; type <= centre.types(); type++) {
            share += centre.getTypeShares()[type - 1] * centre.getConsultProbability()[type - 1];
        }
        return share;
    }

    private static double returnShareTheory(ServiceCentre centre) {
        double returned = 0;
        double visits = 0;
        for (int type = 1; type <= centre.types(); type++) {
            double windowVisits = centre.getTypeShares()[type - 1] * centre.visits(type)[ServiceCentre.PROCESSING];
            returned += windowVisits * centre.getReturnProbability()[type - 1];
            visits += windowVisits;
        }
        return returned / visits;
    }

    private static void configurations(ServiceCentre base, int runs, double warmup) throws IOException {
        List<String> rows = new ArrayList<>(List.of("channels;W;W_hw;V;V_hw;throughput;U1;U2;U3;V1;V2;W1;W2"));
        for (int[] channels : new int[][]{{1, 2, 4}, {1, 2, 5}, {2, 2, 4}, {2, 2, 5}}) {
            ServiceCentre centre = base.copy();
            centre.setChannels(channels);
            List<ServiceCentre.Result> results = ServiceCentre.replicate(centre, runs, warmup, DURATION);
            double[] waiting = Statistics.values(results, WAITING);
            double[] sojourn = Statistics.values(results, SOJOURN);
            StringBuilder row = new StringBuilder(format("%s;%.4f;%.4f;%.4f;%.4f;%.4f",
                    Arrays.toString(channels), Statistics.mean(waiting), Statistics.halfWidth(waiting),
                    Statistics.mean(sojourn), Statistics.halfWidth(sojourn),
                    Statistics.mean(Statistics.values(results, ServiceCentre.Result::getThroughput))));
            for (int node = 0; node < ServiceCentre.NODES.length; node++) {
                int n = node;
                row.append(format(";%.4f", Statistics.mean(Statistics.values(results, r -> r.getUtilisation()[n]))));
            }
            for (int type = 1; type <= centre.types(); type++) {
                int t = type;
                row.append(format(";%.4f", Statistics.mean(Statistics.values(results, r -> r.getSojournByType().get(t)))));
            }
            for (int type = 1; type <= centre.types(); type++) {
                int t = type;
                row.append(format(";%.4f", Statistics.mean(Statistics.values(results, r -> r.getWaitingByType().get(t)))));
            }
            rows.add(row.toString());
            Logger.info("Конфігурація %s", row);
        }
        Files.write(RESULTS.resolve("configurations.csv"), rows);
    }

    private static void writeProtocol(ServiceCentre centre) throws IOException {
        PetriNet net = ServiceCentre.build(centre);
        Simulation simulation = new Simulation(net, 1);
        try (PrintWriter protocol = new PrintWriter(Files.newBufferedWriter(RESULTS.resolve("protocol.txt")))) {
            simulation.setProtocol(protocol);
            simulation.run(15.0);
        }
    }

    private static double transientPeriod(ServiceCentre centre, String variant,
                                          ToDoubleFunction<ServiceCentre.Result> response, String yTitle) throws IOException {
        List<ServiceCentre.Result[]> traces = ServiceCentre.trace(centre, PILOT_RUNS, STEP, HORIZON);
        List<String> rows = new ArrayList<>(List.of("seed;t;W;V;U1;U2;U3"));
        Map<String, double[][]> curves = new LinkedHashMap<>();
        for (int run = 0; run < traces.size(); run++) {
            ServiceCentre.Result[] trace = traces.get(run);
            double[][] series = new double[2][trace.length];
            for (int k = 0; k < trace.length; k++) {
                ServiceCentre.Result r = trace[k];
                series[0][k] = (k + 1) * STEP / 1000;
                series[1][k] = response.applyAsDouble(r);
                double[] u = r.getUtilisation();
                rows.add(format("%d;%.0f;%.4f;%.4f;%.4f;%.4f;%.4f",
                        run + 1, (k + 1) * STEP, r.getWaiting(), r.getSojourn(), u[0], u[1], u[2]));
            }
            curves.put("Прогін " + (run + 1), series);
        }
        Files.write(RESULTS.resolve("transient-" + variant + ".csv"), rows);
        Charts.save(Charts.line("Час моделювання, тис. хв", yTitle, "#", 0.0, HORIZON / 1000 + 10, curves, Map.of()),
                RESULTS.resolve("transient-" + variant + ".png"));

        double last = traces.stream().mapToDouble(trace -> response.applyAsDouble(trace[trace.length - 1])).average().orElseThrow();
        double accuracy = TRANSIENT_ACCURACY * last;
        double settling = settlingTime(traces, response, accuracy);
        Logger.info("Перехідний період (%s): y(T=%.0f) = %.3f хв, eps = %.3f хв, T = %.0f хв",
                variant, HORIZON, last, accuracy, settling);
        return settling;
    }

    private static int replications(ServiceCentre centre, double warmup) throws IOException {
        List<ServiceCentre.Result> pilot = ServiceCentre.replicate(centre, 20, warmup, DURATION);
        int runs = 20;
        List<String> rows = new ArrayList<>(List.of("response;mean;deviation;accuracy;runs"));
        for (Map.Entry<String, ToDoubleFunction<ServiceCentre.Result>> response : List.of(Map.entry("W", WAITING), Map.entry("V", SOJOURN))) {
            double[] values = Statistics.values(pilot, response.getValue());
            double mean = Statistics.mean(values);
            double deviation = Statistics.deviation(values);
            int required = Statistics.requiredRuns(deviation, RELATIVE_ACCURACY * mean);
            runs = Math.max(runs, required);
            rows.add(format("%s;%.4f;%.4f;%.4f;%d", response.getKey(), mean, deviation, RELATIVE_ACCURACY * mean, required));
            Logger.info("%s: середнє = %.3f ± %.3f хв, sigma = %.3f, eps = %.3f, p = %d",
                    response.getKey(), mean, Statistics.halfWidth(values), deviation, RELATIVE_ACCURACY * mean, required);
        }
        Files.write(RESULTS.resolve("replications.csv"), rows);
        return runs;
    }

    private static void regression(ServiceCentre centre, int runs, double warmup) throws IOException {
        RegressionResult result = RegressionExperiment.run(centre, COMPLEX, runs, warmup, DURATION, SOJOURN);
        List<String> rows = new ArrayList<>(List.of("x1;x2;interarrival;shareComplex;V;halfWidth;predicted"));
        Map<String, double[][]> curves = new LinkedHashMap<>();
        Map<String, double[][]> observed = new LinkedHashMap<>();
        for (double[] point : result.getPoints()) {
            rows.add(format("%.0f;%.0f;%.3f;%.3f;%.4f;%.4f;%.4f", point[0], point[1],
                    RegressionExperiment.INTERARRIVAL_CENTRE + point[0] * RegressionExperiment.INTERARRIVAL_HALF_RANGE,
                    RegressionExperiment.SHARE_CENTRE + point[1] * RegressionExperiment.SHARE_HALF_RANGE,
                    point[2], point[3], result.predict(point[0], point[1])));
        }
        for (double x2 : new double[]{-1, 0, 1}) {
            double share = RegressionExperiment.SHARE_CENTRE + x2 * RegressionExperiment.SHARE_HALF_RANGE;
            String label = String.format(Locale.forLanguageTag("uk"), "частка складних послуг = %.1f", share);
            curves.put("Регресія, " + label, curve(result, x2));
            observed.put("Імітація, " + label, observedAt(result, x2));
        }
        Files.write(RESULTS.resolve("regression.csv"), rows);
        Charts.save(Charts.line("Середній інтервал надходження, хв", "Середній час перебування відвідувача, хв",
                "0.00", null, null, curves, observed), RESULTS.resolve("regression.png"));
        double[] b = result.getCoefficients();
        Logger.info("Регресія V(x1, x2) = %.3f %+.3f*x1 %+.3f*x2 %+.3f*x1*x2 %+.3f*x1^2 %+.3f*x2^2, R = %.4f",
                b[0], b[1], b[2], b[3], b[4], b[5], result.getCorrelationIndex());
    }

    private static double[][] curve(RegressionResult result, double x2) {
        double[][] points = new double[2][21];
        for (int i = 0; i <= 20; i++) {
            double x1 = -1 + i * 0.1;
            points[0][i] = RegressionExperiment.INTERARRIVAL_CENTRE + x1 * RegressionExperiment.INTERARRIVAL_HALF_RANGE;
            points[1][i] = result.predict(x1, x2);
        }
        return points;
    }

    private static double[][] observedAt(RegressionResult result, double x2) {
        double[][] points = Arrays.stream(result.getPoints()).filter(point -> point[1] == x2).toArray(double[][]::new);
        double[][] xy = new double[2][points.length];
        for (int i = 0; i < points.length; i++) {
            xy[0][i] = RegressionExperiment.INTERARRIVAL_CENTRE + points[i][0] * RegressionExperiment.INTERARRIVAL_HALF_RANGE;
            xy[1][i] = points[i][2];
        }
        return xy;
    }

    private static void variance(ServiceCentre base, int runs, double warmup) throws IOException {
        Map<String, ServiceCentre> configurations = new LinkedHashMap<>();
        for (int windows : new int[]{4, 5, 6}) {
            ServiceCentre centre = base.copy();
            centre.getChannels()[ServiceCentre.PROCESSING] = windows;
            configurations.put("вікон: " + windows, centre);
        }
        VarianceResult result = VarianceAnalysis.run(configurations, runs, warmup, DURATION, WAITING);
        List<String> rows = new ArrayList<>(List.of("level;mean;halfWidth"));
        for (int j = 0; j < result.getLevels().size(); j++) {
            double[] sample = result.getSamples()[j];
            rows.add(format("%s;%.4f;%.4f", result.getLevels().get(j), Statistics.mean(sample), Statistics.halfWidth(sample)));
            Logger.info("Рівень %s: W = %.3f ± %.3f хв",
                    result.getLevels().get(j), Statistics.mean(sample), Statistics.halfWidth(sample));
        }
        rows.add(format("Df=%.4f;Dr=%.4f;F=%.3f;Fcrit=%.3f", result.getFactorVariance(), result.getResidualVariance(),
                result.getFisher(), result.getCritical()));
        Files.write(RESULTS.resolve("anova.csv"), rows);
        Logger.info("Дисперсійний аналіз: F = %.3f, Fкр = %.3f, вплив %s",
                result.getFisher(), result.getCritical(), result.significant() ? "значущий" : "незначущий");
    }

    private static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }

    private static double settlingTime(List<ServiceCentre.Result[]> traces, ToDoubleFunction<ServiceCentre.Result> response,
                                       double accuracy) {
        int points = traces.getFirst().length;
        int settled = points;
        for (int k = points - 1; k >= 0 && spread(traces, response, k) < accuracy; k--) {
            settled = k;
        }
        return settled == points ? Double.NaN : (settled + 1) * STEP;
    }

    private static double spread(List<ServiceCentre.Result[]> traces, ToDoubleFunction<ServiceCentre.Result> response, int point) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (ServiceCentre.Result[] trace : traces) {
            double value = response.applyAsDouble(trace[point]);
            if (Double.isNaN(value)) {
                return Double.POSITIVE_INFINITY;
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min;
    }

    public static class RegressionExperiment {
        public static final double INTERARRIVAL_CENTRE = 2.0;
        public static final double INTERARRIVAL_HALF_RANGE = 0.1;
        public static final double SHARE_CENTRE = 0.3;
        public static final double SHARE_HALF_RANGE = 0.1;
        private static final double[] LEVELS = {-1, 0, 1};

        public static RegressionResult run(ServiceCentre base, int typeWithVariedShare, int runs, double warmup, double duration,
                                           ToDoubleFunction<ServiceCentre.Result> response) {
            double[][] points = new double[LEVELS.length * LEVELS.length][];
            double[][] design = new double[points.length][];
            double[] observed = new double[points.length];
            int i = 0;
            for (double x1 : LEVELS) {
                for (double x2 : LEVELS) {
                    ServiceCentre centre = base.copy();
                    centre.setInterarrivalMean(INTERARRIVAL_CENTRE + x1 * INTERARRIVAL_HALF_RANGE);
                    centre.setShareOf(typeWithVariedShare, SHARE_CENTRE + x2 * SHARE_HALF_RANGE);
                    double[] values = Statistics.values(ServiceCentre.replicate(centre, runs, warmup, duration), response);
                    observed[i] = Statistics.mean(values);
                    points[i] = new double[]{x1, x2, observed[i], Statistics.halfWidth(values)};
                    design[i] = RegressionResult.terms(x1, x2);
                    i++;
                }
            }
            double[] coefficients = Statistics.leastSquares(design, observed);
            double[] predicted = new double[points.length];
            for (int k = 0; k < points.length; k++) {
                predicted[k] = RegressionResult.evaluate(coefficients, points[k][0], points[k][1]);
            }
            return new RegressionResult(points, coefficients, Statistics.correlationIndex(observed, predicted));
        }
    }

    public static class VarianceAnalysis {

        public static VarianceResult run(Map<String, ServiceCentre> configurations, int runs, double warmup, double duration,
                                         ToDoubleFunction<ServiceCentre.Result> response) {
            int q = configurations.size();
            double[][] samples = new double[q][];
            int j = 0;
            for (ServiceCentre centre : configurations.values()) {
                samples[j++] = Statistics.values(ServiceCentre.replicate(centre, runs, warmup, duration), response);
            }
            double[] means = new double[q];
            for (int k = 0; k < q; k++) {
                means[k] = Statistics.mean(samples[k]);
            }
            double grandMean = Statistics.mean(means);
            double factorSum = 0;
            double residualSum = 0;
            for (int k = 0; k < q; k++) {
                factorSum += Math.pow(means[k] - grandMean, 2);
                for (double value : samples[k]) {
                    residualSum += Math.pow(value - means[k], 2);
                }
            }
            double factorVariance = runs * factorSum / (q * runs - 1);
            double residualVariance = residualSum / (q * (runs - 1));
            return new VarianceResult(
                    new ArrayList<>(configurations.keySet()),
                    samples,
                    factorVariance,
                    residualVariance,
                    factorVariance / residualVariance,
                    Statistics.fisherCritical(q - 1, q * (runs - 1)));
        }
    }

    @Data
    public static class RegressionResult {
        private final double[][] points;
        private final double[] coefficients;
        private final double correlationIndex;

        public static double evaluate(double[] coefficients, double x1, double x2) {
            double[] terms = terms(x1, x2);
            double value = 0;
            for (int i = 0; i < terms.length; i++) {
                value += coefficients[i] * terms[i];
            }
            return value;
        }

        public static double[] terms(double x1, double x2) {
            return new double[]{1, x1, x2, x1 * x2, x1 * x1, x2 * x2};
        }

        public double predict(double x1, double x2) {
            return evaluate(coefficients, x1, x2);
        }
    }

    @Data
    public static class VarianceResult {
        private final List<String> levels;
        private final double[][] samples;
        private final double factorVariance;
        private final double residualVariance;
        private final double fisher;
        private final double critical;

        public boolean significant() {
            return fisher > critical;
        }
    }
}
