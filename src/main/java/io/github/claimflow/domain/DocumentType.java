package io.github.claimflow.domain;

/** Kind of supporting document attached to a claim (metadata only; no file storage). */
public enum DocumentType {
    PHOTO,
    POLICE_REPORT,
    REPAIR_ESTIMATE,
    INVOICE,
    OTHER
}
