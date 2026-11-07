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
        var drafts = AiDrillPolicy.toDrafts(new ProposedDrills(List.of(drill("Six-corner shadow footwork", 10), drill("Net movement", 10))), "ADVANCED");
        assertThat(drafts).hasSize(2);
        assertThat(drafts.getFirst()).satisfies(d -> {
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
        assertThat(drafts).isEmpty();
    }

    @Test
    void rejectsOversizedAndInvalidProposals() {
        var many = List.of(drill("A", 5), drill("B", 5), drill("C", 5), drill("D", 5), drill("E", 5));
        assertThat(AiDrillPolicy.toDrafts(new ProposedDrills(many), "BEGINNER")).isEmpty();
        var longReason = new ProposedDrill("A", "g", 5, "Do it.", "r".repeat(600));
        assertThat(AiDrillPolicy.toDrafts(new ProposedDrills(List.of(longReason, drill("B", 5))), "BEGINNER")).isEmpty();
        assertThat(AiDrillPolicy.toDrafts(new ProposedDrills(List.of(drill("A", 5),
                new ProposedDrill("Medical advice", "g", 5, "Take medicine for pain.", "r"))), "BEGINNER")).isEmpty();
        assertThat(AiDrillPolicy.toDrafts(new ProposedDrills(List.of(drill("A", 5),
                new ProposedDrill("Tennis serve", "g", 5, "Serve.", "r"))), "BEGINNER", null, "Badminton")).isEmpty();
    }

    @Test
    void emptyOrMissingProposalGivesNoDrafts() {
        assertThat(AiDrillPolicy.toDrafts(null, "BEGINNER")).isEmpty();
        assertThat(AiDrillPolicy.toDrafts(new ProposedDrills(null), "BEGINNER")).isEmpty();
        assertThat(AiDrillPolicy.toDrafts(new ProposedDrills(List.of(drill("Only one", 5))), "BEGINNER")).isEmpty();
    }

    @Test
    void providerContextNeverContainsRawCoachNotesOrMemberIdentifiers() {
        var input = new DrillGenerator.Input(DrillGenerator.SCOPE_INDIVIDUAL, "Badminton", "BEGINNER", "ADVANCED",
                "COMPETITION", 2, List.of(new DrillGenerator.RecentScore("Footwork for Alice 0912345678",
                new BigDecimal("4.5"), 10, LocalDate.of(2027, 3, 9))),
                "Alice alice@example.test has a strong serve", "Alice has footwork pain", "Call Alice 0912345678", 45, null);
        String context = AiDrillPolicy.context(new ObjectMapper().registerModule(new JavaTimeModule()), input);
        assertThat(context).contains("BADMINTON", "COMPETITION", "footwork", "4.5");
        assertThat(context).doesNotContain("Alice", "0912345678", "alice@example.test", "pain", "2027-03-09");
    }

    @Test
    void classContextSuppressesSmallBucketsAndSmallCohorts() {
        var group = new DrillGenerator.GroupSummary(3, java.util.Map.of("BEGINNER", 2, "ADVANCED", 1),
                java.util.Map.of("BUILD_SKILLS", 2, "COMPETITION", 1), List.of());
        var input = new DrillGenerator.Input(DrillGenerator.SCOPE_CLASS, "Badminton", "BEGINNER", null,
                null, 0, List.of(), null, null, null, 45, group);
        String context = AiDrillPolicy.context(new ObjectMapper(), input);
        assertThat(context).contains("\"members\":3", "\"levels\":{}", "\"goals\":{}");
        var small = new DrillGenerator.Input(DrillGenerator.SCOPE_CLASS, "Badminton", "BEGINNER", null,
                null, 0, List.of(), null, null, null, 45,
                new DrillGenerator.GroupSummary(2, java.util.Map.of("BEGINNER", 2), java.util.Map.of(), List.of()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AiDrillPolicy.context(new ObjectMapper(), small))
                .isInstanceOf(IllegalArgumentException.class);
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
        when(text.text()).thenReturn(new ProposedDrills(List.of(drill("Six-corner shadow footwork", 10), drill("Net movement", 10))));

        var generator = new ClaudeDrillGenerator(client, "claude-opus-5-5", new ObjectMapper().registerModule(new JavaTimeModule()));
        var drafts = generator.generate(INPUT);

        assertThat(drafts).extracting(d -> d.title()).containsExactly("Six-corner shadow footwork", "Net movement");
        assertThat(generator.version()).isEqualTo("claude-opus-5-5");
        ArgumentCaptor<StructuredMessageCreateParams<ProposedDrills>> sent = ArgumentCaptor.forClass(StructuredMessageCreateParams.class);
        verify(messages).create(sent.capture());
        String body = sent.getValue().rawParams().toString();
        assertThat(body).contains("claude-opus-5-5", "COMPETITION", "footwork", "ADVANCED", "INDIVIDUAL");
        assertThat(body).doesNotContain("Footwork recovery", "Strong serve", "Net kills");
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
