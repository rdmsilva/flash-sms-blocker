package com.flashblocker;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class BlockStatsTest {

    private FakeSharedPreferences prefs;
    private BlockStats stats;

    @Before
    public void setUp() {
        prefs = new FakeSharedPreferences();
        stats = new BlockStats(prefs);
    }

    @Test
    public void valoresPadrao() {
        assertEquals(0, stats.getBlockedCount());
        assertEquals("nunca", stats.getLastBlockedTime());
        assertEquals("nenhum", stats.getLastBlockedSender());
        assertEquals("Nenhum evento ainda", stats.getLog());
        assertFalse(stats.isLearningMode());
    }

    @Test
    public void recordBlockIncrementaContadorEAtualizaResumo() {
        stats.recordBlock("CLASS0", "fechado");
        stats.recordBlock("CLASS0", "fechado de novo");
        assertEquals(2, stats.getBlockedCount());
        assertNotEquals("nunca", stats.getLastBlockedTime());
        assertTrue(stats.getLastBlockedSender().contains("fechado de novo"));
        assertTrue(stats.getLog().contains("fechado"));
    }

    @Test
    public void recordEventRegistraNoLogSemIncrementarContador() {
        stats.recordEvent("LEARN", "janela x");
        assertEquals(0, stats.getBlockedCount());
        assertTrue(stats.getLog().contains("janela x"));
    }

    @Test
    public void logFicaDoMaisRecenteParaOMaisAntigo() {
        stats.recordEvent("A", "primeiro");
        stats.recordEvent("B", "segundo");
        String log = stats.getLog();
        assertTrue(log.indexOf("segundo") < log.indexOf("primeiro"));
    }

    /** Regressão do bug em que o log congelava ao atingir 50 linhas. */
    @Test
    public void logGuardaNoMaximo50EDescartaOsMaisAntigos() {
        for (int i = 1; i <= 55; i++) {
            stats.recordEvent("E", "evento " + i);
        }
        String log = stats.getLog();
        String[] lines = log.split("\n");
        assertEquals(50, lines.length);
        // O mais novo está lá, no topo...
        assertTrue(lines[0].contains("evento 55"));
        // ...e os 5 mais antigos (1..5) foram descartados.
        assertFalse(log.contains("evento 5\n"));
        assertFalse(log.contains("evento 1\n"));
    }

    @Test
    public void linhasVaziasNoLogSalvoSaoIgnoradas() {
        // A linha em branco precisa estar no MEIO (split() descarta as do fim).
        prefs.edit().putString("event_log", "linha antiga\n\noutra linha\n").apply();
        stats.recordEvent("E", "novo evento");
        String log = stats.getLog();
        assertTrue(log.contains("novo evento"));
        assertTrue(log.contains("linha antiga"));
        assertTrue(log.contains("outra linha"));
        assertFalse(log.contains("\n\n"));
    }

    @Test
    public void clearZeraTudoMasPreservaModoAprendizado() {
        stats.setLearningMode(true);
        stats.recordBlock("CLASS0", "x");
        stats.clear();
        assertEquals(0, stats.getBlockedCount());
        assertEquals("nunca", stats.getLastBlockedTime());
        assertEquals("nenhum", stats.getLastBlockedSender());
        assertEquals("Nenhum evento ainda", stats.getLog());
        assertTrue(stats.isLearningMode());
    }

    @Test
    public void modoAprendizadoLigaEDesliga() {
        stats.setLearningMode(true);
        assertTrue(stats.isLearningMode());
        stats.setLearningMode(false);
        assertFalse(stats.isLearningMode());
    }

    @Test
    public void pausaComecaDesligada() {
        assertFalse(stats.isPaused());
    }

    @Test
    public void pausaLigaEDesliga() {
        stats.setPaused(true);
        assertTrue(stats.isPaused());
        stats.setPaused(false);
        assertFalse(stats.isPaused());
    }

    @Test
    public void clearPreservaPausa() {
        stats.setPaused(true);
        stats.recordBlock("CLASS0", "x");
        stats.clear();
        assertTrue(stats.isPaused());
    }

    @Test
    public void pauseUntilComecaZerado() {
        assertEquals(0L, stats.getPauseUntil());
    }

    @Test
    public void pauseUntilGuardaOTimestamp() {
        stats.setPauseUntil(123456789L);
        assertEquals(123456789L, stats.getPauseUntil());
    }
}
