package ma.entraide.tawassol.finance;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Relevé de paie informatif, bilingue (arabe en RTL), généré à partir des données publiées. Ne remplace pas le bulletin officiel. */
@Service
public class BulletinPdfService {
    public record Ligne(String libelleFr, String libelleAr, String sens, BigDecimal montant) {}
    public record Bulletin(String matricule, String nomFr, String nomAr, String gradeFr, String gradeAr, int annee, int mois,
                           BigDecimal net, BigDecimal allocFamiliale, BigDecimal retenueMutuelle, BigDecimal rappel, List<Ligne> lignes) {}

    private static final String[] MOIS_FR = {"janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"};
    private static final String[] MOIS_AR = {"يناير", "فبراير", "مارس", "أبريل", "ماي", "يونيو", "يوليوز", "غشت", "شتنبر", "أكتوبر", "نونبر", "دجنبر"};

    private final byte[] fontBytes;

    public BulletinPdfService() {
        try (InputStream in = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            this.fontBytes = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Police du bulletin introuvable", e);
        }
    }

    public byte[] generer(Bulletin b, LocalDate genereLe) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();
            BaseFont base = BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
            Font normal = new Font(base, 10), small = new Font(base, 8, Font.NORMAL, new Color(90, 100, 95)), title = new Font(base, 14, Font.BOLD);
            Font head = new Font(base, 9, Font.BOLD, new Color(11, 93, 75));

            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.addCell(cell("Royaume du Maroc\nEntraide Nationale", normal, false, Element.ALIGN_LEFT, false));
            header.addCell(cell("المملكة المغربية\nالتعاون الوطني", normal, true, Element.ALIGN_RIGHT, false));
            doc.add(header);

            PdfPTable t = new PdfPTable(2);
            t.setWidthPercentage(100);
            t.setSpacingBefore(14);
            t.addCell(cell("Relevé de paie · " + MOIS_FR[b.mois() - 1] + " " + b.annee(), title, false, Element.ALIGN_LEFT, false));
            t.addCell(cell("كشف الأجر · " + MOIS_AR[b.mois() - 1] + " " + b.annee(), title, true, Element.ALIGN_RIGHT, false));
            doc.add(t);

            PdfPTable id = trio();
            id.setSpacingBefore(12);
            row(id, "Matricule", b.matricule(), "رقم التأجير", normal, head);
            row(id, "Nom", b.nomFr(), "الاسم", normal, head);
            row(id, "Nom (arabe)", b.nomAr(), "الاسم بالعربية", normal, head);
            row(id, "Grade", nz(b.gradeFr()), "الدرجة", normal, head);
            doc.add(id);

            NumberFormat nf = NumberFormat.getNumberInstance(Locale.FRANCE);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            PdfPTable m = trio();
            m.setSpacingBefore(16);
            row(m, "Salaire net", nf.format(b.net()) + " MAD", "الأجر الصافي", normal, head);
            row(m, "Allocations familiales", nf.format(b.allocFamiliale()) + " MAD", "التعويضات العائلية", normal, head);
            row(m, "Retenue mutuelle", nf.format(b.retenueMutuelle()) + " MAD", "اقتطاع التعاضدية", normal, head);
            row(m, "Rappel", nf.format(b.rappel()) + " MAD", "المتأخرات", normal, head);
            for (Ligne l : b.lignes()) {
                row(m, l.libelleFr() + ("retenue".equals(l.sens()) ? " (retenue)" : ""), nf.format(l.montant()) + " MAD", l.libelleAr() == null ? "" : l.libelleAr(), normal, head);
            }
            doc.add(m);

            PdfPTable f = new PdfPTable(1);
            f.setWidthPercentage(100);
            f.setSpacingBefore(28);
            f.addCell(cell("Relevé informatif généré par TAWASSOL le " + genereLe + ". Il ne remplace pas le bulletin de paie officiel. (Mention à valider avec le service financier.)", small, false, Element.ALIGN_LEFT, false));
            f.addCell(cell("كشف إخباري صادر عن تواصل بتاريخ " + genereLe + ". لا يعوض كشف الأجر الرسمي. (الصيغة قيد المصادقة مع المصلحة المالية.)", small, true, Element.ALIGN_RIGHT, false));
            doc.add(f);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Génération du relevé impossible", e);
        }
    }

    private PdfPTable trio() throws Exception {
        PdfPTable t = new PdfPTable(new float[]{3.2f, 3f, 3.2f});
        t.setWidthPercentage(100);
        return t;
    }

    private void row(PdfPTable t, String fr, String valeur, String ar, Font normal, Font head) {
        t.addCell(cell(fr, head, false, Element.ALIGN_LEFT, true));
        t.addCell(cell(valeur, normal, false, Element.ALIGN_CENTER, true));
        t.addCell(cell(ar, head, true, Element.ALIGN_RIGHT, true));
    }

    private PdfPCell cell(String text, Font font, boolean rtl, int align, boolean border) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(6);
        if (rtl) { c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL); c.setHorizontalAlignment(Element.ALIGN_LEFT); }
        if (!border) c.setBorder(PdfPCell.NO_BORDER);
        else { c.setBorderColor(new Color(211, 221, 216)); c.setBorderWidth(0.6f); }
        return c;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
