package info.bitrich.xchangestream.core;

import java.util.Collections;
import java.util.Set;
import org.knowm.xchange.instrument.Instrument;

public class KlineSubscription {

    private final Set<Instrument> klines;

    public KlineSubscription() {
        this.klines = Collections.emptySet();
    }

    public KlineSubscription(Set<Instrument> klines) {
        this.klines = klines;
    }

    public Set<Instrument> getKlines() {
        return klines;
    }

    public boolean isEmpty() {
        return klines.isEmpty();
    }
}
