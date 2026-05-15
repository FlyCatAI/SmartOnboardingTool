package com.flycat.rm.common.statemachine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 通用有向图状态机。&lt;S&gt; 状态枚举、&lt;E&gt; 事件枚举。
 * 不绑定具体业务，由 capability 模块构造自己的转换表。
 */
public final class StateMachine<S extends Enum<S>, E extends Enum<E>> {

    private final Map<Key<S, E>, S> transitions;
    private final Set<S> terminal;

    private StateMachine(Map<Key<S, E>, S> transitions, Set<S> terminal) {
        this.transitions = Map.copyOf(transitions);
        this.terminal = Set.copyOf(terminal);
    }

    public boolean isTerminal(S state) {
        return terminal.contains(state);
    }

    public boolean canFire(S current, E event) {
        if (terminal.contains(current)) {
            return false;
        }
        return transitions.containsKey(new Key<>(current, event));
    }

    /**
     * 触发事件并返回新状态。非法触发返回 {@link IllegalTransitionException}。
     */
    public S fire(S current, E event) {
        if (terminal.contains(current)) {
            throw new IllegalTransitionException(current, event, "terminal_state");
        }
        S next = transitions.get(new Key<>(current, event));
        if (next == null) {
            throw new IllegalTransitionException(current, event, "no_transition");
        }
        return next;
    }

    public static <S extends Enum<S>, E extends Enum<E>> Builder<S, E> builder() {
        return new Builder<>();
    }

    public static final class Builder<S extends Enum<S>, E extends Enum<E>> {
        private final Map<Key<S, E>, S> transitions = new HashMap<>();
        private final Set<S> terminal = new HashSet<>();

        public Builder<S, E> transition(S from, E event, S to) {
            transitions.put(new Key<>(from, event), to);
            return this;
        }

        public Builder<S, E> terminal(S state) {
            terminal.add(state);
            return this;
        }

        public StateMachine<S, E> build() {
            return new StateMachine<>(transitions, terminal);
        }
    }

    private record Key<S, E>(S from, E event) {}
}
