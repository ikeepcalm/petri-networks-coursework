package dev.ua.ikeepcalm.petri.components;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Token {
    private final int type;
    private final double createdAt;
    private double placedAt;
    private double waited;

    public Token(int type, double createdAt) {
        this.type = type;
        this.createdAt = createdAt;
        this.placedAt = createdAt;
    }
}
