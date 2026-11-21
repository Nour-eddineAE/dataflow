import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Inflater;

public class Main {
    static void emit(String method, String params) {
        System.out.println(
                "{\"__dagster_pipes_version\":\"0.1\",\"method\":\"" + method + "\",\"params\":" + params + "}");
        System.out.flush();
    }

    static String decode(String raw) throws Exception {
        byte[] zipped = Base64.getDecoder().decode(raw);
        Inflater inflater = new Inflater();
        inflater.setInput(zipped);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!inflater.finished()) {
            out.write(buf, 0, inflater.inflate(buf));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        String context = decode(System.getenv("DAGSTER_PIPES_CONTEXT"));
        System.err.println("context: " + context.substring(0, Math.min(300, context.length())));
        emit("opened", "{\"extras\":{}}");
        emit("log", "{\"level\":\"INFO\",\"message\":\"engine container says hello\"}");
        emit(
                "report_asset_materialization",
                "{\"asset_key\":\"java_hello\",\"data_version\":null,\"metadata\":{\"rows\":{\"raw_value\":42,\"type\":\"int\"},\"table\":{\"raw_value\":\"s3a://lake/demo/\",\"type\":\"text\"}}}");
        emit(
                "report_asset_check",
                "{\"asset_key\":\"java_hello\",\"check_name\":\"rows_positive\",\"passed\":true,\"severity\":\"WARN\",\"metadata\":{\"rows\":{\"raw_value\":42,\"type\":\"int\"}}}");
        emit("closed", "{}");
    }
}
