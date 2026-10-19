package com.sportify.payment;

import com.sportify.catalog.entity.*;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.ConfirmPaymentRequest;
import com.sportify.payment.entity.*;
import jakarta.persistence.Column;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CashEventFixtureIdentifierTest {
    static Stream<Arguments> destinations() {
        String identity = "identity/V1__init_identity_schema.sql";
        String member = "identity/V2__add_identity_tables.sql";
        String sport = "catalog/V5__create_catalog_membership_tables.sql";
        String catalog = "catalog/V10__context_refresh_schema.sql";
        String finance = "catalog/V14__create_payment_and_invoice_tables.sql";
        return Stream.of(
                Arguments.of(MemberProfile.class, "memberCode", "member_profile", "member_code", member, ""),
                Arguments.of(Sport.class, "code", "sport", "code", sport, ""),
                Arguments.of(Sport.class, "name", "sport", "name", sport, ""),
                Arguments.of(SportPackage.class, "code", "sport_package", "code", catalog, ""),
                Arguments.of(SportPackageRegistration.class, "registrationCode", "sport_package_registration", "registration_code", catalog, ""),
                Arguments.of(Payment.class, "referenceCode", "payment", "reference_code", finance, ""),
                Arguments.of(RefundRequest.class, "refundCode", "refund_request", "refund_code", catalog, ""),
                Arguments.of(Invoice.class, "invoiceNumber", "invoice", "invoice_number", finance, ""),
                Arguments.of(UserAccount.class, "email", "user_account", "email", identity, "@example.invalid"));
    }

    @ParameterizedTest
    @MethodSource("destinations")
    void generatedValueFitsTheActualDdlAndJpaDestination(Class<?> entity, String field, String table,
            String column, String migration, String suffix) throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration").resolve(migration));
        var definition = Pattern.compile("(?is)CREATE\\s+TABLE\\s+\\[?" + Pattern.quote(table)
                + "\\]?\\s*\\((.*?)\\n\\s*\\);").matcher(sql);
        assertThat(definition.find()).as("CREATE TABLE %s", table).isTrue();
        var size = Pattern.compile("(?im)^\\s*\\[?" + Pattern.quote(column)
                + "\\]?\\s+N?VARCHAR\\((\\d+)\\)").matcher(definition.group(1));
        assertThat(size.find()).as("SQL length for %s.%s", table, column).isTrue();
        int sqlLimit = Integer.parseInt(size.group(1));
        Column mapping = entity.getDeclaredField(field).getAnnotation(Column.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.length()).as("JPA/DDL agreement for %s.%s", table, column).isEqualTo(sqlLimit);
        String value = CashEventIntegrationTest.code() + suffix;
        assertThat(value).isNotBlank();
        assertThat(value.length()).as("fixture fits %s.%s", table, column).isLessThanOrEqualTo(sqlLimit);
        assertThat(value.chars().allMatch(c -> c < 128)).isTrue();
    }

    @Test
    void retainsAllEighteenUuidDigitsAndDistinctValuesWithinTheTightestLimit() {
        UUID first = UUID.fromString("01234567-89ab-4cde-8123-456789abcdef");
        UUID second = UUID.fromString("01234567-89ab-4cde-8223-456789abcdef");
        try (var random = mockStatic(UUID.class, CALLS_REAL_METHODS)) {
            random.when(UUID::randomUUID).thenReturn(first, second);
            String a = CashEventIntegrationTest.code(), b = CashEventIntegrationTest.code();
            assertThat(List.of(a, b)).doesNotHaveDuplicates();
            assertThat(a).hasSize(20).endsWith("0123456789ab4cde81");
            assertThat(b).hasSize(20).endsWith("0123456789ab4cde82");
            random.verify(UUID::randomUUID, times(2));
        }
    }

    @Test
    void generatedBankReferenceSatisfiesExistingRequestValidation() {
        String reference = CashEventIntegrationTest.code();
        var request = ConfirmPaymentRequest.builder().method(PaymentMethod.BANK_TRANSFER)
                .referenceCode(reference).amountReceived(new BigDecimal("100.25")).build();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(reference).isNotBlank().isEqualTo(reference.trim());
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }
}
