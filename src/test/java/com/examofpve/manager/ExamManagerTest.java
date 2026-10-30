package com.examofpve.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExamManagerTest {

    @Test
    void formatsMultipliersWithoutFloatingNoise() {
        assertEquals("2.38", ExamManager.formatMultiplier(1.4 * 1.7));
        assertEquals("2.89", ExamManager.formatMultiplier(1.7 * 1.7));
        assertEquals("1", ExamManager.formatMultiplier(1.0));
        assertEquals("2", ExamManager.formatMultiplier(2.0));
    }
}
