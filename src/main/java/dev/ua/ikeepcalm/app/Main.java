package dev.ua.ikeepcalm.app;

import dev.ua.ikeepcalm.petri.ServiceCentre;
import dev.ua.ikeepcalm.util.Logger;

public class Main {

    private static final long SEED = 1;
    private static final double WARMUP = 5_000;
    private static final double DURATION = 100_000;

    static void main() {
        ServiceCentre centre = ServiceCentre.base();
        print("Базові значення", centre);

        centre.getChannels()[ServiceCentre.PROCESSING] = 5;
        print("5 вікон оформлення", centre);
    }

    private static void print(String title, ServiceCentre centre) {
        Logger.stage(title);
        ServiceCentre.Result result = ServiceCentre.run(centre, SEED, WARMUP, DURATION);
        Logger.info("Розгін %.0f хв, Tmod %.0f хв, зерно %d", WARMUP, DURATION, SEED);
        Logger.info("Обслуговано заявок: %d, пропускна здатність %.4f заявок/хв", result.getCompleted(), result.getThroughput());
        Logger.info("Середній час очікування: %.3f хв, середній час перебування: %.3f хв", result.getWaiting(), result.getSojourn());
        result.getSojournByType().forEach((type, sojourn) -> Logger.info("Тип %d: очікування %.3f хв, перебування %.3f хв",
                type, result.getWaitingByType().get(type), sojourn));
        for (int node = 0; node < ServiceCentre.NODES.length; node++) {
            Logger.info("%s: завантаженість %.3f (за рівняннями потоків %.3f), середня черга %.3f, очікування в черзі %.3f хв",
                    ServiceCentre.NODES[node], result.getUtilisation()[node], centre.offeredUtilisation(node),
                    result.getMeanQueue()[node], result.getQueueWaiting()[node]);
        }
        Logger.info("Частка направлених до консультанта %.4f, частка повернень з вікон %.4f",
                result.getConsultShare(), result.getReturnShare());
    }
}
