import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

class StatsGeneratorTask extends DefaultTask  {

    @InputDirectory
    File protoDir;

    @InputFile
    File atomsFile;

    @OutputFile
    File output;

    @Input
    String module;

    @Input
    String packageName;

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        output.delete();
        output.getParentFile().mkdirs();
        StatsGenerator generator =
                new StatsGenerator(protoDir.getParentFile().getParentFile()
                        .getParentFile().getParentFile());
        generator.process(atomsFile, module, packageName, output);
    }
}