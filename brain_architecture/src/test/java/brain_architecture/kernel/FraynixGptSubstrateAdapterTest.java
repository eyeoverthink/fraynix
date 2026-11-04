package brain_architecture.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real FraynixGPT workbench. Set FRAYNIX_GPT_CLASSES to a directory holding the compiled FraynixGPT
 * class (javac -d <dir> FraynixGPT.java from eyeoverthink/fraynix-gpt). Tests that need it are skipped, not
 * passed, when it is unset. The inference test also needs FRAYNIX_GPT_MODEL (a model.bin with its .tokens file).
 */
class FraynixGptSubstrateAdapterTest {
    private static Path classes() {
        String v = System.getenv("FRAYNIX_GPT_CLASSES");
        assumeTrue(v != null && Files.isRegularFile(Path.of(v, "FraynixGPT.class")), "FRAYNIX_GPT_CLASSES not set");
        return Path.of(v);
    }

    private static SubstrateSession session(FraynixGptSubstrateAdapter a) {
        TaskEnvelope t = TaskEnvelope.builder("check the workbench").build();
        return a.loadContext(t, List.of());
    }

    @Test
    void adapterIdAndStateRoundTrip(@TempDir Path ws) {
        FraynixGptSubstrateAdapter a = new FraynixGptSubstrateAdapter(ws, ws, 5);
        SubstrateSession s = session(a);
        assertEquals("fraynix-gpt", a.getAdapterId());
        assertEquals("check the workbench", s.getState().get("goal"));
        a.step(s, "one");
        SubstrateSession back = a.restore(a.snapshot(s));
        assertEquals(s.getSessionId(), back.getSessionId());
        assertEquals("one", back.getState().get("lastStep"));
    }

    @Test
    void refusesWhatItShould(@TempDir Path ws) {
        FraynixGptSubstrateAdapter a = new FraynixGptSubstrateAdapter(ws, ws, 5);
        SubstrateSession s = session(a);
        SubstrateResult denied = a.act(s, SubstrateAction.runCommand(List.of("rm", "-rf", "x")));
        assertFalse(denied.isSuccess());
        assertTrue(denied.getOutput().contains("not allowed"));
        assertFalse(a.act(s, SubstrateAction.runCommand(List.of())).isSuccess());
        SubstrateResult out = a.act(s, SubstrateAction.readFile(Path.of("..", "outside.txt")));
        assertFalse(out.isSuccess());
        assertTrue(out.getOutput().contains("escapes"));
        assertFalse(a.act(s, SubstrateAction.runInference("hi", null, 8)).isSuccess());
        assertTrue(a.act(s, SubstrateAction.runInference("hi", "nope.bin", 8)).getOutput().contains("not found"));
        assertTrue(a.act(s, SubstrateAction.runInference("hi", "../../etc/model.bin", 8)).getOutput().contains("escapes"));
        assertThrows(IllegalArgumentException.class, () -> new FraynixGptSubstrateAdapter(ws, ws, 0));
    }

    @Test
    void readsAndListsInsideTheWorkspace(@TempDir Path ws) throws IOException {
        Files.writeString(ws.resolve("a.txt"), "hello");
        FraynixGptSubstrateAdapter a = new FraynixGptSubstrateAdapter(ws, ws, 5);
        SubstrateSession s = session(a);
        assertEquals("hello", a.act(s, SubstrateAction.readFile(Path.of("a.txt"))).getOutput());
        assertTrue(a.act(s, SubstrateAction.listDirectory(null)).getOutput().contains("a.txt"));
    }

    @Test
    void vsaCommandRunsTheRealWorkbench(@TempDir Path ws) {
        FraynixGptSubstrateAdapter a = new FraynixGptSubstrateAdapter(ws, classes(), 120);
        SubstrateSession s = session(a);
        SubstrateResult ok = a.act(s, SubstrateAction.runCommand(List.of("vsa", "n=32", "noise=0.2")));
        assertTrue(ok.isSuccess(), ok.getOutput());
        assertEquals(0, ok.getExitCode());
        assertTrue(ok.getOutput().contains("32/32 recalled"));
        SubstrateResult lost = a.act(s, SubstrateAction.runCommand(List.of("vsa", "n=32", "noise=0.5")));
        assertFalse(lost.isSuccess());
        assertEquals(1, lost.getExitCode());
    }

    @Test
    void exodusGateAcceptsAndRefuses(@TempDir Path ws) throws IOException {
        FraynixGptSubstrateAdapter a = new FraynixGptSubstrateAdapter(ws, classes(), 120);
        SubstrateSession s = session(a);
        Files.writeString(ws.resolve("p.txt"), "MODE 3\nSTR \"HDC\"\nHANG\n");
        SubstrateResult ok = a.act(s, SubstrateAction.runCommand(List.of("hdcx86", "srcfile=p.txt", "noise=0.1", "out=boot.img")));
        assertTrue(ok.isSuccess(), ok.getOutput());
        assertTrue(Files.exists(ws.resolve("boot.img")));
        SubstrateResult no = a.act(s, SubstrateAction.runCommand(List.of("hdcx86", "srcfile=p.txt", "noise=0.45", "out=bad.img")));
        assertFalse(no.isSuccess());
        assertTrue(no.getOutput().contains("REFUSED"));
        assertFalse(Files.exists(ws.resolve("bad.img")));
    }

    @Test
    void timeoutKillsTheChild(@TempDir Path ws) {
        FraynixGptSubstrateAdapter a = new FraynixGptSubstrateAdapter(ws, classes(), 1);
        long t0 = System.nanoTime();
        SubstrateResult r = a.act(session(a), SubstrateAction.runCommand(List.of("selftest")));
        long seconds = (System.nanoTime() - t0) / 1_000_000_000L;
        assertFalse(r.isSuccess());
        assertTrue(r.getOutput().startsWith("timed out"));
        assertTrue(seconds < 30, "the child must be killed at the limit, took " + seconds + "s");
    }

    @Test
    void inferenceIsDeterministic(@TempDir Path ws) throws IOException {
        String m = System.getenv("FRAYNIX_GPT_MODEL");
        assumeTrue(m != null && Files.isRegularFile(Path.of(m)), "FRAYNIX_GPT_MODEL not set");
        Files.copy(Path.of(m), ws.resolve("model.bin"));
        Files.copy(Path.of(m + ".tokens"), ws.resolve("model.bin.tokens"));
        FraynixGptSubstrateAdapter a = new FraynixGptSubstrateAdapter(ws, classes(), 300);
        SubstrateSession s = session(a);
        SubstrateResult r1 = a.act(s, SubstrateAction.runInference("The ", "model.bin", 24));
        SubstrateResult r2 = a.act(s, SubstrateAction.runInference("The ", "model.bin", 24));
        assertTrue(r1.isSuccess(), r1.getOutput());
        assertEquals(r1.getOutput(), r2.getOutput());
        assertTrue(r1.getOutput().startsWith("The "));
        assertTrue(r1.getOutput().length() > 4);
        SubstrateResult r3 = a.act(s, SubstrateAction.runInference("A different start: ", "model.bin", 24));
        assertNotEquals(r1.getOutput(), r3.getOutput());
    }
}
