package dev.ua.ikeepcalm.petri.components;

import dev.ua.ikeepcalm.util.Counter;
import lombok.Getter;
import lombok.Setter;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

@Getter
public class Simulation {

    private final PetriNet net;
    private final Random random;

    private final Map<Integer, Counter> waitingByType = new TreeMap<>();
    private final Map<Integer, Counter> sojournByType = new TreeMap<>();
    private final Map<Integer, Long> createdByType = new TreeMap<>();

    @Setter
    private PrintWriter protocol;

    private double time;
    private boolean started;

    private Counter waiting = new Counter();
    private Counter sojourn = new Counter();

    public Simulation(PetriNet net, long seed) {
        net.validate();
        this.net = net;
        this.random = new Random(seed);
    }

    public void run(double until) {
        if (!started) {
            started = true;
            enterPhase();
        }
        double next;
        while ((next = nextMoment()) <= until) {
            observe(next - time);
            time = next;
            exitPhase();
            enterPhase();
        }
        observe(until - time);
        time = until;
    }

    public void resetStatistics() {
        net.getPlaces().forEach(Place::resetStatistics);
        net.getTransitions().forEach(Transition::resetStatistics);
        waiting = new Counter();
        sojourn = new Counter();
        waitingByType.clear();
        sojournByType.clear();
        createdByType.clear();
    }

    private double nextMoment() {
        double next = Double.POSITIVE_INFINITY;
        for (Transition transition : net.getTransitions()) {
            next = Math.min(next, transition.nextMoment());
        }
        return next;
    }

    private void observe(double interval) {
        net.getPlaces().forEach(place -> place.observe(interval));
        net.getTransitions().forEach(transition -> transition.observe(interval));
    }

    private void exitPhase() {
        List<Map.Entry<Transition, Firing>> due = new ArrayList<>();
        for (Transition transition : net.getTransitions()) {
            while (transition.nextMoment() == time) {
                due.add(Map.entry(transition, Objects.requireNonNull(transition.getChannels().poll())));
            }
        }
        Collections.shuffle(due, random);
        due.forEach(entry -> exit(entry.getKey(), entry.getValue()));
    }

    private void exit(Transition transition, Firing firing) {
        Token token = firing.getToken();
        if (transition.getEmits() > 0) {
            token = new Token(transition.getEmits(), time);
            createdByType.merge(token.getType(), 1L, Long::sum);
        }
        for (Arc arc : transition.getOutputs()) {
            if (arc.getPlace().isTyped()) {
                arc.getPlace().put(token, time);
            } else {
                arc.getPlace().add(arc.getMultiplicity());
            }
        }
        if (token != null && transition.getQueueOutput() == null) {
            leave(token);
        }
        record("ВИХІД", transition, token);
    }

    private void enterPhase() {
        List<Transition> enabled;
        while (!(enabled = enabledWithTopPriority()).isEmpty()) {
            enter(choose(enabled));
        }
    }

    private List<Transition> enabledWithTopPriority() {
        List<Transition> enabled = new ArrayList<>();
        int top = Integer.MIN_VALUE;
        for (Transition transition : net.getTransitions()) {
            if (!transition.enabled()) {
                continue;
            }
            if (transition.getPriority() > top) {
                top = transition.getPriority();
                enabled.clear();
            }
            if (transition.getPriority() == top) {
                enabled.add(transition);
            }
        }
        return enabled;
    }

    private Transition choose(List<Transition> candidates) {
        double total = candidates.stream().mapToDouble(Transition::weight).sum();
        double point = random.nextDouble() * total;
        for (Transition candidate : candidates) {
            point -= candidate.weight();
            if (point < 0) {
                return candidate;
            }
        }
        return candidates.getLast();
    }

    private void enter(Transition transition) {
        Token token = null;
        for (Arc arc : transition.getInputs()) {
            if (arc.getPlace().isTyped()) {
                token = arc.getPlace().take(time);
            } else {
                arc.getPlace().remove(arc.getMultiplicity());
            }
        }
        int type = token == null ? transition.getEmits() : token.getType();
        double moment = time + transition.delayFor(type).sample(random);
        transition.getChannels().add(new Firing(moment, token));
        transition.setFirings(transition.getFirings() + 1);
        record("ВХІД", transition, token);
    }

    private void leave(Token token) {
        waiting.add(token.getWaited());
        sojourn.add(time - token.getCreatedAt());
        waitingByType.computeIfAbsent(token.getType(), type -> new Counter()).add(token.getWaited());
        sojournByType.computeIfAbsent(token.getType(), type -> new Counter()).add(time - token.getCreatedAt());
    }

    private void record(String event, Transition transition, Token token) {
        if (protocol == null) {
            return;
        }
        String marking = net.getPlaces().stream().map(Place::toString).collect(Collectors.joining(" "));
        String moments = transition.getChannels().stream()
                .map(Firing::getMoment).sorted()
                .map(moment -> String.format(Locale.ROOT, "%.4f", moment))
                .collect(Collectors.joining(", "));
        protocol.printf(Locale.ROOT, "t=%.4f | %s | %s | тип %s | M: %s | E: {%s}%n",
                time, event, transition.getName(), token == null ? "-" : token.getType(), marking, moments);
    }
}
