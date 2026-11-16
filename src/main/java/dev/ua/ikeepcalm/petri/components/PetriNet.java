package dev.ua.ikeepcalm.petri.components;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class PetriNet {
    private final List<Place> places = new ArrayList<>();
    private final List<Transition> transitions = new ArrayList<>();

    public Place place(String name, int marking) {
        Place place = new Place(name, false, marking);
        places.add(place);
        return place;
    }

    public Place queue(String name) {
        Place place = new Place(name, true, 0);
        places.add(place);
        return place;
    }

    public Transition transition(String name, Delay delay) {
        Transition transition = new Transition(name, delay);
        transitions.add(transition);
        return transition;
    }

    public void arc(Place from, Transition to) {
        arc(from, to, 1);
    }

    public void arc(Place from, Transition to, int multiplicity) {
        if (from.isTyped()) {
            requireSingleQueue(to.getQueueInput(), multiplicity, to, "вхідна");
            to.setQueueInput(from);
        }
        to.getInputs().add(new Arc(from, multiplicity));
    }

    public void arc(Transition from, Place to) {
        arc(from, to, 1);
    }

    public void arc(Transition from, Place to, int multiplicity) {
        if (to.isTyped()) {
            requireSingleQueue(from.getQueueOutput(), multiplicity, from, "вихідна");
            from.setQueueOutput(to);
        }
        from.getOutputs().add(new Arc(to, multiplicity));
    }

    public Place findPlace(String name) {
        return places.stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
    }

    public Transition findTransition(String name) {
        return transitions.stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
    }

    public void validate() {
        for (Transition transition : transitions) {
            boolean generates = transition.getEmits() > 0;
            if (generates && (transition.getQueueInput() != null || transition.getQueueOutput() == null)) {
                throw new IllegalStateException(transition.getName() + ": перехід надходження потребує вихідної черги і не може мати вхідної");
            }
            if (!generates && transition.getQueueInput() == null && transition.getQueueOutput() != null) {
                throw new IllegalStateException(transition.getName() + ": вихідна черга без вхідної потребує типу маркера, що створюється");
            }
        }
    }

    private void requireSingleQueue(Place existing, int multiplicity, Transition transition, String side) {
        if (existing != null || multiplicity != 1) {
            throw new IllegalStateException(transition.getName() + ": " + side + " черга має бути єдиною, з кратністю 1");
        }
    }
}
