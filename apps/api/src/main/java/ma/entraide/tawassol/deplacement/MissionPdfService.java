package ma.entraide.tawassol.deplacement;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Documents officiels des missions, bilingues : ordre de mission, état récapitulatif des sommes dues, état kilométrique.
 * Polices libres embarquées (Amiri pour l'arabe, Noto Sans pour le français). QR code = jeton public de vérification.
 * Signataire : celui qui est habilité à la date de l'ordre (signataire_autorisation), jamais un nom écrit dans le code.
 */
@Service
public class MissionPdfService {
    public record Pdf(byte[] contenu, String nomFichier, String sha256) {}

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy"), H = DateTimeFormatter.ofPattern("HH:mm");
    private static final Color VERT = new Color(12, 71, 151), GRIS = new Color(90, 102, 117), TRAIT = new Color(190, 198, 210);

    private final JdbcTemplate jdbc;
    private final VerificationService verification;
    private final byte[] amiri, amiriBold, noto, notoBold, logo;

    public MissionPdfService(JdbcTemplate jdbc, VerificationService verification) {
        this.jdbc = jdbc; this.verification = verification;
        this.amiri = res("/fonts/Amiri_400Regular.ttf"); this.amiriBold = res("/fonts/Amiri_700Bold.ttf");
        this.noto = res("/fonts/NotoSans_400Regular.ttf"); this.notoBold = res("/fonts/NotoSans_700Bold.ttf");
        this.logo = res("/logo.png");
    }

    private static byte[] res(String p) {
        try (InputStream in = MissionPdfService.class.getResourceAsStream(p)) { return in.readAllBytes(); }
        catch (Exception e) { throw new IllegalStateException("Ressource introuvable : " + p, e); }
    }

    private static final String SQL = """
            SELECT om.*, p.matricule, p.nom_fr, p.prenom_fr, p.nom_ar, p.prenom_ar, g.libelle_fr AS grade_fr, g.libelle_ar AS grade_ar, g.echelle AS grade_echelle,
                   s.delegation_id AS agent_delegation_id, dag.adresse AS deleg_adresse, dag.telephone AS deleg_tel, dag.coordination_id AS agent_coordination_id,
                   dd.nom_deleg_fr AS depart_fr, dd.nom_deleg_ar AS depart_ar, da.nom_deleg_fr AS arrivee_fr, da.nom_deleg_ar AS arrivee_ar,
                   mt.code AS moyen_code, mt.libelle_fr AS moyen_fr, mt.libelle_ar AS moyen_ar, pf.libelle_fr AS puissance_fr
            FROM ordre_mission om
            JOIN personnel p ON p.id = om.personnel_id JOIN structure s ON s.id = p.structure_id JOIN delegation_province dag ON dag.id = s.delegation_id
            LEFT JOIN grade g ON g.id = p.grade_id
            LEFT JOIN delegation_province dd ON dd.id = om.depart_delegation_id LEFT JOIN delegation_province da ON da.id = om.arrival_delegation_id
            LEFT JOIN moyen_transport mt ON mt.id = om.moyen_transport_id LEFT JOIN puissance_fiscale pf ON pf.id = om.puissance_fiscale_id
            WHERE om.id = ? AND om.supprime_le IS NULL""";

    public Pdf ordreDeMission(String id) {
        Map<String, Object> m = charger(id);
        if (!List.of("approuvee", "finalisee", "payee").contains(m.get("statut"))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_INDISPONIBLE", "L'ordre de mission n'est émis qu'après l'approbation");
        return produire(m, "ordre", "ORDRE-DE-MISSION-" + numeroOrdre(m).replace('/', '-'));
    }

    public Pdf etat(String id) {
        Map<String, Object> m = charger(id);
        if (!List.of("finalisee", "payee").contains(m.get("statut"))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_INDISPONIBLE", "L'état d'indemnités n'existe qu'après la finalisation de la mission");
        return produire(m, "etat", "ETAT-" + numeroEtat(m).replace('/', '-'));
    }

    public Pdf etatKilometrique(String id) {
        Map<String, Object> m = charger(id);
        if (!List.of("finalisee", "payee").contains(m.get("statut"))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_INDISPONIBLE", "L'état kilométrique n'existe qu'après la finalisation de la mission");
        if (!"voiture_privee".equals(m.get("moyen_code")) || dec(m.get("montant_km")).signum() <= 0) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_INDISPONIBLE", "Pas d'indemnité kilométrique pour cette mission");
        return produire(m, "etat-km", "ETAT-K" + numeroEtat(m).replace('/', '-'));
    }

    private Map<String, Object> charger(String id) {
        List<Map<String, Object>> l = jdbc.queryForList(SQL, id);
        if (l.isEmpty()) throw ApiException.notFound();
        return l.get(0);
    }

    private static String s(Object o) { return o == null ? "" : o.toString(); }
    private static BigDecimal dec(Object o) { return o == null ? BigDecimal.ZERO : new BigDecimal(o.toString()); }
    private static LocalDateTime dt(Object o) { return o == null ? null : o instanceof java.sql.Timestamp t ? t.toLocalDateTime() : (LocalDateTime) o; }
    private static String numeroOrdre(Map<String, Object> m) { return m.get("numero_ordm") != null ? s(m.get("numero_ordm")) : s(m.get("numero_ordm_legacy")); }
    private static String numeroEtat(Map<String, Object> m) { return m.get("numero_etat") != null ? s(m.get("numero_etat")) : s(m.get("numero_etat_legacy")); }

    private Pdf produire(Map<String, Object> m, String type, String nom) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 36, 60);
            PdfWriter w = PdfWriter.getInstance(doc, out);
            Fonts f = new Fonts();
            String numero = type.startsWith("etat") ? numeroEtat(m) : numeroOrdre(m);
            boolean verifiable = type.startsWith("etat") ? m.get("numero_etat") != null : m.get("numero_ordm") != null;
            String url = verifiable ? verification.url(s(m.get("id")), type, s(type.startsWith("etat") ? m.get("numero_etat") : m.get("numero_ordm"))) : null;
            w.setPageEvent(new Pied(f, m, url, numero));
            doc.addTitle(nom); doc.addCreator("TAWASSOL");
            doc.open();
            entete(doc);
            switch (type) {
                case "ordre" -> corpsOrdre(doc, f, m, url, numero);
                case "etat" -> corpsEtat(doc, f, m, url, numero, false);
                default -> corpsEtat(doc, f, m, url, numero, true);
            }
            doc.close();
            byte[] b = out.toByteArray();
            return new Pdf(b, nom + ".pdf", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)));
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Génération du PDF impossible", e); }
    }

    // ------------------------------------------------------------------ mise en page

    private final class Fonts {
        final BaseFont ar, arB, fr, frB;
        final Font arN, arT, arS, frN, frT, frS, frH;
        Fonts() throws Exception {
            ar = BaseFont.createFont("Amiri-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, amiri, null);
            arB = BaseFont.createFont("Amiri-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, amiriBold, null);
            fr = BaseFont.createFont("NotoSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, noto, null);
            frB = BaseFont.createFont("NotoSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, notoBold, null);
            arN = new Font(ar, 13); arT = new Font(arB, 20); arS = new Font(ar, 10, Font.NORMAL, GRIS);
            frN = new Font(fr, 9.5f); frT = new Font(frB, 13); frS = new Font(fr, 7.5f, Font.NORMAL, GRIS); frH = new Font(frB, 8.5f, Font.NORMAL, VERT);
        }
    }

    private void entete(Document doc) throws Exception {
        Image img = Image.getInstance(logo);
        img.scaleToFit(480, 85); img.setAlignment(Element.ALIGN_CENTER);
        doc.add(img);
        doc.add(new Paragraph(" "));
    }

    private final class Pied extends PdfPageEventHelper {
        final Fonts f; final Map<String, Object> m; final String url, numero;
        Pied(Fonts f, Map<String, Object> m, String url, String numero) { this.f = f; this.m = m; this.url = url; this.numero = numero; }
        @Override public void onEndPage(PdfWriter w, Document d) {
            PdfContentByte cb = w.getDirectContent();
            cb.setColorStroke(TRAIT); cb.setLineWidth(0.5f); cb.moveTo(d.left(), d.bottom() - 8); cb.lineTo(d.right(), d.bottom() - 8); cb.stroke();
            String adr = s(m.get("deleg_adresse")), tel = s(m.get("deleg_tel"));
            String l1 = (adr + (tel.isEmpty() ? "" : "  -  Tél. " + tel)).trim();
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(l1, f.frS), d.left(), d.bottom() - 20, 0);
            String ver = url == null ? "Document hérité de Suivi RH : référence " + numero : "Vérification : " + url;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(ver, f.frS), d.left(), d.bottom() - 32, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("Page " + w.getPageNumber(), f.frS), d.right(), d.bottom() - 32, 0);
        }
    }

    private PdfPCell cellule(String t, Font font, boolean rtl, int align, boolean bord) {
        PdfPCell c = new PdfPCell(new Phrase(t, font));
        c.setPadding(5); c.setHorizontalAlignment(align);
        if (rtl) { c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL); }
        if (bord) { c.setBorderColor(TRAIT); c.setBorderWidth(0.6f); } else c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private Image qr(String url) throws Exception {
        BitMatrix bm = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 220, 220, Map.of(com.google.zxing.EncodeHintType.MARGIN, 1));
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(bm.getWidth(), bm.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < bm.getWidth(); x++) for (int y = 0; y < bm.getHeight(); y++) img.setRGB(x, y, bm.get(x, y) ? 0x000000 : 0xFFFFFF);
        javax.imageio.ImageIO.write(img, "PNG", o);
        Image i = Image.getInstance(o.toByteArray()); i.scaleToFit(72, 72);
        return i;
    }

    private String signataire(Map<String, Object> m, boolean arabe) {
        LocalDate date = m.get("date_ordm") != null ? ((java.sql.Date) m.get("date_ordm")).toLocalDate() : dt(m.get("date_depart")).toLocalDate();
        List<Map<String, Object>> l = jdbc.queryForList("""
                SELECT nom_fr, nom_ar, qualite_fr, qualite_ar FROM signataire_autorisation
                WHERE ((portee = 'delegation' AND delegation_id = ?) OR (portee = 'coordination' AND coordination_id = ?)) AND date_debut <= ? AND (date_fin IS NULL OR date_fin >= ?)
                ORDER BY (portee = 'delegation') DESC, date_debut DESC LIMIT 1""", m.get("agent_delegation_id"), m.get("agent_coordination_id"), date, date);
        if (l.isEmpty()) return arabe ? "(المُوقِّع غير محدد)" : "(signataire non renseigné)";
        Map<String, Object> r = l.get(0);
        String q = s(r.get(arabe ? "qualite_ar" : "qualite_fr")), n = s(r.get(arabe ? "nom_ar" : "nom_fr"));
        return n.isBlank() ? q : q + "\n" + n;
    }

    // ------------------------------------------------------------------ ordre de mission

    private void ligne(PdfPTable t, Fonts f, String libFr, String valFr, String libAr, String valAr) {
        t.addCell(cellule(libFr + " : " + valFr, f.frN, false, Element.ALIGN_LEFT, true));
        t.addCell(cellule(libAr + " : " + valAr, f.arN, true, Element.ALIGN_LEFT, true));
    }

    private void corpsOrdre(Document doc, Fonts f, Map<String, Object> m, String url, String numero) throws Exception {
        LocalDate dateOrdre = m.get("date_ordm") == null ? null : ((java.sql.Date) m.get("date_ordm")).toLocalDate();
        PdfPTable num = new PdfPTable(2); num.setWidthPercentage(100);
        num.addCell(cellule("N° " + numero, f.frT, false, Element.ALIGN_LEFT, false));
        num.addCell(cellule("الرقم : " + numero + "    التاريخ : " + (dateOrdre == null ? "" : D.format(dateOrdre)), f.arN, true, Element.ALIGN_LEFT, false));
        doc.add(num);
        PdfPTable titre = new PdfPTable(1); titre.setWidthPercentage(100); titre.setSpacingBefore(6); titre.setSpacingAfter(10);
        titre.addCell(cellule("تكليف بمهمة", new Font(f.arB, 22), true, Element.ALIGN_CENTER, false));
        titre.addCell(cellule("ORDRE DE MISSION", f.frT, false, Element.ALIGN_CENTER, false));
        doc.add(titre);

        LocalDateTime d = dt(m.get("date_depart")), r = dt(m.get("date_retour"));
        PdfPTable t = new PdfPTable(new float[]{1, 1}); t.setWidthPercentage(100);
        ligne(t, f, "Agent", s(m.get("nom_fr")) + " " + s(m.get("prenom_fr")), "السيد(ة)", s(m.get("prenom_ar")) + " " + s(m.get("nom_ar")));
        ligne(t, f, "Matricule", s(m.get("matricule")), "الرقم الترتيبي", s(m.get("matricule")));
        ligne(t, f, "Grade", s(m.get("grade_fr")), "الإطار", s(m.get("grade_ar")));
        ligne(t, f, "Destination", s(m.get("arrivee_fr")), "بالتوجه إلى", s(m.get("arrivee_ar")));
        ligne(t, f, "Objet", s(m.get("objet")), "موضوع التنقل", s(m.get("objet")));
        ligne(t, f, "Moyen de transport", s(m.get("moyen_fr")), "وسيلة النقل", s(m.get("moyen_ar")));
        ligne(t, f, "Départ", d == null ? "" : D.format(d) + " à " + H.format(d), "الذهاب", d == null ? "" : D.format(d) + "  " + H.format(d));
        ligne(t, f, "Retour prévu", r == null ? "" : D.format(r) + " à " + H.format(r), "الرجوع", r == null ? "" : D.format(r) + "  " + H.format(r));
        doc.add(t);

        PdfPTable sig = new PdfPTable(new float[]{4, 1.4f, 4}); sig.setWidthPercentage(100); sig.setSpacingBefore(14);
        sig.addCell(cellule("Le signataire\n" + signataire(m, false) + "\n\n\n", f.frN, false, Element.ALIGN_LEFT, false));
        PdfPCell q = url == null ? cellule("", f.frN, false, Element.ALIGN_CENTER, false) : new PdfPCell(qr(url), false);
        q.setBorder(Rectangle.NO_BORDER); q.setHorizontalAlignment(Element.ALIGN_CENTER); sig.addCell(q);
        sig.addCell(cellule("توقيع المدير أو من ينوب عنه\n" + signataire(m, true) + "\n\n\n", f.arN, true, Element.ALIGN_LEFT, false));
        doc.add(sig);

        PdfPTable att = new PdfPTable(new float[]{1, 1}); att.setWidthPercentage(100); att.setSpacingBefore(10);
        att.addCell(cellule("ATTESTATION DE PRÉSENCE\nService / Délégation / Coordination : ........................\nDate d'arrivée : ................   Date de départ : ................\nSignature et cachet du responsable", f.frN, false, Element.ALIGN_LEFT, true));
        att.addCell(cellule("شهادة الحضور\nالقسم / المصلحة / المنسقية / المندوبية : ........................\nتاريخ الوصول : ................   تاريخ المغادرة : ................\nتوقيع وخاتم المسؤول", f.arN, true, Element.ALIGN_LEFT, true));
        doc.add(att);
    }

    // ------------------------------------------------------------------ états

    private void corpsEtat(Document doc, Fonts f, Map<String, Object> m, String url, String numero, boolean km) throws Exception {
        Paragraph titre = new Paragraph("ÉTAT RÉCAPITULATIF DES SOMMES DUES" + (km ? "\nINDEMNITÉS KILOMÉTRIQUES" : ""), f.frT); titre.setAlignment(Element.ALIGN_CENTER); titre.setSpacingAfter(8);
        doc.add(titre);
        PdfPTable id = new PdfPTable(2); id.setWidthPercentage(100);
        id.addCell(cellule("État N° : " + (km ? "K" : "") + numero, f.frN, false, Element.ALIGN_LEFT, false));
        id.addCell(cellule("Matricule : " + s(m.get("matricule")), f.frN, false, Element.ALIGN_LEFT, false));
        id.addCell(cellule("Mr/Mme : " + s(m.get("nom_fr")) + " " + s(m.get("prenom_fr")), f.frN, false, Element.ALIGN_LEFT, false));
        id.addCell(cellule("Grade : " + s(m.get("grade_fr")), f.frN, false, Element.ALIGN_LEFT, false));
        if (km) { id.addCell(cellule("Voiture : " + s(m.get("vehicule_nom")) + " " + s(m.get("vehicule_immatriculation")), f.frN, false, Element.ALIGN_LEFT, false)); id.addCell(cellule("Puissance fiscale : " + s(m.get("puissance_fr")), f.frN, false, Element.ALIGN_LEFT, false)); }
        doc.add(id);
        doc.add(new Paragraph(km ? "Pour les indemnités kilométriques suivantes :" : "Pour les indemnités de déplacement suivantes :", f.frN));

        LocalDateTime d = dt(m.get("date_depart")), r = dt(m.get("date_retour"));
        String lieu = s(m.get("depart_fr")) + " - " + s(m.get("arrivee_fr"));
        String ordre = "N° " + numeroOrdre(m) + (m.get("date_ordm") == null ? "" : "\ndu " + D.format(((java.sql.Date) m.get("date_ordm")).toLocalDate()));
        BigDecimal total;
        PdfPTable t;
        if (km) {
            t = new PdfPTable(new float[]{2, 1.4f, 2.6f, 1.4f, 1.6f, 1.2f, 1.4f}); t.setWidthPercentage(100); t.setSpacingBefore(6);
            for (String h : new String[]{"Ordre de mission", "Date", "Lieux", "Voiture", "Distance x trajets", "Taux/km", "Décompte"}) t.addCell(entete(h, f));
            total = dec(m.get("montant_km"));
            t.addCell(c(ordre, f)); t.addCell(c(D.format(d) + (r == null ? "" : "\n" + D.format(r)), f)); t.addCell(c(lieu, f));
            t.addCell(c(s(m.get("vehicule_nom")) + "\n" + s(m.get("vehicule_immatriculation")), f));
            t.addCell(c(s(m.get("distance_km")) + " x " + s(m.get("nombre_trajets")), f)); t.addCell(c(s(m.get("taux_km")) + " DH", f)); t.addCell(c(total.toPlainString() + " DH", f));
        } else {
            t = new PdfPTable(new float[]{1.7f, 1.6f, 2.1f, 1.0f, 1.0f, 1.2f, 1.3f, 1.2f, 1.4f}); t.setWidthPercentage(100); t.setSpacingBefore(6);
            for (String h : new String[]{"Ordre de mission", "Date", "Lieux", "Heure départ", "Heure retour", "Nombre de repas", "Taux indemnité", "Frais transport", "Décompte total"}) t.addCell(entete(h, f));
            int r1 = ((Number) m.get("nombre_repas_taux_1")).intValue(), r2 = ((Number) m.get("nombre_repas_taux_2")).intValue();
            BigDecimal frais = "transports_en_commun".equals(m.get("moyen_code")) ? dec(m.get("frais_transport")) : BigDecimal.ZERO;
            total = dec(m.get("montant_indemnites")).add(frais);
            t.addCell(c(ordre, f)); t.addCell(c(D.format(d) + (r == null ? "" : "\n" + D.format(r)), f)); t.addCell(c(lieu, f));
            t.addCell(c(H.format(d), f)); t.addCell(c(r == null ? "" : H.format(r), f));
            t.addCell(c(r1 + (r2 > 0 ? "\n" + r2 : ""), f));
            t.addCell(c(dec(m.get("indemnite_taux_1")).toPlainString() + " DH" + (r2 > 0 ? "\n" + dec(m.get("indemnite_taux_2")).toPlainString() + " DH" : ""), f));
            t.addCell(c(frais.signum() > 0 ? frais.toPlainString() + " DH" : "", f)); t.addCell(c(total.toPlainString() + " DH", f));
        }
        doc.add(t);

        Paragraph tot = new Paragraph("Total : " + total.toPlainString() + " DH", f.frT); tot.setAlignment(Element.ALIGN_RIGHT); tot.setSpacingBefore(8);
        doc.add(tot);
        Paragraph lf = new Paragraph("Arrêté le présent état à la somme de : " + MontantEnLettres.fr(total) + ".", f.frN); lf.setSpacingBefore(4);
        doc.add(lf);
        PdfPTable la = new PdfPTable(1); la.setWidthPercentage(100);
        la.addCell(cellule("أُوقف هذا البيان في مبلغ : " + MontantEnLettres.ar(total) + ".", f.arN, true, Element.ALIGN_LEFT, false));
        doc.add(la);

        PdfPTable sig = new PdfPTable(new float[]{3, 3, 1.4f, 3}); sig.setWidthPercentage(100); sig.setSpacingBefore(14);
        sig.addCell(cellule("L'Ordonnateur\n(Sous-ordonnateurs)\n" + signataire(m, false) + "\n\n\n", f.frN, false, Element.ALIGN_CENTER, false));
        sig.addCell(cellule("L'Intéressé(e)\n" + s(m.get("nom_fr")) + " " + s(m.get("prenom_fr")) + "\n\n\n", f.frN, false, Element.ALIGN_CENTER, false));
        PdfPCell q = url == null ? cellule("", f.frN, false, Element.ALIGN_CENTER, false) : new PdfPCell(qr(url), false);
        q.setBorder(Rectangle.NO_BORDER); q.setHorizontalAlignment(Element.ALIGN_CENTER); sig.addCell(q);
        sig.addCell(cellule("", f.frN, false, Element.ALIGN_CENTER, false));
        doc.add(sig);
    }

    private PdfPCell entete(String t, Fonts f) {
        PdfPCell c = new PdfPCell(new Phrase(t, f.frH)); c.setBackgroundColor(new Color(232, 238, 247)); c.setPadding(4); c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBorderColor(TRAIT); c.setBorderWidth(0.6f); return c;
    }

    private PdfPCell c(String t, Fonts f) {
        PdfPCell c = new PdfPCell(new Phrase(t, new Font(f.fr, 8.5f))); c.setPadding(4); c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBorderColor(TRAIT); c.setBorderWidth(0.6f); return c;
    }
}
