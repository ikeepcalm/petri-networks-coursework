package dev.ua.ikeepcalm.petri.components;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Firing {
    private final double moment;
    private final Token token;
}
