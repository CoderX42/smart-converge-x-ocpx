import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SignRequest {
  public static void main(String[] args) throws Exception {
    if (args.length != 3) throw new IllegalArgumentException("METHOD RAW_PATH BODY_FILE required");
    String secret = System.getenv("SCX_CHANNEL_SECRET");
    if (secret == null || secret.isBlank()) throw new IllegalStateException("SCX_CHANNEL_SECRET is required");
    byte[] body = Files.readAllBytes(Path.of(args[2]));
    String timestamp = Long.toString(Instant.now().toEpochMilli());
    String nonce = UUID.randomUUID().toString().replace("-", "");
    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    String canonical = String.join("\n", args[0].toUpperCase(), args[1], timestamp, nonce, digest);
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
    String signature = HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(UTF_8)));
    System.out.println("X-Timestamp: " + timestamp);
    System.out.println("X-Nonce: " + nonce);
    System.out.println("X-Sign: " + signature);
  }
}
