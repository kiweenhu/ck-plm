package cn.ck.plm.base.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lifecycle template domain model - pure in-memory FSM state machine.
 * Constructed by {@link LifecycleTemplateMaster#toDomainDef()} at runtime.
 */
public class LifecycleTemplateDef {

    public static final LifecycleTemplateDef STANDARD = createStandard();
    public static final LifecycleTemplateDef SIMPLE = createSimple();

    private final String name;
    private final List<LifecycleStatus> statuses = new ArrayList<>();
    private final Map<String, String> transitions = new LinkedHashMap<>();
    private final Map<String, String> rejections = new LinkedHashMap<>();

    public LifecycleTemplateDef(String name) {
        this.name = name;
    }

    public void addStatus(LifecycleStatus status) {
        this.statuses.add(status);
    }

    public void addTransition(String fromStatusCode, String toStatusCode) {
        this.transitions.put(fromStatusCode, toStatusCode);
    }

    public void addRejection(String fromStatusCode, String toStatusCode) {
        this.rejections.put(fromStatusCode, toStatusCode);
    }

    public LifecycleStatus promote(String currentStatusCode) {
        String nextCode = transitions.get(currentStatusCode);
        if (nextCode == null) return null;
        return findStatusByCode(nextCode);
    }

    public LifecycleStatus reject(String currentStatusCode) {
        String prevCode = rejections.get(currentStatusCode);
        if (prevCode == null) return null;
        return findStatusByCode(prevCode);
    }

    private LifecycleStatus findStatusByCode(String code) {
        for (LifecycleStatus s : statuses) {
            if (code.equals(s.getCode())) return s;
        }
        return null;
    }

    private static LifecycleTemplateDef createStandard() {
        LifecycleTemplateDef def = new LifecycleTemplateDef("Standard");
        def.addStatus(new LifecycleStatus("DRAFT", "Draft"));
        def.addStatus(new LifecycleStatus("IN_WORK", "In Work"));
        def.addStatus(new LifecycleStatus("RELEASED", "Released"));
        def.addTransition("DRAFT", "IN_WORK");
        def.addTransition("IN_WORK", "RELEASED");
        def.addRejection("IN_WORK", "DRAFT");
        def.addRejection("RELEASED", "IN_WORK");
        return def;
    }

    private static LifecycleTemplateDef createSimple() {
        LifecycleTemplateDef def = new LifecycleTemplateDef("Simple");
        def.addStatus(new LifecycleStatus("DRAFT", "Draft"));
        def.addStatus(new LifecycleStatus("RELEASED", "Released"));
        def.addTransition("DRAFT", "RELEASED");
        def.addRejection("RELEASED", "DRAFT");
        return def;
    }

    public String getName() {
        return name;
    }

    public List<LifecycleStatus> getStatuses() {
        return statuses;
    }

    public Map<String, String> getTransitions() {
        return transitions;
    }

    public Map<String, String> getRejections() {
        return rejections;
    }

    @Override
    public String toString() {
        return "LifecycleTemplateDef{name=" + name + ", statuses=" + statuses + "}";
    }
}
