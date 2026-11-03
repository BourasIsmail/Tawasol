package ma.entraide.tawassol.access;

import java.time.LocalDate;
import java.util.Set;
import ma.entraide.tawassol.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerimeterServiceIT extends IntegrationTestBase {
    @Autowired PerimeterService perimeters;

    @Test void perimetreStructureInclutLesEnfantsRecursivement() {
        String d = data.delegation("P1");
        String a = data.structure(d, null, "A"), b = data.structure(d, a, "B"), c = data.structure(d, b, "C"), x = data.structure(d, null, "X");
        String resp = data.personnel(a, "Resp");
        data.perimeter(resp, "structure", b, LocalDate.of(2020, 1, 1), null, true);
        assertEquals(Set.of(b, c), perimeters.structureIdsInPerimeter(resp, true));
    }

    @Test void perimetreCoordinationCouvreToutesLesDelegations() {
        String d1 = data.delegation("P2"), d2 = data.delegation("P3");
        String s1 = data.structure(d1, null, "A1"), s2 = data.structure(d2, null, "A2");
        String resp = data.personnel(s1, "Resp");
        String coord = jdbc.queryForObject("SELECT coordination_id FROM delegation_province WHERE id = ?", String.class, d1);
        data.perimeter(resp, "coordination", coord, LocalDate.of(2020, 1, 1), null, true);
        assertEquals(Set.of(s1, s2), perimeters.structureIdsInPerimeter(resp, true));
    }

    @Test void unAgentSansSuppleanceNAPasDePerimetre() {
        String d = data.delegation("P4");
        String s = data.structure(d, null, "B1");
        String agent = data.personnel(s, "Agent");
        assertEquals(Set.of(), perimeters.structureIdsInPerimeter(agent, false));
    }

    @Test void unCycleDansLArbreNeBouclePas() {
        String d = data.delegation("P5");
        String a = data.structure(d, null, "C1"), b = data.structure(d, a, "C2");
        jdbc.update("UPDATE structure SET parent_id = ? WHERE id = ?", b, a);
        String resp = data.personnel(a, "Resp");
        data.perimeter(resp, "structure", a, LocalDate.of(2020, 1, 1), null, true);
        assertEquals(Set.of(a, b), perimeters.structureIdsInPerimeter(resp, true));
    }
}
