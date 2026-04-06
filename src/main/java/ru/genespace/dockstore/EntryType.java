package ru.genespace.dockstore;

public enum EntryType {
    TOOL("tool"),
    WORKFLOW("workflow"),
    SERVICE("service"),
    APPTOOL("tool"),
    NOTEBOOK("notebook");

    private final String term;

    EntryType(String term) {
        this.term = term;
    }

    public String getTerm() {
        return term;
    }
}
