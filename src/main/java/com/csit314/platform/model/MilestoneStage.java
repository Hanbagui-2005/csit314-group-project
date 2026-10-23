package com.csit314.platform.model;

// IDR-09 - a renovation's progress, in order. Designers move a project forward
// one stage at a time; there's no going back once a stage is reached.
public enum MilestoneStage {
    DESIGN_APPROVED("Design approved"),
    MATERIALS_ORDERED("Materials ordered"),
    INSTALLATION_COMPLETE("Installation complete");

    private final String label;

    MilestoneStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public MilestoneStage next() {
        MilestoneStage[] stages = values();
        int nextOrdinal = ordinal() + 1;
        return nextOrdinal < stages.length ? stages[nextOrdinal] : null;
    }

    // named to avoid clashing with the "final" keyword in Thymeleaf expressions
    public boolean isLastStage() {
        return next() == null;
    }
}
