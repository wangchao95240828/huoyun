package com.xqt.saas.framework.statemachine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 单个实体的状态机：转移关系存为 (from, event) → to 的 map。 */
public final class EntityStateMachine {
    private final Map<TransitionKey, String> transitions;

    private EntityStateMachine(Map<TransitionKey, String> transitions) {
        this.transitions = transitions;
    }

    public String next(String fromStatus, String event) {
        return transitions.get(new TransitionKey(fromStatus, event));
    }

    public Set<String> eventsFrom(String fromStatus) {
        Set<String> events = new HashSet<>();
        for (TransitionKey key : transitions.keySet()) {
            if (key.from.equals(fromStatus)) {
                events.add(key.event);
            }
        }
        return events;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<TransitionKey, String> map = new HashMap<>();

        public Builder transition(String from, String event, String to) {
            map.put(new TransitionKey(from, event), to);
            return this;
        }

        public EntityStateMachine build() {
            return new EntityStateMachine(Map.copyOf(map));
        }
    }

    private record TransitionKey(String from, String event) {
    }
}
