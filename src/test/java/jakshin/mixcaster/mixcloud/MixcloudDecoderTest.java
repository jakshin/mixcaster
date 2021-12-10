/*
 * Copyright (C) 2016 Jason Jackson
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jakshin.mixcaster.mixcloud;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the MixcloudDecoder class.
 */
class MixcloudDecoderTest {
    private MixcloudDecoder instance;

    @BeforeEach
    void setUp() {
        this.instance = new MixcloudDecoder();
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void decodeUrlWorks() {
        String url = "ITItPyZtbmEnIDogID9sZz49KzcjKDAwfiImKWs8KywhNip4LWMidSVpZHtiKWZrbHpgNGt8I2l8eG" +
                     "J2emd5fHZwYXlwNTZmeXp+cWQ0JHlxdC14fHopezZxPyYmeRIoAwQEFjItfwANCHAKHgoyBTI3AAU=";
        String expResult = "https://stream8.mixcloud.com/secure/c/m4a/64/d/3/6/a/" +
                           "5e03-5743-4313-9fb5-5940de050b63.m4a?sig=TzLII_jn3OXL9LGEgRsyTQ";
        String result = instance.decodeUrl(url);
        assertEquals(expResult, result);
    }
}
