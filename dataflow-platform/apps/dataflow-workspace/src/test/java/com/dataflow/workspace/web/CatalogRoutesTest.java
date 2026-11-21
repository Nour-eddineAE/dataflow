package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Visibility;
import com.dataflow.workspace.catalog.CatalogViews;
import com.dataflow.workspace.catalog.DeltaMetadata;
import com.dataflow.workspace.catalog.Quarantine;
import com.dataflow.workspace.catalog.Rescued;
import com.dataflow.workspace.catalog.TableChanges;
import com.dataflow.workspace.catalog.TableProfiles;
import com.dataflow.workspace.catalog.TableService;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.SystemTables;
import com.dataflow.workspace.setup.PlatformServices;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogRoutesTest {

    private final TableService tables = mock(TableService.class);
    private final Visibility visibility = mock(Visibility.class);
    private final SystemTables system = mock(SystemTables.class);
    private final ClickHouse serving = mock(ClickHouse.class);
    private final TableProfiles profiles = mock(TableProfiles.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new CatalogController(tables, mock(CatalogViews.class), visibility, serving, mock(PlatformServices.class)),
                    new CatalogUsageController(tables, visibility, serving, system, Clock.systemUTC()),
                    new LakeTableController(tables, profiles, mock(DeltaMetadata.class), mock(Quarantine.class),
                            mock(TableChanges.class), mock(Rescued.class), visibility),
                    new ServedTableController(tables, profiles, visibility))
            .build();

    @Test
    void landingTable_isATableLikeAnyOther() throws Exception {
        mvc.perform(get("/api/catalog/earthquakes/landing")).andExpect(status().isOk());

        verify(tables).detail("earthquakes", "landing");
        verify(tables, never()).landing("earthquakes");
    }

    @Test
    void landedFiles_haveTheirOwnPath() throws Exception {
        mvc.perform(get("/api/catalog/earthquakes/landing/files")).andExpect(status().isOk());

        verify(tables).landing("earthquakes");
    }

    @Test
    void columnUsage_ofAReadableTable() throws Exception {
        when(visibility.sqlNames(any(), any())).thenReturn(Set.of("earthquakes.current"));

        mvc.perform(get("/api/catalog/usage/columns").param("table", "earthquakes.current"))
                .andExpect(status().isOk());

        verify(system).columnUsage(eq("earthquakes.current"), any());
    }

    @Test
    void columnUsage_ofAnotherTeamsTable_isEmptyAndNeverAsked() throws Exception {
        when(visibility.sqlNames(any(), any())).thenReturn(Set.of("earthquakes.current"));

        mvc.perform(get("/api/catalog/usage/columns").param("table", "taxi_trips.current"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));

        verify(system, never()).columnUsage(any(), any());
    }
}
