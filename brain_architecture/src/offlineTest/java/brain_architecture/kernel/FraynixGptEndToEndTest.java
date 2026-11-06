package brain_architecture.kernel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * End to end: a task goes through OfflineKernel.runTask, is routed to the FraynixGPT substrate adapter,
 * and the trained model generates text. Needs FRAYNIX_GPT_CLASSES and FRAYNIX_GPT_MODEL (model.bin + .tokens).
 */
public final class FraynixGptEndToEndTest {
    public static void main(String[] args) throws Exception {
        String classes = System.getenv("FRAYNIX_GPT_CLASSES");
        String model = System.getenv("FRAYNIX_GPT_MODEL");
        if (classes == null || model == null) {
            throw new IllegalStateException("Set FRAYNIX_GPT_CLASSES and FRAYNIX_GPT_MODEL");
        }
        Path modelSrc = Path.of(model);
        Path workspace = Files.createTempDirectory("fraynix-gpt-e2e");
        Files.copy(modelSrc, workspace.resolve("model.bin"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Path.of(model + ".tokens"), workspace.resolve("model.bin.tokens"), StandardCopyOption.REPLACE_EXISTING);

        KernelConfiguration configuration = KernelConfiguration.defaultForWorkspace(workspace);
        String first;
        try (OfflineKernel kernel = new OfflineKernel(configuration)) {
            TaskEnvelope task = TaskEnvelope.builder("The quick brown")
                .mode(TaskMode.SUPERVISED)
                .strategy("substrate", "fraynix-gpt")
                .strategy("fraynix.classes", classes)
                .strategy("model.id", "model.bin")
                .strategy("model.maxTokens", "48")
                .build();
            KernelRunResult result = kernel.runTask(task, ValidationSuite.empty());
            check("fraynix-gpt".equals(result.getAdapterId()), "Task was not routed to fraynix-gpt: " + result.getAdapterId());

            var calls = result.getTrace().getToolCalls();
            var inference = calls.stream().filter(c -> "local-model-inference".equals(c.getToolName())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No inference tool call in trace: " + calls));
            check(inference.isSuccess(), "Inference failed: " + inference.getOutput());
            first = inference.getOutput();
            check(first != null && first.length() > "The quick brown".length(), "Model produced no text: " + first);
            System.out.println("MODEL OUTPUT: " + first.replace('\n', ' '));
        }
        try (OfflineKernel kernel = new OfflineKernel(configuration)) {
            TaskEnvelope task = TaskEnvelope.builder("The quick brown")
                .mode(TaskMode.SUPERVISED)
                .strategy("substrate", "fraynix-gpt")
                .strategy("fraynix.classes", classes)
                .strategy("model.id", "model.bin")
                .strategy("model.maxTokens", "48")
                .build();
            KernelRunResult again = kernel.runTask(task, ValidationSuite.empty());
            String second = again.getTrace().getToolCalls().stream()
                .filter(c -> "local-model-inference".equals(c.getToolName())).findFirst().orElseThrow().getOutput();
            check(first.equals(second), "Inference is not deterministic");
        }
        System.out.println("FraynixGptEndToEndTest passed");
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }
}

