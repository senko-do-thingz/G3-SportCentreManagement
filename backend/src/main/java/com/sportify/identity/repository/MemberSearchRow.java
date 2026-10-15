package com.sportify.identity.repository;

public interface MemberSearchRow {
    Long getUserId();
    String getMemberCode();
    String getFullName();
    String getEmail();
    String getPhone();
    String getCurrentLevel();
    String getCardTier();
    String getStatus();
}
