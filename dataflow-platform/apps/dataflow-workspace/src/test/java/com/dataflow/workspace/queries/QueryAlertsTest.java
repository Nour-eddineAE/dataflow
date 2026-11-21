package com.dataflow.workspace.queries;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryAlertsTest {

    private static final UUID OWN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHERS = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID HIDDEN = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private static final Caller ADA = new Caller("ada", List.of("analysts"), false);

    private final QueryAlertStore store = mock(QueryAlertStore.class);
    private final SavedQueries queries = mock(SavedQueries.class);
    private final Access access = mock(Access.class);
    private final QueryAlerts alerts = new QueryAlerts(store, queries, access);

    private static SavedQuery query(UUID id, String owner) {
        return new SavedQuery(id, owner, "name-" + owner, "SELECT 1", true, NOW);
    }

    private static QueryAlert stored(UUID id, String owner) {
        return new QueryAlert(
                id, owner, List.of(), false, 15, QueryAlert.Comparison.ANY_ROWS, null,
                QueryAlert.State.OK, NOW, null, "1", null);
    }

    @BeforeEach
    void setUp() {
        when(access.caller()).thenReturn(ADA);
        when(queries.get(OWN)).thenReturn(query(OWN, "ada"));
        when(queries.get(OTHERS)).thenReturn(query(OTHERS, "bob"));
    }

    @Test
    void anAlertRunsAsThePersonWhoSetItWithTheirGroups() {
        alerts.save(OWN, 15, QueryAlert.Comparison.ABOVE, BigDecimal.TEN);

        ArgumentCaptor<QueryAlert> saved = ArgumentCaptor.forClass(QueryAlert.class);
        verify(store).save(saved.capture());
        assertThat(saved.getValue().caller()).isEqualTo(ADA);
        assertThat(saved.getValue().state()).isEqualTo(QueryAlert.State.UNKNOWN);
        assertThat(saved.getValue().threshold()).isEqualTo(BigDecimal.TEN);
    }

    @Test
    void onlyTheOwnerSetsOrRemovesAnAlert() {
        assertThatThrownBy(() -> alerts.save(OTHERS, 15, QueryAlert.Comparison.ANY_ROWS, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> alerts.delete(OTHERS)).isInstanceOf(AccessDeniedException.class);

        verify(store, never()).save(any());
        verify(store, never()).delete(any());
    }

    @Test
    void anIntervalOutsideTheChoicesIsRefused() {
        assertThatThrownBy(() -> alerts.save(OWN, 1, QueryAlert.Comparison.ANY_ROWS, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> alerts.save(OWN, 7, QueryAlert.Comparison.ANY_ROWS, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aComparisonNeedsAThresholdAndARowCheckDropsOne() {
        assertThatThrownBy(() -> alerts.save(OWN, 15, QueryAlert.Comparison.ABOVE, null))
                .isInstanceOf(IllegalArgumentException.class);

        alerts.save(OWN, 15, QueryAlert.Comparison.ANY_ROWS, BigDecimal.TEN);

        ArgumentCaptor<QueryAlert> saved = ArgumentCaptor.forClass(QueryAlert.class);
        verify(store).save(saved.capture());
        assertThat(saved.getValue().threshold()).isNull();
    }

    @Test
    void peopleSeeAlertsOnlyOnQueriesTheyMayOpen() {
        when(queries.list()).thenReturn(List.of(query(OWN, "ada")));
        when(store.all()).thenReturn(List.of(stored(OWN, "ada"), stored(HIDDEN, "bob")));

        assertThat(alerts.visible())
                .extracting(QueryAlertView::queryId, QueryAlertView::name)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(OWN, "name-ada"));
    }
}
