package cn.karpov.music.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {
    @Test
    void okWrapsDataWithoutMessage() {
        ApiResponse<String> response = ApiResponse.ok("ready");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("ready");
        assertThat(response.message()).isNull();
    }

    @Test
    void failCanCarryMessageOnly() {
        ApiResponse<String> response = ApiResponse.fail("bad request");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.message()).isEqualTo("bad request");
    }

    @Test
    void failCanCarryDebugPayload() {
        ApiResponse<Integer> response = ApiResponse.fail(42, "gateway failed");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isEqualTo(42);
        assertThat(response.message()).isEqualTo("gateway failed");
    }
}
