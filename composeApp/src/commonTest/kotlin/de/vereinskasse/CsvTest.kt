package de.vereinskasse

import de.vereinskasse.logik.*
import de.vereinskasse.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CsvTest {
    private val verein = Verein(
        "v", "TSV",
        gruppen = listOf(Gruppe("g1", "Getränke", GRUPPEN_FARBEN[0]), Gruppe("g2", "Essen", 0xFF123456), Gruppe("g3", "Leer", GRUPPEN_FARBEN[8])),
        artikel = listOf(
            Artikel("a1", "Bier 0,5", 350, "g1", 200),
            Artikel("a2", "Bratwurst", 400, "g2", sichtbar = false),
            Artikel("a3", "Sekt \"Hausmarke\"; trocken", 1250, "g1"),
            Artikel("a4", "Lose", 100, null),
        ),
    )

    @Test
    fun exportUndWiederImport() {
        val csv = verein.alsCsv()
        assertTrue(csv.startsWith("﻿Gruppe;Artikel;Preis;Pfand;Sichtbar;Farbe\r\n"))
        assertTrue(csv.contains("Getränke;Bier 0,5;3,50;2,00;ja;blau\r\n"))
        assertTrue(csv.contains("\"Sekt \"\"Hausmarke\"\"; trocken\""))
        assertTrue(csv.contains("Essen;Bratwurst;4,00;;nein;#123456"))
        assertTrue(csv.contains("Leer;;;;;pink"))

        val e = csvLesen(textDekodieren(csv.encodeToByteArray()))
        assertEquals(emptyList(), e.fehler)
        assertEquals(listOf("Getränke", "Essen", "Leer"), e.gruppen.map { it.name })
        assertEquals(listOf("Bier 0,5", "Sekt \"Hausmarke\"; trocken", "Bratwurst", "Lose"), e.artikel.map { it.name })
        assertEquals(0xFF123456, e.gruppen[1].farbe)
        assertEquals(false, e.artikel[2].sichtbar)
        assertEquals(null, e.artikel[3].gruppeId)
        assertEquals(1250L, e.artikel[1].preisCent)
    }

    @Test
    fun excelWindowsKodierungUndKomma() {
        // Windows-1252: "Getränke" mit ä = 0xE4, Preis mit Punkt, Komma als Trenner, Spalten vertauscht
        val text = "Preis,Artikel,Gruppe\r\n2.50,Käse,Getränke\r\n\"3,50 €\",Würstl,\r\n"
        val bytes = text.map { if (it == '€') 0x80.toByte() else it.code.toByte() }.toByteArray()
        val dekodiert = textDekodieren(bytes)
        assertEquals(text, dekodiert)
        val e = csvLesen(dekodiert)
        assertEquals(listOf("Käse", "Würstl"), e.artikel.map { it.name })
        assertEquals(listOf(250L, 350L), e.artikel.map { it.preisCent })
        assertEquals(listOf("Getränke"), e.gruppen.map { it.name })
        assertEquals(false, e.hatSichtbarSpalte)
    }

    @Test
    fun fehlerhafteZeilen() {
        val text = """
            Gruppe;Artikel;Preis;Pfand;Sichtbar;Farbe
            Getränke;Bier;3,50;2;ja;blau
            Getränke;;2,00;;;
            Getränke;Cola;abc;;;
            Getränke;Bier;4,00;;;
            Essen;Wurst;4;;vielleicht;lila-grün
            ;;;;;
            Getränke;Wasser;-1;;;
        """.trimIndent()
        val e = csvLesen(text)
        assertEquals(listOf("Bier", "Wurst"), e.artikel.map { it.name })
        assertEquals(4, e.fehler.size)
        assertTrue(e.fehler[0].startsWith("Zeile 3"))
        assertEquals(2, e.hinweise.size)
        assertTrue(e.artikel[1].sichtbar)
        assertFailsWith<CsvFehler> { csvLesen("Name;Kosten\nBier;3") }
        assertFailsWith<CsvFehler> { csvLesen("") }
    }

    @Test
    fun ergaenzenUndErsetzen() {
        val e = csvLesen("Gruppe;Artikel;Preis;Farbe\nGetränke;Bier 0,5;3,80;\nKuchen;Torte;2,50;\nGetränke;Radler;3,50;rot\n")
        val ergaenzt = verein.csvUebernehmen(e, ersetzen = false)
        assertEquals(listOf("Getränke", "Essen", "Leer", "Kuchen"), ergaenzt.gruppen.map { it.name })
        // Farbe von Getränke in der Datei als "rot" angegeben (Radler-Zeile)
        assertEquals(GRUPPEN_FARBEN[1], ergaenzt.gruppen[0].farbe)
        // Kuchen ohne Farbe: eine noch freie Farbe
        assertTrue(ergaenzt.gruppen[3].farbe !in ergaenzt.gruppen.take(3).map { it.farbe })
        val bier = ergaenzt.artikel.first { it.name == "Bier 0,5" }
        assertEquals("a1", bier.id)
        assertEquals(380L, bier.preisCent)
        assertEquals(0L, bier.pfandCent)
        assertEquals(6, ergaenzt.artikel.size)
        // Bratwurst bleibt ausgeblendet (keine Sichtbar-Spalte)
        assertEquals(false, ergaenzt.artikel.first { it.name == "Bratwurst" }.sichtbar)

        val ersetzt = verein.csvUebernehmen(e, ersetzen = true)
        assertEquals(listOf("Getränke", "Kuchen"), ersetzt.gruppen.map { it.name })
        assertEquals("g1", ersetzt.gruppen[0].id)
        assertEquals(listOf("Bier 0,5", "Torte", "Radler"), ersetzt.artikel.map { it.name })
        assertEquals("a1", ersetzt.artikel[0].id)
        assertEquals("g1", ersetzt.artikel[2].gruppeId)
    }

    @Test
    fun hilfen() {
        assertEquals("TSV Musterdorf", vereinsnameAusDatei("content/TSV_Musterdorf_Artikel.csv"))
        assertEquals(GRUPPEN_FARBEN[2], farbeLesen("Grün"))
        assertEquals(0xFFAABBCC, farbeLesen("#aabbcc"))
        assertEquals(";", trennerErkennen("a;b,c;d").toString())
        assertEquals("\t", trennerErkennen("a\tb\tc").toString())
    }
}
