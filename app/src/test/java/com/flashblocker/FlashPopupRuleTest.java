package com.flashblocker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FlashPopupRuleTest {

    private static final FlashPopupRule RULE = new FlashPopupRule(
        "Dispositivo de teste",
        "com.exemplo.mensagens",
        "ClassZeroActivity",
        "class 0 message",
        new String[] { "cancel", "descartar" });

    // ---- matches() ----

    @Test
    public void pacoteDiferenteNuncaCasa() {
        assertFalse(RULE.matches("com.outro.app",
            "com.outro.app.ClassZeroActivity", "class 0 message"));
    }

    @Test
    public void casaPorPacoteEClasse() {
        assertTrue(RULE.matches("com.exemplo.mensagens",
            "com.exemplo.mensagens.ui.ClassZeroActivity", ""));
    }

    @Test
    public void classeRenomeadaCasaPeloTextoNoMesmoPacote() {
        assertTrue(RULE.matches("com.exemplo.mensagens",
            "com.exemplo.mensagens.ui.OutraActivity", "um class 0 message chegou"));
    }

    @Test
    public void mesmoPacoteSemClasseNemTextoNaoCasa() {
        assertFalse(RULE.matches("com.exemplo.mensagens",
            "com.exemplo.mensagens.ui.OutraActivity", "texto qualquer"));
    }

    @Test
    public void regraSemClasseCasaSoPeloTexto() {
        FlashPopupRule r = new FlashPopupRule("t", "pkg", null, "marca",
            new String[] { "ok" });
        assertTrue(r.matches("pkg", "qualquer.Classe", "tem a marca aqui"));
        assertFalse(r.matches("pkg", "qualquer.Classe", "nada relevante"));
    }

    @Test
    public void regraSemTextoCasaSoPelaClasse() {
        FlashPopupRule r = new FlashPopupRule("t", "pkg", "Cls", null,
            new String[] { "ok" });
        assertTrue(r.matches("pkg", "a.Cls", "x"));
        assertFalse(r.matches("pkg", "a.Outra", "x"));
    }

    // ---- match() na lista KNOWN ----

    @Test
    public void matchEncontraSamsungOneUi() {
        FlashPopupRule r = FlashPopupRule.match(
            "com.samsung.android.messaging",
            "com.samsung.android.messaging.ui.view.classzero.ClassZeroActivity",
            "");
        assertNotNull(r);
        assertEquals("Samsung One UI", r.label);
    }

    @Test
    public void matchRetornaNullQuandoNadaCasa() {
        assertNull(FlashPopupRule.match("com.qualquer.app",
            "com.qualquer.app.Dialog", "ola"));
    }

    // ---- isDismissLabel() ----

    @Test
    public void labelDeDescarteCasaPorSubstring() {
        assertTrue(RULE.isDismissLabel("cancel"));
        assertTrue(RULE.isDismissLabel("botao descartar tudo"));
    }

    @Test
    public void labelIrrelevanteNaoCasa() {
        assertFalse(RULE.isDismissLabel("save"));
    }

    @Test
    public void labelNuloOuVazioNaoCasa() {
        assertFalse(RULE.isDismissLabel(null));
        assertFalse(RULE.isDismissLabel(""));
    }
}
