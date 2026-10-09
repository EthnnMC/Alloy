package dev.alloy.remap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SrgNameTableTest {

    private static final SrgNameTable TABLE = SrgNameTable.of(Map.of("func_71410_x", "getMinecraft"));

    @Test
    void translatesKnownSrgName() {
        assertEquals("getMinecraft", SrgNameTableTest.TABLE.runtimeName("func_71410_x"));
        assertEquals(Optional.of("getMinecraft"), SrgNameTableTest.TABLE.find("func_71410_x"));
    }

    @Test
    void leavesAnyOtherNameUnchanged() {
        assertEquals("func_175744_a", SrgNameTableTest.TABLE.runtimeName("func_175744_a"));
        assertEquals("toString", SrgNameTableTest.TABLE.runtimeName("toString"));
        assertEquals(Optional.empty(), SrgNameTableTest.TABLE.find("toString"));
    }

    @Test
    void doesNotChangeWhenTheSourceMapChangesLater() {
        Map<String, String> source = new HashMap<>(Map.of("field_1_a", "first"));
        SrgNameTable table = SrgNameTable.of(source);

        source.put("field_2_b", "second");

        assertEquals(1, table.size());
        assertThrows(UnsupportedOperationException.class, () -> table.asMap().put("field_3_c", "third"));
    }
}
