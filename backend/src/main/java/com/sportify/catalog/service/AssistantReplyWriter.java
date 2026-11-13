package com.sportify.catalog.service;

import java.util.List;

/**
 * Optional conversational step of the Flow 6 Member assistant: an AI model writes the reply a Member reads, in natural
 * English or Vietnamese, from the center data the rules already looked up. It never queries, books, cancels or pays,
 * and private or health questions never reach it (the keyword guard answers those first). When no bean is present or
 * a call fails, the fixed rule-based answer is shown instead.
 */
public interface AssistantReplyWriter {

    /** Identifies the writer in logs, for example the model id. */
    String version();

    /** Writes one reply. Implementations throw on any provider failure; the caller then keeps the rule answer. */
    Reply write(ReplyRequest request);

    /** One earlier message of the same conversation; role is "user" or "assistant". */
    record Turn(String role, String text) {}

    /** One record the reply may rely on, as shown to the Member in the answer sources. */
    record Fact(String title, String detail) {}

    /**
     * @param language "vi" or "en", detected from the question
     * @param question the Member's question with e-mail addresses and phone numbers removed
     * @param history  earlier turns, oldest first, already filtered by the guard
     * @param intent   what the rules or the classifier decided the question is about
     * @param draft    the fixed rule-based answer for the same data
     * @param facts    the center or Member records found for the question
     * @param today    today's date at the center (ISO)
     */
    record ReplyRequest(String language, String question, List<Turn> history, String intent, String draft,
                        List<Fact> facts, String today) {}

    /** answered=false when the data did not contain the answer, so the screen offers a human handoff. */
    record Reply(String text, boolean answered) {}
}
