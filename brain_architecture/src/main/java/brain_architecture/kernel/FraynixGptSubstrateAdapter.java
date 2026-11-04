package brain_architecture.kernel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Substrate adapter for the dependency-free FraynixGPT workbench (eyeoverthink/fraynix-gpt).
 *
 * <p>RUN_INFERENCE runs the workbench's own {@code generate} command (seeded, so the same prompt gives the same
 * bytes). RUN_COMMAND runs one allow-listed FraynixGPT subcommand, such as {@code selftest}, {@code vsa} or
 * {@code hdcx86}; anything else is refused. FraynixGPT always runs as a child JVM with the workspace as its
 * working directory, a time limit, and bounded output. READ_FILE and LIST_DIRECTORY stay inside the workspace.
 */
public final class FraynixGptSubstrateAdapter implements SubstrateAdapter {
    public static final String ADAPTER_ID = "fraynix-gpt";
    public static final Set<String> ALLOWED_COMMANDS = Set.of(
        "selftest", "vsa", "hdcx86", "generate", "seal", "verify", "sealcross", "peephole", "airgap");

    private static final int MAX_OUTPUT_CHARS = 4_000;
    private static final long MAX_CAPTURE_BYTES = 1_048_576L;

    private final Path workspaceRoot;
    private final Path classpath;
    private final long timeoutSeconds;
    private final String javaExecutable;

    /**
     * @param workspaceRoot directory every action is confined to
     * @param classpath     directory or jar holding the compiled {@code FraynixGPT} class
     * @param timeoutSeconds wall-clock limit for one child process
     */
    public FraynixGptSubstrateAdapter(Path workspaceRoot, Path classpath, long timeoutSeconds) {
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("timeoutSeconds must be at least 1");
        }
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.classpath = classpath.toAbsolutePath().normalize();
        this.timeoutSeconds = timeoutSeconds;
        this.javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    @Override
    public SubstrateSession loadContext(TaskEnvelope task, List<MemoryHit> context) {
        SubstrateSession session = new SubstrateSession(workspaceRoot);
        session.getState().put("taskId", task.getId().toString());
        session.getState().put("goal", task.getGoal());
        session.getState().put("contextHits", Integer.toString(context.size()));
        session.getState().put("classpath", classpath.toString());
        return session;
    }

    @Override
    public SubstrateResult step(SubstrateSession session, String stepInstruction) {
        session.getState().put("lastStep", stepInstruction);
        return new SubstrateResult(true, stepInstruction, 0, Map.of("adapter", ADAPTER_ID));
    }

    @Override
    public SubstrateResult act(SubstrateSession session, SubstrateAction action) {
        try {
            return switch (action.getType()) {
                case READ_FILE -> readFile(session, action.getTarget());
                case LIST_DIRECTORY -> listDirectory(session, action.getTarget());
                case RUN_COMMAND -> runCommand(session, action.getArguments());
                case RUN_INFERENCE -> runInference(session, action);
            };
        } catch (IOException e) {
            return fail(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("interrupted");
        }
    }

    @Override
    public SubstrateSnapshot snapshot(SubstrateSession session) {
        return new SubstrateSnapshot(session.getSessionId(), session.getWorkspaceRoot(), session.getState());
    }

    @Override
    public SubstrateSession restore(SubstrateSnapshot snapshot) {
        return new SubstrateSession(snapshot.getSessionId(), snapshot.getWorkspaceRoot(), new HashMap<>(snapshot.getState()));
    }

    private SubstrateResult runInference(SubstrateSession session, SubstrateAction action) throws IOException, InterruptedException {
        String model = action.getModelReference();
        if (model == null || model.isBlank()) {
            return fail("RUN_INFERENCE needs a model reference (a FraynixGPT model file inside the workspace)");
        }
        if (action.getPrompt() == null) {
            return fail("RUN_INFERENCE needs a prompt");
        }
        Path modelFile = resolveWithinWorkspace(session.getWorkspaceRoot(), Path.of(model));
        if (!Files.isRegularFile(modelFile)) {
            return fail("model file not found in workspace: " + model);
        }
        int len = Math.max(1, action.getMaxTokens());
        List<String> args = List.of("generate", "model=" + modelFile, "prompt=" + action.getPrompt(), "len=" + len, "seed=1");
        return execute(session.getWorkspaceRoot(), args, "inference");
    }

    private SubstrateResult runCommand(SubstrateSession session, List<String> command) throws IOException, InterruptedException {
        if (command.isEmpty()) {
            return fail("No command provided");
        }
        String name = command.get(0);
        if (!ALLOWED_COMMANDS.contains(name)) {
            return fail("FraynixGPT command not allowed: " + name);
        }
        return execute(session.getWorkspaceRoot(), new ArrayList<>(command), "command");
    }

    private SubstrateResult execute(Path cwd, List<String> gptArgs, String kind) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable);
        cmd.add("-cp");
        cmd.add(classpath.toString());
        cmd.add("FraynixGPT");
        cmd.addAll(gptArgs);
        Process p = new ProcessBuilder(cmd).directory(cwd.toFile()).redirectErrorStream(true).start();
        p.getOutputStream().close();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread pump = new Thread(() -> {
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    long room = MAX_CAPTURE_BYTES - captured.size();
                    if (room > 0) {
                        captured.write(buf, 0, (int) Math.min(n, room));
                    }
                }
            } catch (IOException ignored) {
                // the stream closes when the process ends or is killed
            }
        });
        pump.setDaemon(true);
        pump.start();
        boolean done = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            p.waitFor();
        }
        pump.join(2_000);
        String text = captured.toString(StandardCharsets.UTF_8).trim();
        if (!done) {
            return new SubstrateResult(false, truncate("timed out after " + timeoutSeconds + "s\n" + text), -1, metadata(kind, gptArgs.get(0)));
        }
        int exit = p.exitValue();
        return new SubstrateResult(exit == 0, truncate(text), exit, metadata(kind, gptArgs.get(0)));
    }

    private SubstrateResult readFile(SubstrateSession session, Path target) throws IOException {
        Path file = resolveWithinWorkspace(session.getWorkspaceRoot(), target);
        return new SubstrateResult(true, truncate(Files.readString(file, StandardCharsets.UTF_8)), 0, Map.of("path", file.toString()));
    }

    private SubstrateResult listDirectory(SubstrateSession session, Path target) throws IOException {
        Path dir = resolveWithinWorkspace(session.getWorkspaceRoot(), target);
        try (var stream = Files.list(dir)) {
            List<String> entries = stream.map(p -> p.getFileName().toString()).sorted().limit(100).toList();
            return new SubstrateResult(true, String.join(System.lineSeparator(), entries), 0, Map.of("path", dir.toString()));
        }
    }

    private Path resolveWithinWorkspace(Path root, Path target) throws IOException {
        Path resolved = (target == null ? root : root.resolve(target)).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Path escapes workspace: " + target);
        }
        return resolved;
    }

    private static String truncate(String s) {
        return s.length() > MAX_OUTPUT_CHARS ? s.substring(0, MAX_OUTPUT_CHARS) : s;
    }

    private static SubstrateResult fail(String message) {
        return new SubstrateResult(false, message, -1, Map.of("adapter", ADAPTER_ID));
    }

    private static Map<String, String> metadata(String kind, String command) {
        return Map.of("adapter", ADAPTER_ID, "kind", kind, "command", command, "executor", "process-builder");
    }
}
