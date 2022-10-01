import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class to create a map of available dependencies in repo
 */
public class RepoDependencyMapper {

    private final Map<String, String> mVersionMap = new HashMap<>();

    public RepoDependencyMapper mapPath(String path) {
        return mapPath(path, "");
    }

    /**
     * Parses the provided path for a possible m2repository
     */
    public RepoDependencyMapper mapPath(String path, String prefix) {
        File repoPath = new File(path);
        for (File child : repoPath.listFiles()) {
            checkEndPoint(child, new ArrayList<>(), prefix);
        }
        return this;
    }

    public Map<String, String> getMap() {
        return mVersionMap;
    }

    private void checkEndPoint(File current, List<File> parents, String prefix) {
        if (!current.isDirectory()) {
            return;
        }

        parents.add(current);
        for (File child : current.listFiles()) {
            checkEndPoint(child, parents, prefix);
        }
        parents.remove(current);

        // Check if this is the end point.
        int parentsCount = parents.size();
        if (parentsCount > 0) {
            String versionName = current.getName();
            String moduleName = parents.get(parentsCount - 1).getName();
            if (new File(current, moduleName + "-" + versionName + ".pom").exists()) {
                String groupName = parents.subList(0, parentsCount - 1)
                        .stream().map(File::getName).collect(Collectors.joining("."));
                System.out.println(prefix + groupName + ":" + moduleName + " -> " + versionName);
                mVersionMap.put(prefix + groupName + ":" + moduleName, versionName);
            }
        }
    }
}
