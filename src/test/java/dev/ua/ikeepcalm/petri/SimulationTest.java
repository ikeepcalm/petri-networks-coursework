package dev.ua.ikeepcalm.petri;

import dev.ua.ikeepcalm.petri.components.*;
import dev.ua.ikeepcalm.util.Counter;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationTest {
    private static final double EPS = 1e-9;

    static PetriNet singleServer(Delay arrival, Delay service, int servers) {
        PetriNet net = new PetriNet();
        Place generator = net.place("G", 1);
        Place queue = net.queue("Q");
        Place free = net.place("D", servers);
        Place done = net.place("N", 0);
        Transition arrive = net.transition("Надходження", arrival).setEmits(1);
        net.arc(generator, arrive);
        net.arc(arrive, generator);
        net.arc(arrive, queue);
        Transition serve = net.transition("Обробка", service);
        net.arc(queue, serve);
        net.arc(free, serve);
        net.arc(serve, free);
        net.arc(serve, done);
        return net;
    }

    @Test
    void deterministicSingleServerMatchesHandCalculation() {
        PetriNet net = singleServer(Delay.det(2.0), Delay.det(1.0), 1);
        Simulation simulation = new Simulation(net, 1);
        StringWriter log = new StringWriter();
        simulation.setProtocol(new PrintWriter(log));

        simulation.run(10);

        assertEquals(4, net.findPlace("N").marking());
        assertEquals(6, net.findTransition("Надходження").getFirings());
        assertEquals(5, net.findTransition("Обробка").getFirings());
        assertEquals(0.4, net.findTransition("Обробка").meanBusy(), EPS);
        assertEquals(0.6, net.findPlace("D").mean(), EPS);
        assertEquals(0.0, net.findPlace("Q").mean(), EPS);
        assertEquals(1.0, net.findTransition("Надходження").meanBusy(), EPS);
        assertEquals(4, simulation.getSojourn().getCount());
        assertEquals(1.0, simulation.getSojourn().mean(), EPS);
        assertEquals(0.0, simulation.getWaiting().mean(), EPS);
        assertTrue(log.toString().contains("t=2.0000 | ВХІД | Обробка | тип 1"));
    }

    @Test
    void twoTypesWithTypeDependentServiceMatchHandCalculation() {
        PetriNet net = new PetriNet();
        Place queue = net.queue("Q");
        Place free = net.place("D", 1);
        for (int type = 1; type <= 2; type++) {
            Place generator = net.place("G" + type, 1);
            Transition arrive = net.transition("Надходження типу " + type, Delay.det(type == 1 ? 4.0 : 6.0)).setEmits(type);
            net.arc(generator, arrive);
            net.arc(arrive, generator);
            net.arc(arrive, queue);
        }
        Transition serve = net.transition("Обробка", Delay.det(1.0)).delay(2, Delay.det(3.0));
        net.arc(queue, serve);
        net.arc(free, serve);
        net.arc(serve, free);
        Simulation simulation = new Simulation(net, 1);

        simulation.run(11);

        Counter sojourn1 = simulation.getSojournByType().get(1);
        Counter waiting1 = simulation.getWaitingByType().get(1);
        assertEquals(2, sojourn1.getCount());
        assertEquals(1.5, sojourn1.mean(), EPS);
        assertEquals(0.5, waiting1.mean(), EPS);
        assertEquals(1, simulation.getSojournByType().get(2).getCount());
        assertEquals(3.0, simulation.getSojournByType().get(2).mean(), EPS);
        assertEquals(1.0 / 11, net.findPlace("Q").mean(), EPS);
        assertEquals(5.0 / 11, serve.meanBusy(), EPS);
    }

    @Test
    void higherPriorityAlwaysWinsConflict() {
        PetriNet net = new PetriNet();
        Place source = net.place("P", 100);
        Transition high = net.transition("A", Delay.det(1.0)).setPriority(1);
        Transition low = net.transition("B", Delay.det(1.0));
        net.arc(source, high);
        net.arc(source, low);
        net.arc(high, net.place("NA", 0));
        net.arc(low, net.place("NB", 0));

        new Simulation(net, 7).run(5);

        assertEquals(100, high.getFirings());
        assertEquals(0, low.getFirings());
    }

    @Test
    void sameSeedGivesSameRun() {
        assertEquals(runMm1(42), runMm1(42));
    }

    @Test
    void mm1MatchesTheory() {
        Counter sojourn = new Counter();
        for (int seed = 1; seed <= 20; seed++) {
            Simulation simulation = new Simulation(singleServer(Delay.exp(1.25), Delay.exp(1.0), 1), seed);
            simulation.run(1000);
            simulation.resetStatistics();
            simulation.run(21000);
            sojourn.add(simulation.getSojourn().mean());
        }
        double halfWidth = 2.093 * sojourn.deviation() / Math.sqrt(sojourn.getCount());
        assertEquals(5.0, sojourn.mean(), halfWidth);
    }

    @Test
    void mm2MatchesTheory() {
        Counter queue = new Counter();
        for (int seed = 1; seed <= 20; seed++) {
            PetriNet net = singleServer(Delay.exp(1 / 1.5), Delay.exp(1.0), 2);
            Simulation simulation = new Simulation(net, seed);
            simulation.run(1000);
            simulation.resetStatistics();
            simulation.run(21000);
            queue.add(net.findPlace("Q").mean());
        }
        double halfWidth = 2.093 * queue.deviation() / Math.sqrt(queue.getCount());
        assertEquals(27.0 / 14, queue.mean(), halfWidth);
    }

    @Test
    void me21MatchesPollaczekKhinchine() {
        Counter waiting = new Counter();
        for (int seed = 1; seed <= 20; seed++) {
            Simulation simulation = new Simulation(singleServer(Delay.exp(1.25), Delay.erlang(1.0, 2), 1), seed);
            simulation.run(1000);
            simulation.resetStatistics();
            simulation.run(21000);
            waiting.add(simulation.getWaiting().mean());
        }
        double halfWidth = 2.093 * waiting.deviation() / Math.sqrt(waiting.getCount());
        assertEquals(3.0, waiting.mean(), halfWidth);
    }

    @Test
    void typeMixAndTypeDependentBranchingFollowProbabilities() {
        double[] shares = {0.3, 0.5, 0.2};
        PetriNet net = new PetriNet();
        Place generator = net.place("G", 1);
        Place queue = net.queue("Q");
        for (int type = 1; type <= 3; type++) {
            Transition arrive = net.transition("Надходження типу " + type, Delay.exp(1.0))
                    .setEmits(type).setProbability(shares[type - 1]);
            net.arc(generator, arrive);
            net.arc(arrive, generator);
            net.arc(arrive, queue);
        }
        Place left = net.queue("A");
        Place right = net.queue("B");
        Transition toLeft = net.transition("Маршрут A", Delay.det(0)).setProbability(0)
                .probability(1, 0.8).probability(2, 0.1).probability(3, 1.0);
        Transition toRight = net.transition("Маршрут B", Delay.det(0)).setProbability(0)
                .probability(1, 0.2).probability(2, 0.9);
        net.arc(queue, toLeft);
        net.arc(toLeft, left);
        net.arc(queue, toRight);
        net.arc(toRight, right);
        Simulation simulation = new Simulation(net, 11);

        simulation.run(50_000);

        long total = simulation.getCreatedByType().values().stream().mapToLong(Long::longValue).sum();
        for (int type = 1; type <= 3; type++) {
            assertShare(shares[type - 1], simulation.getCreatedByType().get(type), total);
        }
        long leftType1 = countType(left, 1);
        long leftType2 = countType(left, 2);
        assertShare(0.8, leftType1, leftType1 + countType(right, 1));
        assertShare(0.1, leftType2, leftType2 + countType(right, 2));
        assertEquals(0, countType(right, 3));
    }

    @Test
    void tokensAreConservedPerType() {
        PetriNet net = singleServer(Delay.exp(1.25), Delay.exp(1.0), 1);
        Simulation simulation = new Simulation(net, 5);

        simulation.run(10_000);

        long created = simulation.getCreatedByType().get(1);
        long inQueue = net.findPlace("Q").marking();
        long inService = net.findTransition("Обробка").getChannels().size();
        assertEquals(created, simulation.getSojourn().getCount() + inQueue + inService);
        assertEquals(simulation.getSojourn().getCount(), net.findPlace("N").marking());
    }

    @Test
    void littleLawHoldsForSojournMeasuredOnTokens() {
        PetriNet net = singleServer(Delay.exp(1.25), Delay.exp(1.0), 1);
        Simulation simulation = new Simulation(net, 9);
        simulation.run(1000);
        simulation.resetStatistics();
        simulation.run(201_000);

        double inSystem = net.findPlace("Q").mean() + net.findTransition("Обробка").meanBusy();
        double throughput = simulation.getSojourn().getCount() / 200_000.0;
        assertEquals(inSystem, throughput * simulation.getSojourn().mean(), 0.02 * inSystem);
    }

    private static long countType(Place place, int type) {
        return place.getTokens().stream().filter(token -> token.getType() == type).count();
    }

    private static void assertShare(double expected, long hits, long total) {
        double sigma = Math.sqrt(expected * (1 - expected) / total);
        assertEquals(expected, (double) hits / total, 3 * sigma);
    }

    private String runMm1(long seed) {
        PetriNet net = singleServer(Delay.exp(1.25), Delay.exp(1.0), 1);
        Simulation simulation = new Simulation(net, seed);
        simulation.run(500);
        return net.findPlace("Q").mean() + " " + simulation.getSojourn().mean() + " " + net.findPlace("N").marking();
    }
}
