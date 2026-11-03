package ma.entraide.tawassol.imports;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.common.ApiException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;

/** Lit un fichier .xlsx ou .csv en lignes de texte. Le type est reconnu par le contenu, pas par l'extension. */
public final class SheetReader {
    public record Reglages(String encodage, char separateur, int ligneEntete) {
        public static Reglages defaut() { return new Reglages("UTF-8", ';', 1); }
    }
    public record Feuille(List<String> colonnes, List<Map<String, String>> lignes, List<Integer> numeros) {}

    private SheetReader() {}

    public static Feuille lire(byte[] contenu, Reglages r) {
        if (contenu.length >= 4 && contenu[0] == 'P' && contenu[1] == 'K') return xlsx(contenu, r);
        if (contenu.length >= 4 && (contenu[0] & 0xFF) == 0xD0 && (contenu[1] & 0xFF) == 0xCF) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "Ancien format .xls non pris en charge : enregistrez en .xlsx ou .csv");
        return csv(contenu, r);
    }

    private static Feuille xlsx(byte[] contenu, Reglages r) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenu))) {
            Sheet sheet = wb.getSheetAt(0);
            Row head = sheet.getRow(r.ligneEntete() - 1);
            if (head == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_FILE", "Ligne d'en-tête introuvable");
            List<String> cols = new ArrayList<>();
            for (int c = 0; c < head.getLastCellNum(); c++) cols.add(texte(head.getCell(c)));
            List<Map<String, String>> lignes = new ArrayList<>();
            List<Integer> numeros = new ArrayList<>();
            for (int i = r.ligneEntete(); i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> m = new LinkedHashMap<>();
                boolean vide = true;
                for (int c = 0; c < cols.size(); c++) {
                    String v = texte(row.getCell(c));
                    if (!v.isEmpty()) vide = false;
                    if (!cols.get(c).isEmpty()) m.put(cols.get(c), v);
                }
                if (!vide) { lignes.add(m); numeros.add(i + 1); }
            }
            return new Feuille(cols, lignes, numeros);
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException a) throw a;
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNREADABLE_FILE", "Fichier Excel illisible");
        }
    }

    private static Feuille csv(byte[] contenu, Reglages r) {
        try {
            int off = contenu.length >= 3 && (contenu[0] & 0xFF) == 0xEF && (contenu[1] & 0xFF) == 0xBB && (contenu[2] & 0xFF) == 0xBF ? 3 : 0;
            try (CSVParser p = CSVFormat.DEFAULT.builder().setDelimiter(r.separateur()).setTrim(true).setIgnoreEmptyLines(true).build()
                    .parse(new InputStreamReader(new ByteArrayInputStream(contenu, off, contenu.length - off), Charset.forName(r.encodage())))) {
                List<CSVRecord> recs = p.getRecords();
                if (recs.size() < r.ligneEntete()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_FILE", "Fichier vide");
                CSVRecord head = recs.get(r.ligneEntete() - 1);
                List<String> cols = new ArrayList<>();
                head.forEach(cols::add);
                List<Map<String, String>> lignes = new ArrayList<>();
                List<Integer> numeros = new ArrayList<>();
                for (int i = r.ligneEntete(); i < recs.size(); i++) {
                    CSVRecord rec = recs.get(i);
                    Map<String, String> m = new LinkedHashMap<>();
                    for (int c = 0; c < cols.size(); c++) if (!cols.get(c).isEmpty()) m.put(cols.get(c), c < rec.size() ? rec.get(c) : "");
                    lignes.add(m);
                    numeros.add((int) rec.getRecordNumber());
                }
                return new Feuille(cols, lignes, numeros);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNREADABLE_FILE", "Fichier CSV illisible (vérifiez l'encodage et le séparateur)");
        }
    }

    private static String texte(Cell c) {
        if (c == null) return "";
        CellType t = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        return switch (t) {
            case STRING -> c.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(c)
                    ? c.getDateCellValue().toInstant().atZone(ZoneId.of("Africa/Casablanca")).toLocalDate().toString()
                    : new BigDecimal(String.valueOf(c.getNumericCellValue())).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default -> "";
        };
    }

    // ---- valeurs ----

    public static BigDecimal decimal(String s, char sepDecimal) {
        String t = s.replace(" ", "").replace(" ", "");
        t = sepDecimal == ',' ? t.replace(".", "").replace(',', '.') : t.replace(",", "");
        return new BigDecimal(t);
    }

    public static LocalDate date(String s, String format) {
        try { return LocalDate.parse(s, java.time.format.DateTimeFormatter.ofPattern(format)); }
        catch (RuntimeException e) { return LocalDate.parse(s); }
    }
}
