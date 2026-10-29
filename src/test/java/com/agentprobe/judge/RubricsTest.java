package com.agentprobe.judge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RubricsTest {

    @Test
    void libraryNamesResolveCaseInsensitively() {
        assertThat(Rubrics.resolve("Correctness")).contains("5 =").contains("1 =");
        assertThat(Rubrics.resolve(" refusal ")).contains("declines");
    }

    @Test
    void everyLibraryRubricHasAnchors() {
        for (String name : Rubrics.names()) {
            String text = Rubrics.resolve(name);
            assertThat(text).as(name).contains("5 =").contains("3 =").contains("1 =");
        }
    }

    @Test
    void customTextGetsADefaultScale() {
        String text = Rubrics.resolve("The agent must apologise for the delay.");
        assertThat(text).contains("apologise").contains("Scale: 5 =");
    }
}