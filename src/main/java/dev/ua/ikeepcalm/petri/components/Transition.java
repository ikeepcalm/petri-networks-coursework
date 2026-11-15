package dev.ua.ikeepcalm.petri.components;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.*;

@Getter
@Setter
@Accessors(chain = true)
public class Transition {
    private final String name;
    private final Map<Integer, Delay> delayByType = new HashMap<>();
    private final Map<Integer, Double> probabilityByType = new HashMap<>();
    private final List<Arc> inputs = new ArrayList<>();
    private final List<Arc> outputs = new ArrayList<>();
    private final PriorityQueue<Firing> channels = new PriorityQueue<>(Comparator.comparingDouble(Firing::getMoment));
    private Delay delay;
    private int priority;
    private double probability = 1.0;
    private int emits;
    private Place queueInput;
    private Place queueOutput;
    private double busyArea;
    private double observed;
    private long firings;

    public Transition(String name, Delay delay) {
        this.name = name;
        this.delay = delay;
    }

    public Transition delay(int type, Delay typeDelay) {
        delayByType.put(type, typeDelay);
        return this;
    }

    public Transition probability(int type, double typeProbability) {
        probabilityByType.put(type, typeProbability);
        return this;
    }

    public Delay delayFor(int type) {
        return delayByType.getOrDefault(type, delay);
    }

    public int headType() {
        if (queueInput == null) {
            return emits;
        }
        return queueInput.marking() == 0 ? 0 : queueInput.head().getType();
    }

    public double weight() {
        return probabilityByType.getOrDefault(headType(), probability);
    }

    public boolean enabled() {
        for (Arc arc : inputs) {
            if (arc.getPlace().marking() < arc.getMultiplicity()) {
                return false;
            }
        }
        return weight() > 0;
    }

    public double nextMoment() {
        return channels.isEmpty() ? Double.POSITIVE_INFINITY : channels.peek().getMoment();
    }

    public void observe(double interval) {
        busyArea += channels.size() * interval;
        observed += interval;
    }

    public double meanBusy() {
        return observed == 0 ? channels.size() : busyArea / observed;
    }

    public void resetStatistics() {
        busyArea = 0;
        observed = 0;
        firings = 0;
    }
}
