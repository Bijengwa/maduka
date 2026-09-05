// LoginKeyUtilTest.java
package com.maduka.rentmanager.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class LoginKeyUtilTest {
    @Test
    public void sanitize_lowercasesAndStripsRtdbIllegalChars() {
        assertEquals("elias_nyoni_gmail_com", LoginKeyUtil.sanitize("Elias.Nyoni@Gmail.com"));
    }

    @Test
    public void sanitize_stripsNonDigitsFromPhoneNumbers() {
        assertEquals("0713220441", LoginKeyUtil.sanitize("0713 220 441"));
    }

    @Test
    public void sanitize_lowercasesUsernames() {
        assertEquals("elias", LoginKeyUtil.sanitize("Elias"));
    }
}
