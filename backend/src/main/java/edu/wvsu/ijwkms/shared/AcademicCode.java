package edu.wvsu.ijwkms.shared;

import java.text.Normalizer;
import java.util.Locale;

public final class AcademicCode {

    private AcademicCode() {}

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);
    }
}
