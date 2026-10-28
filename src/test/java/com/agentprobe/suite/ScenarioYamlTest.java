package com.agentprobe.suite;

import com.agentprobe.api.ApiException;
import com.agentprobe.suite.ScenarioYaml.ParsedSuite;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioYamlTest {

    private final ScenarioYaml yaml = new ScenarioYaml();

    @Test
    void parsesSuiteScenariosTagsAndEvaluators() {
        String text = """
                suite: order-support-bot
                description: Basic checks
                scenarios:
                  - id: status-shipped
                    input: "What is the status of order 1043?"
                    tags: [smoke, orders]
                    evaluators:
                      - type: contains
                        value: shipped
                      - type: tool_call
                        name: getOrderStatus
                        args: { orderId: 1043 }
                """;
        ParsedSuite parsed = yaml.parse(text);

        assertThat(parsed.name()).isEqualTo("order-support-bot");
        assertThat(parsed.scenarios()).hasSize(1);
        assertThat(parsed.scenarios().get(0).externalId()).isEqualTo("status-shipped");
        assertThat(parsed.scenarios().get(0).tags()).containsExactly("smoke", "orders");
        assertThat(parsed.scenarios().get(0).evaluators()).hasSize(2);
        assertThat(parsed.scenarios().get(0).evaluators().get(0).get("type")).isEqualTo("contains");
    }

    @Test
    void missingSuiteNameIsRejected() {
        assertThatThrownBy(() -> yaml.parse("scenarios: []")).isInstanceOf(ApiException.class);
    }

    @Test
    void brokenYamlIsRejectedWithBadRequest() {
        assertThatThrownBy(() -> yaml.parse("suite: x\nscenarios: [unclosed"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void yamlCannotInstantiateArbitraryClasses() {
        String evil = "suite: x\nscenarios:\n  - id: !!java.lang.ProcessBuilder [[\"calc\"]]\n";
        assertThatThrownBy(() -> yaml.parse(evil)).isInstanceOf(ApiException.class);
    }
}