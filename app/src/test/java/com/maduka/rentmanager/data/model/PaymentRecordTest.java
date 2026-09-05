package com.maduka.rentmanager.data.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class PaymentRecordTest {
    @Test
    public void recorderCannotActionTheirOwnPayment() {
        PaymentRecord p = new PaymentRecord();
        p.setRecordedByUid("admin-elias");

        assertFalse(p.canBeActionedBy("admin-elias"));
        assertTrue(p.canBeActionedBy("admin-sarah"));
        assertFalse(p.canBeActionedBy(null));
    }
}
