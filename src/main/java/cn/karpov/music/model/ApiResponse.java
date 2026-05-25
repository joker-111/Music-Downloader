package cn.karpov.music.model;

/**
 * 前后端约定的统一响应外壳。
 *
 * <p>成功时 data 承载业务数据；失败时 message 用于前端状态栏展示。</p>
 */
public record ApiResponse<T>(boolean success, T data, String message) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }

    public static <T> ApiResponse<T> fail(T data, String message) {
        return new ApiResponse<>(false, data, message);
    }
}
