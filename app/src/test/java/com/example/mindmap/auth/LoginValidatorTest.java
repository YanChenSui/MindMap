package com.example.mindmap.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LoginValidatorTest {
    @Test
    public void usernameMustContainVisibleCharacters() {
        assertFalse(LoginValidator.isValidUsername(null));
        assertFalse(LoginValidator.isValidUsername("   "));
        assertTrue(LoginValidator.isValidUsername("小村"));
        assertEquals("xiaocun22", LoginValidator.normalizeUsername("  xiaocun22  "));
    }

    @Test
    public void passwordMustContainAtLeastSixCharacters() {
        assertFalse(LoginValidator.isValidPassword(null));
        assertFalse(LoginValidator.isValidPassword("12345"));
        assertTrue(LoginValidator.isValidPassword("123456"));
    }
}
