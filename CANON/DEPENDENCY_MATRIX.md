# Dependency Matrix

Canonical workspace: `E:\registrarCanon_canon`

## Ownership Graph

- Department owns program grouping
- Program owns curriculum lineage
- Curriculum owns year and semester placement
- Sections, schedules, rooms, and faculty are downstream operational records
- Active term overlays the whole graph

## Operational Rules

- room assignment is required for valid scheduling
- room conflict, faculty conflict, and same-section overlap must be rejected
- slot monitoring and room monitoring are separate views
- current term decides what is enforceable now
