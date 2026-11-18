package dev.ua.ikeepcalm.petri;

import dev.ua.ikeepcalm.petri.components.PetriNet;
import dev.ua.ikeepcalm.petri.components.Place;
import dev.ua.ikeepcalm.petri.components.Simulation;
import dev.ua.ikeepcalm.util.Counter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceCentreTest {

    @Test
    void utilisationMatchesTrafficEquations() {
        ServiceCentre centre = ServiceCentre.base();
        double[] expected = {0.650, 0.600, 0.717};

        ServiceCentre.Result result = ServiceCentre.run(centre, 3, 5_000, 400_000);

        for (int node = 0; node < expected.length; node++) {
            assertEquals(expected[node], centre.offeredUtilisation(node), 0.001);
            assertEquals(expected[node], result.getUtilisation()[node], 0.01, ServiceCentre.NODES[node]);
        }
        assertEquals(0.5, result.getThroughput(), 0.005);
    }

    @Test
    void routingFollowsTypeDependentProbabilities() {
        ServiceCentre.Result result = ServiceCentre.run(ServiceCentre.base(), 4, 5_000, 400_000);

        assertEquals(0.7 * 0.2 + 0.3 * 0.6, result.getConsultShare(), 0.005);
        assertEquals(0.1325, result.getReturnShare(), 0.005);
    }

    @Test
    void registrationQueueMatchesPollaczekKhinchine() {
        Counter waiting = new Counter();
        for (int seed = 1; seed <= 20; seed++) {
            waiting.add(ServiceCentre.run(ServiceCentre.base(), seed, 5_000, 100_000).getQueueWaiting()[ServiceCentre.REGISTRATION]);
        }
        double halfWidth = 2.093 * waiting.deviation() / Math.sqrt(waiting.getCount());
        assertEquals(0.5 * 3.8 / (2 * 0.35), waiting.mean(), halfWidth);
    }

    @Test
    void withoutQueuesSojournEqualsServiceTimeOverAllVisits() {
        ServiceCentre centre = ServiceCentre.base();
        centre.setInterarrivalMean(100);

        ServiceCentre.Result result = ServiceCentre.run(centre, 5, 0, 5_000_000);

        assertEquals(6.689, centre.serviceTimeWithoutQueues(1), 0.001);
        assertEquals(15.85, centre.serviceTimeWithoutQueues(2), 0.001);
        assertEquals(6.689, result.getSojournByType().get(1), 0.2);
        assertEquals(15.85, result.getSojournByType().get(2), 0.6);
    }

    @Test
    void requestsAreConserved() {
        ServiceCentre centre = ServiceCentre.base();
        PetriNet net = ServiceCentre.build(centre);
        Simulation simulation = new Simulation(net, 6);

        simulation.run(20_000);

        long created = simulation.getCreatedByType().values().stream().mapToLong(Long::longValue).sum();
        long inQueues = net.getPlaces().stream().filter(Place::isTyped).mapToLong(Place::marking).sum();
        long inService = net.getTransitions().stream().filter(t -> t.getEmits() == 0).mapToLong(t -> t.getChannels().size()).sum();
        assertEquals(created, simulation.getSojourn().getCount() + inQueues + inService);
        assertEquals(simulation.getSojourn().getCount(), net.findPlace("N").marking());
    }

    @Test
    void returnProbabilityOfOneIsRejected() {
        ServiceCentre centre = ServiceCentre.base();
        centre.getReturnProbability()[1] = 1.0;

        assertThrows(IllegalArgumentException.class, () -> ServiceCentre.build(centre));
    }
}
