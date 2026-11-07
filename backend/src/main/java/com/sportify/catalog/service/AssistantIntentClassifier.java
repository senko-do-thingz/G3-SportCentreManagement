package com.sportify.catalog.service;

import java.util.List;

/**
 * Optional AI step of the Flow 6 Member assistant: it only works out what a free-text question (English or
 * Vietnamese) is asking for. It never writes the answer. Answers always come from read-only center queries scoped to
 * the signed-in Member, so a wrong or manipulated classification cannot reveal other people's data or change anything.
 * When no bean is present, or a call fails, the keyword rules in {@link CenterConsultationService} are used.
 */
public interface AssistantIntentClassifier {

    /** Identifies the classifier in responses, for example the model id. */
    String version();

    /**
     * Classifies one question. {@code question} has already had e-mail addresses and long numbers removed;
     * {@code sports} lists the active sport names the answer may be filtered by.
     */
    Classification classify(String question, List<String> sports);

    /**
     * @param intent one of {@link AssistantAiPolicy#INTENTS}
     * @param sport  one of the given sport names, or null
     * @param day    TODAY, TOMORROW or UPCOMING
     * @param faqId  the curated FAQ topic for intent FAQ, otherwise 0
     */
    record Classification(String intent, String sport, String day, int faqId) {}
}
