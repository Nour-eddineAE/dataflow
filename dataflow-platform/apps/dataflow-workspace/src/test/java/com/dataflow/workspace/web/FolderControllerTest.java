package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.Principal;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.folders.FolderFiles;
import com.dataflow.workspace.folders.FolderSync;
import com.dataflow.workspace.folders.FolderViews.CreateFolder;
import com.dataflow.workspace.folders.GitFolder;
import com.dataflow.workspace.folders.GitFolders;
import com.dataflow.workspace.folders.JavaApi;
import com.dataflow.workspace.folders.JavaChecks;
import com.dataflow.workspace.folders.People;
import com.dataflow.workspace.folders.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FolderControllerTest {

    private static final Person BOB = new Person("bob", "Bob", "bob@dataflow.local");
    private static final Securable FOLDER = Securable.folder("Shared/analytics");

    private final GitFolders folders = mock(GitFolders.class);
    private final People people = mock(People.class);
    private final Access access = mock(Access.class);
    private final FolderController controller = new FolderController(
            folders,
            mock(FolderFiles.class),
            mock(FolderSync.class),
            people,
            access,
            mock(JavaApi.class),
            mock(JavaChecks.class));

    private static CreateFolder request(boolean shared, String team) {
        return new CreateFolder("analytics", "https://git.test/a.git", "main", shared, null, team);
    }

    @BeforeEach
    void signIn() {
        when(access.caller()).thenReturn(new Caller("bob", List.of("finance"), false));
        when(people.current()).thenReturn(BOB);
        when(folders.create(any(), any())).thenReturn(new GitFolder(
                UUID.randomUUID(), "Shared/analytics", "https://git.test/a.git", "main", null, "bob", Instant.EPOCH));
    }

    @Test
    void aTeamMemberMakesTheTeamManageAFolder() {
        controller.create(request(true, "finance"));

        verify(access).owner(FOLDER, "bob");
        verify(access).ensure(FOLDER, Principal.group("finance"), Privilege.MANAGE, "bob");
    }

    @Test
    void nobodyGivesAFolderToATeamTheyAreNotIn() {
        assertThatThrownBy(() -> controller.create(request(true, "logistics"))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(folders);
    }

    @Test
    void aPersonalFolderHasNoTeam() {
        assertThatThrownBy(() -> controller.create(request(false, "finance"))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(folders);
    }
}
