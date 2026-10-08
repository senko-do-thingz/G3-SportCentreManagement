package com.sportify.catalog.entity;

/**
 * Lifecycle of a member card (database check {@code ck_member_card_status}, widened in V13).
 * <ul>
 *   <li>{@code PENDING_PAYMENT}: requested online; gives no discount until the front desk confirms the payment.</li>
 *   <li>{@code ACTIVE}: paid; gives its discount between start date and end date.</li>
 *   <li>{@code EXPIRED}: the end date has passed.</li>
 *   <li>{@code CANCELLED}: a pending request that was cancelled before payment.</li>
 *   <li>{@code REPLACED}: an active card that was upgraded to a dearer tier.</li>
 * </ul>
 */
public enum CardStatus {
    PENDING_PAYMENT,
    ACTIVE,
    EXPIRED,
    CANCELLED,
    REPLACED
}
