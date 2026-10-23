package br.casa.sala;

import java.util.Calendar;

/** Approximate local day period for the theme, without a baked-in user's location. */
final class Sun {
    static final int MANHA = 0, TARDE = 1, ENTARDECER = 2, NOITE = 3;

    private Sun() {}

    static int period(Calendar now) {
        double h = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0;
        double rise = 6.0, set = 18.0;
        if (h < rise || h >= set + 0.75) return NOITE;
        if (h >= set - 0.75) return ENTARDECER;
        return h < 12 ? MANHA : TARDE;
    }
}
