package dev.ua.ikeepcalm.petri;

import dev.ua.ikeepcalm.petri.components.Delay;
import dev.ua.ikeepcalm.util.Counter;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class DelayTest {

    static Stream<org.junit.jupiter.params.provider.Arguments> laws() {
        return Stream.of(
                arguments(Delay.det(2.5), 2.5, 0.0),
                arguments(Delay.exp(2.0), 2.0, 2.0),
                arguments(Delay.unif(5.0, 2.0), 5.0, 2.0 / Math.sqrt(3)),
                arguments(Delay.norm(10.0, 2.0), 10.0, 2.0),
                arguments(Delay.erlang(0.6, 2), 0.6, 0.6 / Math.sqrt(2)));
    }

    @ParameterizedTest
    @MethodSource("laws")
    void sampleMeanAndDeviationMatchParameters(Delay delay, double mean, double deviation) {
        Random random = new Random(3);
        Counter counter = new Counter();
        for (int i = 0; i < 100_000; i++) {
            counter.add(delay.sample(random));
        }
        assertEquals(mean, counter.mean(), 0.01 * mean);
        assertEquals(deviation, counter.deviation(), 0.01 * Math.max(deviation, mean));
    }
}
