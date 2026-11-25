package com.portfolio.profile.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CvServiceTest
{
    @Test
    void downloadNamesAreCleanedUp()
    {
        assertThat(CvService.safeName("CV Lloyd.pdf")).isEqualTo("CV Lloyd.pdf");
        assertThat(CvService.safeName("C:\\Users\\moi\\Résumé.PDF")).isEqualTo("Résumé.pdf");
        assertThat(CvService.safeName("../../etc/passwd")).isEqualTo("passwd.pdf");
        assertThat(CvService.safeName("a\"b;c\r\nd.pdf")).isEqualTo("abcd.pdf");
        assertThat(CvService.safeName("...pdf")).isEqualTo("CV.pdf");
        assertThat(CvService.safeName(null)).isEqualTo("CV.pdf");
        assertThat(CvService.safeName("x".repeat(300) + ".pdf")).hasSize(124);
    }
}
