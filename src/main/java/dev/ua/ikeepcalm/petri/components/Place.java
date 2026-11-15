package dev.ua.ikeepcalm.petri.components;

import dev.ua.ikeepcalm.util.Counter;
import lombok.Getter;

import java.util.ArrayDeque;
import java.util.Deque;

@Getter
public class Place {

    private final String name;
    private final boolean typed;
    private final Deque<Token> tokens = new ArrayDeque<>();
    private int count;
    private double area;
    private double observed;
    private int min;
    private int max;
    private Counter dwell = new Counter();

    public Place(String name, boolean typed, int marking) {
        this.name = name;
        this.typed = typed;
        this.count = marking;
    }

    public int marking() {
        return typed ? tokens.size() : count;
    }

    public Token head() {
        return tokens.peekFirst();
    }

    public void add(int multiplicity) {
        count += multiplicity;
    }

    public void remove(int multiplicity) {
        count -= multiplicity;
    }

    public void put(Token token, double time) {
        token.setPlacedAt(time);
        tokens.addLast(token);
    }

    public Token take(double time) {
        Token token = tokens.pollFirst();
        double stay = time - token.getPlacedAt();
        token.setWaited(token.getWaited() + stay);
        dwell.add(stay);
        return token;
    }

    public void observe(double interval) {
        if (interval <= 0) {
            return;
        }
        int marking = marking();
        min = observed == 0 ? marking : Math.min(min, marking);
        max = observed == 0 ? marking : Math.max(max, marking);
        area += marking * interval;
        observed += interval;
    }

    public double mean() {
        return observed == 0 ? marking() : area / observed;
    }

    public void resetStatistics() {
        area = 0;
        observed = 0;
        dwell = new Counter();
    }

    @Override
    public String toString() {
        return name + "=" + marking();
    }
}
