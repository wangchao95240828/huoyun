package com.xqt.saas.finance.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class SandboxBankFeedTest {

    private final SandboxBankFeed feed = new SandboxBankFeed();

    @Test
    void emptyAccountReturnsEmpty() {
        assertThat(feed.pullStatement("t", null, LocalDate.now(), LocalDate.now())).isEmpty();
        assertThat(feed.pullStatement("t", "", LocalDate.now(), LocalDate.now())).isEmpty();
    }

    @Test
    void returnsThreeSampleEntries() {
        List<BankReconciliationFeed.BankStatementEntry> rows =
            feed.pullStatement("t1", "62211234", LocalDate.parse("2026-05-29"), LocalDate.parse("2026-05-29"));
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.direction()).containsExactly("CREDIT", "DEBIT", "CREDIT");
        assertThat(rows.get(0).currency()).isEqualTo("CNY");
        assertThat(rows.get(0).externalRef()).startsWith("SBX-BANK-");
        assertThat(rows.get(0).raw()).containsEntry("source", "SANDBOX");
    }

    @Test
    void codeIsSandbox() {
        assertThat(feed.code()).isEqualTo("SANDBOX");
    }

    @Test
    void externalRefsAreUnique() {
        List<BankReconciliationFeed.BankStatementEntry> a =
            feed.pullStatement("t", "ACC1", LocalDate.now(), LocalDate.now());
        List<BankReconciliationFeed.BankStatementEntry> b =
            feed.pullStatement("t", "ACC2", LocalDate.now(), LocalDate.now());
        long unique = java.util.stream.Stream.concat(a.stream(), b.stream())
            .map(BankReconciliationFeed.BankStatementEntry::externalRef)
            .distinct().count();
        assertThat(unique).isEqualTo(6);
    }
}
