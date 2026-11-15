package dev.ua.ikeepcalm.petri.components;

import lombok.Data;

@Data
public class Arc {
    private final Place place;
    private final int multiplicity;
}
