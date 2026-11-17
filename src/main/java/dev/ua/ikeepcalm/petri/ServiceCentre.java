package dev.ua.ikeepcalm.petri;

import dev.ua.ikeepcalm.petri.components.*;
import dev.ua.ikeepcalm.util.Counter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.LongStream;

@Getter
@Setter
@AllArgsConstructor
public class ServiceCentre {

    public static final String[] NODES = {"Адміністратор", "Консультант", "Вікна оформлення"};
    private static final String[] SERVICES = {"Реєстрація", "Консультація", "Оформлення"};

    public static final int REGISTRATION = 0;
    public static final int CONSULTATION = 1;
    public static final int PROCESSING = 2;

    private double interarrivalMean;
    private double[] typeShares;
    private int[] channels;
    private double[][] serviceMeans;
    private double[] consultProbability;
    private double[] returnProbability;

    public static ServiceCentre base() {
        return new ServiceCentre(
                2.0,
                new double[]{0.7, 0.3},
                new int[]{1, 2, 4},
                new double[][]{{1.0, 4.0, 4.0}, {2.0, 6.0, 7.0}},
                new double[]{0.2, 0.6},
                new double[]{0.1, 0.2});
    }

    public static PetriNet build(ServiceCentre centre) {
        centre.validate();
        PetriNet net = new PetriNet();
        Place generator = net.place("G", 1);
        Place registrationQueue = net.queue("Q1");
        Place admins = net.place("D1", centre.getChannels()[REGISTRATION]);
        Place registered = net.queue("R1");
        Place consultationQueue = net.queue("Q2");
        Place consultants = net.place("D2", centre.getChannels()[CONSULTATION]);
        Place processingQueue = net.queue("Q3");
        Place windows = net.place("D3", centre.getChannels()[PROCESSING]);
        Place processed = net.queue("R3");
        Place finished = net.place("N", 0);

        Transition[] services = {
                service(net, SERVICES[REGISTRATION], registrationQueue, admins, registered),
                service(net, SERVICES[CONSULTATION], consultationQueue, consultants, processingQueue),
                service(net, SERVICES[PROCESSING], processingQueue, windows, processed)};
        Transition toConsultant = route(net, "До консультанта", registered, consultationQueue);
        Transition toWindows = route(net, "До вікон", registered, processingQueue);
        Transition back = route(net, "Повернення", processed, consultationQueue);
        Transition exit = route(net, "Вихід", processed, finished);

        for (int type = 1; type <= centre.types(); type++) {
            Transition arrive = net.transition("Надходження типу " + type, Delay.exp(centre.getInterarrivalMean()))
                    .setEmits(type)
                    .setProbability(centre.getTypeShares()[type - 1]);
            net.arc(generator, arrive);
            net.arc(arrive, generator);
            net.arc(arrive, registrationQueue);
            for (int node = 0; node < NODES.length; node++) {
                services[node].delay(type, Delay.exp(centre.getServiceMeans()[type - 1][node]));
            }
            double consult = centre.getConsultProbability()[type - 1];
            double ret = centre.getReturnProbability()[type - 1];
            toConsultant.probability(type, consult);
            toWindows.probability(type, 1 - consult);
            back.probability(type, ret);
            exit.probability(type, 1 - ret);
        }
        return net;
    }

    private static Transition service(PetriNet net, String name, Place queue, Place channels, Place output) {
        Transition transition = net.transition(name, Delay.det(0));
        net.arc(queue, transition);
        net.arc(channels, transition);
        net.arc(transition, channels);
        net.arc(transition, output);
        return transition;
    }

    private static Transition route(PetriNet net, String name, Place from, Place to) {
        Transition transition = net.transition(name, Delay.det(0)).setProbability(0);
        net.arc(from, transition);
        net.arc(transition, to);
        return transition;
    }

    public static Result run(ServiceCentre centre, long seed, double warmup, double duration) {
        PetriNet net = build(centre);
        Simulation simulation = new Simulation(net, seed);
        simulation.run(warmup);
        simulation.resetStatistics();
        simulation.run(warmup + duration);
        return collect(centre, net, simulation, duration);
    }

    public static List<Result> replicate(ServiceCentre centre, int runs, double warmup, double duration) {
        return LongStream.rangeClosed(1, runs).parallel()
                .mapToObj(seed -> run(centre, seed, warmup, duration))
                .toList();
    }

    public static List<Result[]> trace(ServiceCentre centre, int runs, double step, double horizon) {
        return LongStream.rangeClosed(1, runs).parallel()
                .mapToObj(seed -> traceOne(centre, seed, step, horizon))
                .toList();
    }

    private static Result[] traceOne(ServiceCentre centre, long seed, double step, double horizon) {
        PetriNet net = build(centre);
        Simulation simulation = new Simulation(net, seed);
        Result[] trace = new Result[(int) Math.round(horizon / step)];
        for (int k = 0; k < trace.length; k++) {
            double time = (k + 1) * step;
            simulation.run(time);
            trace[k] = collect(centre, net, simulation, time);
        }
        return trace;
    }

    public static Result collect(ServiceCentre centre, PetriNet net, Simulation simulation, double observed) {
        double[] utilisation = new double[NODES.length];
        double[] meanQueue = new double[NODES.length];
        double[] queueWaiting = new double[NODES.length];
        for (int node = 0; node < NODES.length; node++) {
            utilisation[node] = net.findTransition(SERVICES[node]).meanBusy() / centre.getChannels()[node];
            Place queue = net.findPlace("Q" + (node + 1));
            meanQueue[node] = queue.mean();
            queueWaiting[node] = queue.getDwell().mean();
        }
        return new Result(
                simulation.getWaiting().mean(),
                simulation.getSojourn().mean(),
                means(simulation.getWaitingByType()),
                means(simulation.getSojournByType()),
                utilisation,
                meanQueue,
                queueWaiting,
                share(net, "До консультанта", "До вікон"),
                share(net, "Повернення", "Вихід"),
                simulation.getSojourn().getCount() / observed,
                simulation.getSojourn().getCount());
    }

    private static double share(PetriNet net, String chosen, String other) {
        double hits = net.findTransition(chosen).getFirings();
        return hits / (hits + net.findTransition(other).getFirings());
    }

    private static Map<Integer, Double> means(Map<Integer, Counter> tallies) {
        Map<Integer, Double> means = new TreeMap<>();
        tallies.forEach((type, tally) -> means.put(type, tally.mean()));
        return means;
    }

    public ServiceCentre copy() {
        return new ServiceCentre(
                interarrivalMean,
                typeShares.clone(),
                channels.clone(),
                Arrays.stream(serviceMeans).map(double[]::clone).toArray(double[][]::new),
                consultProbability.clone(),
                returnProbability.clone());
    }

    public void setShareOf(int type, double share) {
        double rest = 1 - typeShares[type - 1];
        for (int i = 0; i < typeShares.length; i++) {
            typeShares[i] = i == type - 1 ? share : rest == 0 ? (1 - share) / (typeShares.length - 1) : typeShares[i] * (1 - share) / rest;
        }
    }

    public int types() {
        return typeShares.length;
    }

    public void validate() {
        int types = types();
        if (interarrivalMean <= 0 || channels.length != NODES.length || serviceMeans.length != types
                || consultProbability.length != types || returnProbability.length != types) {
            throw new IllegalArgumentException("Неузгоджені вхідні дані");
        }
        if (Math.abs(Arrays.stream(typeShares).sum() - 1) > 1e-6) {
            throw new IllegalArgumentException("Сума часток типів має дорівнювати 1");
        }
        for (int channel : channels) {
            if (channel < 1) {
                throw new IllegalArgumentException("Кожен вузол потребує щонайменше одного каналу");
            }
        }
        for (int type = 0; type < types; type++) {
            if (typeShares[type] < 0 || serviceMeans[type].length != NODES.length
                    || Arrays.stream(serviceMeans[type]).anyMatch(mean -> mean < 0)) {
                throw new IllegalArgumentException("Тип " + (type + 1) + ": неприпустима частка або час обслуговування");
            }
            if (consultProbability[type] < 0 || consultProbability[type] > 1) {
                throw new IllegalArgumentException("Тип " + (type + 1) + ": імовірність консультації має бути в [0, 1]");
            }
            if (returnProbability[type] < 0 || returnProbability[type] >= 1) {
                throw new IllegalArgumentException("Тип " + (type + 1) + ": імовірність повернення має бути в [0, 1)");
            }
        }
    }

    public double[] visits(int type) {
        double consult = consultProbability[type - 1];
        double ret = returnProbability[type - 1];
        return new double[]{1, consult + ret / (1 - ret), 1 / (1 - ret)};
    }

    public double offeredUtilisation(int node) {
        double work = 0;
        for (int type = 1; type <= types(); type++) {
            work += typeShares[type - 1] * visits(type)[node] * serviceMeans[type - 1][node];
        }
        return work / interarrivalMean / channels[node];
    }

    public double arrivalRate(int node) {
        double rate = 0;
        for (int type = 1; type <= types(); type++) {
            rate += typeShares[type - 1] * visits(type)[node];
        }
        return rate / interarrivalMean;
    }

    public double serviceTimeWithoutQueues(int type) {
        double total = 0;
        for (int node = 0; node < NODES.length; node++) {
            total += visits(type)[node] * serviceMeans[type - 1][node];
        }
        return total;
    }

    @Data
    public static class Result {
        private final double waiting;
        private final double sojourn;
        private final Map<Integer, Double> waitingByType;
        private final Map<Integer, Double> sojournByType;
        private final double[] utilisation;
        private final double[] meanQueue;
        private final double[] queueWaiting;
        private final double consultShare;
        private final double returnShare;
        private final double throughput;
        private final long completed;
    }
}
