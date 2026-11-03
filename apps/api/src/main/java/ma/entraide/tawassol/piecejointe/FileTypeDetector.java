package ma.entraide.tawassol.piecejointe;

/** Détermine le vrai type d'un fichier d'après son contenu (signature), sans se fier au nom ni au type déclaré. */
public final class FileTypeDetector {
    private FileTypeDetector() {}

    public static String detect(byte[] b) {
        if (b == null || b.length < 4) return null;
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "application/pdf";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "image/png";
        return null;
    }
}
