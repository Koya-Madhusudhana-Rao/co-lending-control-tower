package com.vivriti.controltower.probabilistic;

import com.vivriti.controltower.domain.CanonicalEvent;
import com.vivriti.controltower.domain.ProbableMatchEvidence;

/** A surfaced probable match: the unresolved originator, its best candidate, and the evidence bundle. */
public record ProbableMatch(CanonicalEvent originator, CanonicalEvent candidate, ProbableMatchEvidence evidence) {
}
