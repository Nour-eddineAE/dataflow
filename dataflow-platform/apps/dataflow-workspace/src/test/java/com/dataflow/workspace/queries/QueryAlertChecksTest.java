package com.dataflow.workspace.queries;

import com.dataflow.workspace.access.ClickHouseRoles;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.QueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryAlertChecksTest {

    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SQL = "SELECT count() FROM quarantine";

    private final ClickHouse serving = mock(ClickHouse.class);
    private final ClickHouseRoles roles = mock(ClickHouseRoles.class);
    private final SavedQueryStore queries = mock(SavedQueryStore.class);
    private final FakeStore store = new FakeStore();
    private final QueryAlertChecks checks =
            new QueryAlertChecks(store, queries, serving, roles, Clock.fixed(NOW, ZoneOffset.UTC));

    private static QueryAlert alert(QueryAlert.State state, Instant checkedAt, Instant since) {
        return new QueryAlert(
                ID,
                "ada",
                List.of("analysts"),
                false,
                15,
                QueryAlert.Comparison.ABOVE,
                BigDecimal.TEN,
                state,
                checkedAt,
                since,
                null,
                null);
    }

    private static QueryResult rows(Object... values) {
        List<List<Object>> rows = new ArrayList<>();
        for (Object value : values) {
            rows.add(List.of(value));
        }
        return new QueryResult(List.of(new QueryResult.Column("count", "UInt64")), rows, false, 1, 1, 1);
    }

    @BeforeEach
    void setUp() {
        when(serving.maxRows()).thenReturn(100);
        when(queries.find(ID)).thenReturn(Optional.of(new SavedQuery(ID, "ada", "q", SQL, false, NOW)));
    }

    @Test
    void aValuePastTheThresholdFiresAndRemembersWhen() {
        store.alert = alert(QueryAlert.State.UNKNOWN, null, null);
        when(serving.query(anyString(), anyInt(), any())).thenReturn(rows("11"));

        checks.checkDue();

        assertThat(store.alert.state()).isEqualTo(QueryAlert.State.FIRING);
        assertThat(store.alert.value()).isEqualTo("11");
        assertThat(store.alert.since()).isEqualTo(NOW);
        assertThat(store.alert.checkedAt()).isEqualTo(NOW);
    }

    @Test
    void anAlertStillFiringKeepsItsFirstTime() {
        Instant first = NOW.minusSeconds(7200);
        store.alert = alert(QueryAlert.State.FIRING, NOW.minusSeconds(3600), first);
        when(serving.query(anyString(), anyInt(), any())).thenReturn(rows("11"));

        checks.checkDue();

        assertThat(store.alert.since()).isEqualTo(first);
    }

    @Test
    void anAlertThatIsNotDueIsLeftAlone() {
        store.alert = alert(QueryAlert.State.OK, NOW.minusSeconds(60), null);

        checks.checkDue();

        verify(serving, never()).query(anyString(), anyInt(), any());
    }

    @Test
    void anotherReplicaHoldingTheCheckIsLeftToIt() {
        store.alert = alert(QueryAlert.State.UNKNOWN, null, null);
        store.claimable = false;

        checks.checkDue();

        verify(serving, never()).query(anyString(), anyInt(), any());
    }

    @Test
    void aFailingQueryIsShownNotHidden() {
        store.alert = alert(QueryAlert.State.OK, null, null);
        when(serving.query(anyString(), anyInt(), any())).thenThrow(new IllegalStateException("Not enough privileges"));

        checks.checkDue();

        assertThat(store.alert.state()).isEqualTo(QueryAlert.State.ERROR);
        assertThat(store.alert.error()).contains("Not enough privileges");
    }

    @Test
    void aNonNumericValueIsAnError() {
        store.alert = alert(QueryAlert.State.OK, null, null);
        when(serving.query(anyString(), anyInt(), any())).thenReturn(rows("north"));

        checks.checkDue();

        assertThat(store.alert.state()).isEqualTo(QueryAlert.State.ERROR);
    }

    @Test
    void anAlertOfAPersonRunsWithTheirRolesOnly() {
        store.alert = alert(QueryAlert.State.UNKNOWN, null, null);
        ClickHouse.As asAda = new ClickHouse.As("people", "key", List.of("analysts"), "sql:ada");
        when(roles.as(store.alert.caller())).thenReturn(asAda);
        when(serving.query(SQL, 100, asAda)).thenReturn(rows("1"));

        checks.checkDue();

        verify(serving).query(SQL, 100, asAda);
        verify(serving, never()).platform(anyString());
        assertThat(store.alert.state()).isEqualTo(QueryAlert.State.OK);
    }

    @ParameterizedTest
    @CsvSource({
        "ABOVE, 11, true", "ABOVE, 10, false",
        "BELOW, 9, true", "BELOW, 10, false",
        "EQUAL, 10.0, true", "EQUAL, 11, false"
    })
    void comparisonsReadTheFirstValue(QueryAlert.Comparison comparison, String value, boolean fires) {
        assertThat(comparison.holds(rows(value), BigDecimal.TEN)).isEqualTo(fires);
    }

    @Test
    void rowComparisonsCountRows() {
        assertThat(QueryAlert.Comparison.ANY_ROWS.holds(rows("a"), null)).isTrue();
        assertThat(QueryAlert.Comparison.ANY_ROWS.holds(rows(), null)).isFalse();
        assertThat(QueryAlert.Comparison.NO_ROWS.holds(rows(), null)).isTrue();
        assertThat(QueryAlert.Comparison.NO_ROWS.value(rows("a", "b"))).isEqualTo("2");
    }

    private static final class FakeStore implements QueryAlertStore {

        private QueryAlert alert;
        private boolean claimable = true;

        @Override
        public List<QueryAlert> all() {
            return List.of(alert);
        }

        @Override
        public void save(QueryAlert saved) {
            alert = saved;
        }

        @Override
        public void delete(UUID queryId) {
            alert = null;
        }

        @Override
        public boolean claim(UUID queryId, Instant previous, Instant at) {
            return claimable;
        }

        @Override
        public void record(QueryAlert recorded) {
            alert = recorded;
        }
    }
}
