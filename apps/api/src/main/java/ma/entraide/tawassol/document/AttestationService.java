package ma.entraide.tawassol.document;

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
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import ma.entraide.tawassol.audit.AuditService;
import ma.entraide.tawassol.common.ApiException;
import ma.entraide.tawassol.deplacement.MontantEnLettres;
import ma.entraide.tawassol.deplacement.VerificationService;
import ma.entraide.tawassol.param.NumeroService;
import ma.entraide.tawassol.param.ParametreService;
import ma.entraide.tawassol.security.AuthenticatedUser;
import ma.entraide.tawassol.security.Role;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attestations de travail et de salaire : PDF bilingue, numéroté (ATT-AAAA-NNNNNN), avec QR de vérification publique.
 * Émise une seule fois par demande approuvée ; le fichier émis est archivé tel quel et les téléchargements suivants le restituent.
 * La signature est celle du service RH (paramètre daté, à valider) : aucun nom de personne n'est écrit dans le code.
 */
@Service
public class AttestationService {
    public record Pdf(byte[] contenu, String nomFichier, String numero) {}

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color TRAIT = new Color(190, 200, 195);

    private final JdbcTemplate jdbc;
    private final NumeroService numeros;
    private final ParametreService params;
    private final VerificationService verification;
    private final AuditService audit;
    private final Clock clock;
    private final byte[] amiri, amiriBold, noto, notoBold, logo;

    public AttestationService(JdbcTemplate jdbc, NumeroService numeros, ParametreService params, VerificationService verification, AuditService audit, Clock clock) {
        this.jdbc = jdbc; this.numeros = numeros; this.params = params; this.verification = verification; this.audit = audit; this.clock = clock;
        this.amiri = res("/fonts/Amiri_400Regular.ttf"); this.amiriBold = res("/fonts/Amiri_700Bold.ttf"); this.noto = res("/fonts/NotoSans_400Regular.ttf"); this.notoBold = res("/fonts/NotoSans_700Bold.ttf"); this.logo = res("/logo.png");
    }

    private static byte[] res(String p) {
        try (InputStream in = AttestationService.class.getResourceAsStream(p)) { return in.readAllBytes(); } catch (Exception e) { throw new IllegalStateException("Ressource introuvable : " + p, e); }
    }

    @Transactional
    public Pdf attestation(AuthenticatedUser u, String demandeId) {
        List<Map<String, Object>> dl = jdbc.queryForList("""
                SELECT d.id, d.personnel_id, d.statut, d.objet, d.donnees_json, t.code FROM demande d JOIN type_demande t ON t.id = d.type_demande_id WHERE d.id = ?""", demandeId);
        if (dl.isEmpty()) throw ApiException.notFound();
        Map<String, Object> d = dl.get(0);
        String pid = (String) d.get("personnel_id");
        // l'agent concerné et la RH seulement ; pour tout autre : 404, on ne révèle pas l'existence
        if (!(pid.equals(u.personnelId()) || u.role() == Role.RH)) throw ApiException.notFound();
        if (!"ATTESTATION".equals(d.get("code"))) throw ApiException.notFound();
        if (!"approuvee".equals(d.get("statut"))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_INDISPONIBLE", "L'attestation n'est émise qu'après l'approbation de la demande");

        List<Map<String, Object>> existant = jdbc.queryForList("SELECT contenu, numero FROM document_officiel WHERE demande_id = ?", demandeId);
        if (!existant.isEmpty()) return new Pdf((byte[]) existant.get(0).get("contenu"), "ATTESTATION-" + existant.get(0).get("numero") + ".pdf", (String) existant.get(0).get("numero"));

        String typeAtt = d.get("donnees_json") != null && d.get("donnees_json").toString().contains("\"salaire\"") ? "salaire" : "travail";
        Map<String, Object> p = jdbc.queryForMap("""
                SELECT p.matricule, p.nom_fr, p.prenom_fr, p.nom_ar, p.prenom_ar, p.sexe, p.date_recrutement, g.libelle_fr AS grade_fr, g.libelle_ar AS grade_ar,
                       s.nom_fr AS structure_fr, s.nom_ar AS structure_ar, dp.nom_deleg_fr AS deleg_fr, dp.nom_deleg_ar AS deleg_ar
                FROM personnel p JOIN structure s ON s.id = p.structure_id JOIN delegation_province dp ON dp.id = s.delegation_id LEFT JOIN grade g ON g.id = p.grade_id WHERE p.id = ?""", pid);
        BigDecimal net = null; String periode = null;
        if ("salaire".equals(typeAtt)) {
            List<Map<String, Object>> sl = jdbc.queryForList("SELECT salaire_net, annee, mois FROM salaire WHERE personnel_id = ? AND statut = 'publie' ORDER BY annee DESC, mois DESC LIMIT 1", pid);
            if (sl.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AUCUN_BULLETIN_PUBLIE", "Aucun bulletin publié : l'attestation de salaire ne peut pas être établie");
            net = (BigDecimal) sl.get(0).get("salaire_net"); periode = String.format("%02d/%d", ((Number) sl.get(0).get("mois")).intValue(), ((Number) sl.get(0).get("annee")).intValue());
        }
        LocalDate today = LocalDate.now(clock);
        String numero = numeros.suivant("ATT", today.getYear());
        String documentId = java.util.UUID.randomUUID().toString();
        byte[] pdf = produire(documentId, numero, typeAtt, p, net, periode, today, (String) d.get("objet"));
        try {
            jdbc.update("INSERT INTO document_officiel (id, type, numero, demande_id, personnel_id, emis_par, sha256, contenu) VALUES (?,?,?,?,?,?,?,?)", documentId, "salaire".equals(typeAtt) ? "attestation_salaire" : "attestation_travail", numero, demandeId, pid, u.userId(),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)), pdf);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        audit.record(u.userId(), "ATTESTATION_EMISE", "document_officiel", documentId, null, "{\"numero\":\"" + numero + "\"}");
        return new Pdf(pdf, "ATTESTATION-" + numero + ".pdf", numero);
    }

    private byte[] produire(String documentId, String numero, String type, Map<String, Object> p, BigDecimal net, String periode, LocalDate date, String destinataire) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 56, 56, 36, 60);
            PdfWriter w = PdfWriter.getInstance(doc, out);
            BaseFont ar = BaseFont.createFont("Amiri-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, amiri, null), arB = BaseFont.createFont("Amiri-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, amiriBold, null);
            BaseFont fr = BaseFont.createFont("NotoSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, noto, null), frB = BaseFont.createFont("NotoSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, notoBold, null);
            Font frN = new Font(fr, 10.5f), frT = new Font(frB, 15), arN = new Font(ar, 14), arT = new Font(arB, 22), small = new Font(fr, 7.5f, Font.NORMAL, new Color(90, 100, 95));
            String url = verification.url(documentId, VerificationService.ATTESTATION, numero);
            w.setPageEvent(new PdfPageEventHelper() {
                @Override public void onEndPage(PdfWriter wr, Document d) {
                    PdfContentByte cb = wr.getDirectContent(); cb.setColorStroke(TRAIT); cb.moveTo(d.left(), d.bottom() - 8); cb.lineTo(d.right(), d.bottom() - 8); cb.stroke();
                    ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase("Vérification : " + url, small), d.left(), d.bottom() - 22, 0);
                }
            });
            doc.addTitle("Attestation " + numero); doc.addCreator("TAWASSOL");
            doc.open();
            Image img = Image.getInstance(logo); img.scaleToFit(480, 85); img.setAlignment(Element.ALIGN_CENTER); doc.add(img);

            boolean femme = "F".equals(p.get("sexe"));
            PdfPTable num = new PdfPTable(2); num.setWidthPercentage(100); num.setSpacingBefore(14);
            num.addCell(cell("N° " + numero, frN, false, Element.ALIGN_LEFT)); num.addCell(cell("الرقم : " + numero, arN, true, Element.ALIGN_LEFT));
            doc.add(num);
            PdfPTable titre = new PdfPTable(1); titre.setWidthPercentage(100); titre.setSpacingBefore(18); titre.setSpacingAfter(14);
            titre.addCell(cell("salaire".equals(type) ? "شهادة أجر" : "شهادة عمل", arT, true, Element.ALIGN_CENTER));
            titre.addCell(cell("salaire".equals(type) ? "ATTESTATION DE SALAIRE" : "ATTESTATION DE TRAVAIL", frT, false, Element.ALIGN_CENTER));
            doc.add(titre);

            String nomFr = (femme ? "Madame " : "Monsieur ") + p.get("prenom_fr") + " " + p.get("nom_fr");
            String nomAr = (femme ? "السيدة " : "السيد ") + p.get("prenom_ar") + " " + p.get("nom_ar");
            String depuisFr = p.get("date_recrutement") == null ? "" : ", recruté" + (femme ? "e" : "") + " le " + D.format(((java.sql.Date) p.get("date_recrutement")).toLocalDate());
            String depuisAr = p.get("date_recrutement") == null ? "" : "، المعين بتاريخ " + D.format(((java.sql.Date) p.get("date_recrutement")).toLocalDate());
            String corpsFr = "Le service des ressources humaines de l'Entraide Nationale atteste que " + nomFr + ", matricule " + p.get("matricule") + ", grade « " + nz(p.get("grade_fr")) + " »" + depuisFr
                    + ", est en activité au sein de la structure « " + p.get("structure_fr") + " » (" + p.get("deleg_fr") + ").";
            String corpsAr = "يشهد مصلحة الموارد البشرية للتعاون الوطني أن " + nomAr + "، رقم التأجير " + p.get("matricule") + "، الدرجة « " + nz(p.get("grade_ar")) + " »" + depuisAr + "، يزاول مهامه بـ « " + p.get("structure_ar") + " » (" + p.get("deleg_ar") + ").";
            if (net != null) {
                corpsFr += " L'intéressé" + (femme ? "e" : "") + " perçoit un salaire net mensuel de " + net.toPlainString() + " MAD (" + MontantEnLettres.fr(net) + "), selon le dernier bulletin publié (" + periode + ").";
                corpsAr += " ويتقاضى أجرا صافيا شهريا قدره " + net.toPlainString() + " درهم (" + MontantEnLettres.ar(net) + ")، حسب آخر كشف منشور (" + periode + ").";
            }
            corpsFr += "\nCette attestation est délivrée à l'intéressé" + (femme ? "e" : "") + " pour servir et valoir ce que de droit" + (destinataire == null || destinataire.isBlank() ? "." : " (destinataire : " + destinataire.trim() + ").");
            corpsAr += "\nسلمت هذه الشهادة للمعني بالأمر لما يقتضيه الحال.";
            PdfPTable corps = new PdfPTable(1); corps.setWidthPercentage(100);
            PdfPCell cf = cell(corpsFr, new Font(fr, 11), false, Element.ALIGN_JUSTIFIED); cf.setLeading(0, 1.5f); corps.addCell(cf);
            PdfPCell ca = cell(corpsAr, new Font(ar, 15), true, Element.ALIGN_LEFT); ca.setLeading(0, 1.5f); ca.setPaddingTop(14); corps.addCell(ca);
            doc.add(corps);

            LocalDate effet = date;
            PdfPTable sig = new PdfPTable(new float[]{4, 1.5f, 4}); sig.setWidthPercentage(100); sig.setSpacingBefore(26);
            sig.addCell(cell("Fait à Rabat, le " + D.format(effet) + "\n" + params.get("attestation.signataire_fr", effet).orElse("") + "\n\n\n", frN, false, Element.ALIGN_LEFT));
            BitMatrix bm = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 220, 220, Map.of(com.google.zxing.EncodeHintType.MARGIN, 1));
            java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(bm.getWidth(), bm.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < bm.getWidth(); x++) for (int y = 0; y < bm.getHeight(); y++) bi.setRGB(x, y, bm.get(x, y) ? 0 : 0xFFFFFF);
            ByteArrayOutputStream qb = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(bi, "PNG", qb);
            Image qr = Image.getInstance(qb.toByteArray()); qr.scaleToFit(72, 72);
            PdfPCell qc = new PdfPCell(qr, false); qc.setBorder(Rectangle.NO_BORDER); qc.setHorizontalAlignment(Element.ALIGN_CENTER); sig.addCell(qc);
            sig.addCell(cell("حرر بالرباط في " + D.format(effet) + "\n" + params.get("attestation.signataire_ar", effet).orElse("") + "\n\n\n", arN, true, Element.ALIGN_LEFT));
            doc.add(sig);
            doc.close();
            return out.toByteArray();
        } catch (ApiException e) { throw e; } catch (Exception e) { throw new IllegalStateException("Génération de l'attestation impossible", e); }
    }

    private static String nz(Object o) { return o == null ? "" : o.toString(); }

    private PdfPCell cell(String t, Font f, boolean rtl, int align) {
        PdfPCell c = new PdfPCell(new Phrase(t, f)); c.setBorder(Rectangle.NO_BORDER); c.setPadding(5); c.setHorizontalAlignment(align);
        if (rtl) c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        return c;
    }
}
