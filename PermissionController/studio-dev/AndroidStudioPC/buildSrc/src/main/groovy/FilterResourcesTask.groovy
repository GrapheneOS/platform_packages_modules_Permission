import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern

/**
 * Filters resources to the given product type.
 */
class FilterResourcesTask extends DefaultTask {

    @InputDirectory
    File inputDir

    @OutputDirectory
    File outputDir

    @Input
    String productType

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        def inputPath = inputDir.canonicalFile.toPath();
        if (!inputs.incremental)
            project.delete(outputDir)

        inputs.outOfDate { change ->
            File changedFile = change.file;

            def relative = inputPath.relativize(inputPath.resolve(changedFile.toPath()));
            File targetFile = project.file("$outputDir/$relative")
            targetFile.parentFile.mkdirs()

            if (changedFile.name.endsWith(".xml")) {
                String match1 = "product="
                String match2 = match1 + '"' + productType + '"'
                String match3 = match1 + "'" + productType + "'"
                Pattern match4 = Pattern.compile(/<\/\w+>/);
                StringBuilder filteredText = new StringBuilder();
                boolean bulkDelete = false;

                changedFile.eachLine { line ->
                    if (bulkDelete) {
                        bulkDelete = !line.find(match4);
                    } else if (!line.contains(match1)
                            || line.contains(match2)
                            || line.contains(match3)) {
                        filteredText.append(line).append('\n')
                    } else {
                        bulkDelete = !line.find(match4);
                    }
                }
                targetFile.text = filteredText.toString();
            } else {
                Files.copy(changedFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        inputs.removed { change ->
            def targetFile = project.file("$outputDir/${change.file.name}")
            if (targetFile.exists()) {
                targetFile.delete()
            }
        }
    }
}