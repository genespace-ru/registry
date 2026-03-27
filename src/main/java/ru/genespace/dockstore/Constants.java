package ru.genespace.dockstore;

import java.util.List;
import java.util.Set;

public final class Constants
{
    public static final String DOCKSTORE_YML_PATH = "/.dockstore.yml";
    public static final String DOCKSTORE_ALTERNATE_YML_PATH = "/.github/.dockstore.yml";
    public static final List<String> DOCKSTORE_YML_PATHS = List.of( DOCKSTORE_YML_PATH, DOCKSTORE_ALTERNATE_YML_PATH );
    public static final Set<String> DOCKSTORE_YML_PATHS_SET = Set.of( DOCKSTORE_YML_PATH, DOCKSTORE_ALTERNATE_YML_PATH );
    public static final String SKIP_COMMIT_ID = "skip";
}
