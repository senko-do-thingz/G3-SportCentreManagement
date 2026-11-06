package com.sportify.catalog.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredContentBlock;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.services.blocking.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sportify.catalog.service.AiDrillPolicy.ProposedDrill;
import com.sportify.catalog.service.AiDrillPolicy.ProposedDrills;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** No network: the Anthropic client is mocked. */
class ClaudeDrillGeneratorTest {
    private static final DrillGenerator.Input INPUT = new DrillGenerator.Input(DrillGenerator.SCOPE_INDIVIDUAL, "Badminton", "BEGINNER", "ADVANCED",
            "COMPETITION", 6, List.of(new DrillGenerator.RecentScore("Footwork", new BigDecimal("4.50"), 10, LocalDate.of(2027, 3, 9))),
            "Strong serve", "Footwork recovery", "Net kills", 45, null);

    private static ProposedDrill drill(String title, int minutes) {
        return new ProposedDrill(title, "Footwork", minutes, "Shadow the six corners, then reset.", "Footwork scored 4.5/10.");
    }

    @Test
    void keepsTheTotalWithinTheSessionDuration() {
        var drafts = AiDrillPolicy.toDrafts(new ProposedDrills(List.of(drill("A", 20), drill("B", 20), drill("C", 10), drill("D", 5))), "BEGINNER", 45);
        assertThat(drafts).extracting(d -> d.title()).containsExactly("A", "B", "D");
        assertThat(drafts.stream().mapToInt(d -> d.durationMinutes()).sum()).isLessThanOrEqualTo(45);
    }

    @Test
    void validatesCoachInputs() {
        assertThat(DrillGenerator.Options.of("  Net kills  ", 60)).isEqualTo(new DrillGenerator.Options("Net kills", 60));
        assertThat(DrillGenerator.Options.of(" ", null)).isEqualTo(DrillGenerator.Options.NONE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DrillGenerator.Options.of("x".repeat(201), null))
                .isInstanceOf(com.sportify.core.exception.BusinessRuleException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DrillGenerator.Options.of(null, 14))
                .isInstanceOf(com.sportify.core.exception.BusinessRuleException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DrillGenerator.Options.of(null, 181))
                .isInstanceOf(com.sportify.core.exception.BusinessRuleException.class);
    }

    @Test
    void keepsValidDraftsAndUsesTheMemberLevel() {
        var drafts = AiDrillPolicy.toDrafts(new ProposedDrills(List.of(drill("Six-corner shadow footwork", 10))), "ADVANCED");
        assertThat(drafts).singleElement().satisfies(d -> {
            assertThat(d.title()).isEqualTo("Six-corner shadow footwork");
            assertThat(d.durationMinutes()).isEqualTo(10);
            assertThat(d.level()).isEqualTo("ADVANCED");
            assertThat(d.reason()).contains("4.5");
        });
    }

    @Test
    void dropsDraftsACoachPlanCouldNotStore() {
        var drafts = AiDrillPolicy.toDrafts(new ProposedDrills(List.of(
                drill("Too short", 2),
                drill("Too long", 31),
                drill("x".repeat(151), 10),
                drill("   ", 10),
                new ProposedDrill("No instructions", "Footwork", 10, null, "r"),
                drill("Valid", 3))), "BEGINNER");
        assertThat(drafts).extracting(d -> d.title()).containsExactly("Valid");
    }

    @Test
    void limitsToFourDraftsAndTruncatesLongReasons() {
        var many = List.of(drill("A", 5), drill("B", 5), drill("C", 5), drill("D", 5), drill("E", 5));
        assertThat(AiDrillPolicy.toDrafts(new ProposedDrills(many), "BEGINNER")).hasSize(4);
        var longReason = new ProposedDrill("A", "g", 5, "Do it.", "r".repeat(600));
        assertThat(AiDrillPolicy.toDrafts(new ProposedDrills(List.of(longReason)), "BEGINNER").getFirst().reason()).hasSize(500);
    }

    @Test
    void emptyOrMissingProposalGivesNoDrafts() {
        assertThat(AiDrillPolicy.toDrafts(null, "BEGINNER")).isEmpty();
        assertThat(AiDrillPolicy.toDrafts(new ProposedDrills(null), "BEGINNER")).isEmpty();
    }

    @Test
    void startsWithoutAnApiKeyBecauseTheClientIsCreatedOnFirstUse() {
        org.assertj.core.api.Assertions.assertThatCode(() -> new ClaudeDrillGenerator("claude-opus-5-5", 5, "", new ObjectMapper()))
                .doesNotThrowAnyException();
    }

    @Test
    void memberLevelWinsOnlyWhenSupported() {
        assertThat(DrillGenerator.effectiveLevel("ADVANCED", "BEGINNER")).isEqualTo("ADVANCED");
        assertThat(DrillGenerator.effectiveLevel(null, "BEGINNER")).isEqualTo("BEGINNER");
        assertThat(DrillGenerator.effectiveLevel("EXPERT", "INTERMEDIATE")).isEqualTo("INTERMEDIATE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsOnlyTheTrainingContextAndMapsTheStructuredReply() {
        var client = mock(AnthropicClient.class);
        var messages = mock(MessageService.class);
        StructuredMessage<ProposedDrills> reply = mock(StructuredMessage.class);
        StructuredContentBlock<ProposedDrills> block = mock(StructuredContentBlock.class);
        StructuredTextBlock<ProposedDrills> text = mock(StructuredTextBlock.class);
        when(client.messages()).thenReturn(messages);
        when(messages.create(any(StructuredMessageCreateParams.class))).thenReturn(reply);
        when(reply.stopReason()).thenReturn(Optional.of(StopReason.END_TURN));
        when(reply.content()).thenReturn(List.of(block));
        when(block.text()).thenReturn(Optional.of(text));
        when(text.text()).thenReturn(new ProposedDrills(List.of(drill("Six-corner shadow footwork", 10))));

        var generator = new ClaudeDrillGenerator(client, "claude-opus-5-5", new ObjectMapper().registerModule(new JavaTimeModule()));
        var drafts = generator.generate(INPUT);

        assertThat(drafts).extracting(d -> d.title()).containsExactly("Six-corner shadow footwork");
        assertThat(generator.version()).isEqualTo("claude-opus-5-5");
        ArgumentCaptor<StructuredMessageCreateParams<ProposedDrills>> sent = ArgumentCaptor.forClass(StructuredMessageCreateParams.class);
        verify(messages).create(sent.capture());
        String body = sent.getValue().rawParams().toString();
        assertThat(body).contains("claude-opus-5-5", "COMPETITION", "Footwork recovery", "ADVANCED", "Net kills", "INDIVIDUAL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void refusalGivesNoDrafts() {
        var client = mock(AnthropicClient.class);
        var messages = mock(MessageService.class);
        StructuredMessage<ProposedDrills> reply = mock(StructuredMessage.class);
        when(client.messages()).thenReturn(messages);
        when(messages.create(any(StructuredMessageCreateParams.class))).thenReturn(reply);
        when(reply.stopReason()).thenReturn(Optional.of(StopReason.REFUSAL));

        assertThat(new ClaudeDrillGenerator(client, "m", new ObjectMapper().registerModule(new JavaTimeModule())).generate(INPUT)).isEmpty();
    }
}
