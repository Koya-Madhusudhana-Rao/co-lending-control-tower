package com.vivriti.controltower.exceptions;

import java.util.ArrayList;
import java.util.List;

public class AppendOnlyAuditTrail {
    private final List<AuditEntry> entries = new ArrayList<>();

    public void append(AuditEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Audit entry is required");
        }
        entries.add(entry);
    }

    public List<AuditEntry> entries() {
        return List.copyOf(entries);
    }
}