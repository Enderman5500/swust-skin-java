package cn.edu.swust.mc.skin;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public final class MailService {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final Config config;

    public MailService(Config config) {
        this.config = config;
    }

    public boolean enabled() {
        return config.mailEnabled;
    }

    public void sendVerificationCode(String to, String code, int minutes,
                                     String siteName, String siteShortName) throws IOException {
        if (!enabled()) throw new IOException("邮件服务未启用");
        if (config.mailHost.isBlank() || config.mailUsername.isBlank()
                || config.mailPassword.isBlank() || config.mailFrom.isBlank()) {
            throw new IOException("SMTP 配置不完整");
        }

        String fromName = config.mailFromName.isBlank() ? "星空社皮肤站" : config.mailFromName;
        String shortName = siteShortName == null || siteShortName.isBlank() ? "星空社" : siteShortName;
        String title = siteName == null || siteName.isBlank() ? "星空社皮肤站" : siteName;
        String html = verificationHtml(title, code, minutes);
        String payload = buildMessage(to, fromName, "【" + shortName + "】邮箱验证码", html);

        try (Socket socket = openSocket();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
            Smtp smtp = new Smtp(reader, writer);
            smtp.expect(smtp.read(), 220);
            smtp.command("EHLO " + localHostName(), 250);

            smtp.command("AUTH LOGIN", 334);
            smtp.command(base64(config.mailUsername), 334);
            smtp.command(base64(config.mailPassword), 235);

            smtp.command("MAIL FROM:<" + config.mailFrom + ">", 250);
            smtp.command("RCPT TO:<" + to + ">", 250, 251);
            smtp.command("DATA", 354);
            smtp.writeData(payload);
            smtp.expect(smtp.read(), 250);
            try {
                smtp.command("QUIT", 221);
            } catch (IOException ignored) {
                // 邮件已经投递成功，QUIT 失败不影响结果。
            }
        }
    }

    private Socket openSocket() throws IOException {
        if (!config.mailSsl) {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(config.mailHost, config.mailPort), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            return socket;
        }

        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) factory.createSocket();
        socket.connect(new InetSocketAddress(config.mailHost, config.mailPort), CONNECT_TIMEOUT_MS);
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setServerNames(List.of(new SNIHostName(config.mailHost)));
        socket.setSSLParameters(parameters);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        socket.startHandshake();
        return socket;
    }

    private String buildMessage(String to, String fromName, String subject, String html) {
        String domain = config.mailFrom.substring(config.mailFrom.indexOf('@') + 1);
        String encodedFromName = "=?UTF-8?B?" + base64(fromName) + "?=";
        String encodedSubject = "=?UTF-8?B?" + base64(subject) + "?=";
        String body = Base64.getMimeEncoder(76, new byte[]{'\r', '\n'})
                .encodeToString(html.getBytes(StandardCharsets.UTF_8));
        String date = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.now(ZoneOffset.UTC));
        return "From: " + encodedFromName + " <" + config.mailFrom + ">\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + encodedSubject + "\r\n"
                + "Date: " + date + "\r\n"
                + "Message-ID: <" + UUID.randomUUID() + "@" + domain + ">\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + body;
    }

    private static String localHostName() {
        try {
            String name = InetAddress.getLocalHost().getHostName();
            return name == null || name.isBlank() ? "localhost" : name;
        } catch (Exception e) {
            return "localhost";
        }
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String verificationHtml(String siteName, String code, int minutes) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <body style="margin:0;padding:0;background:#f3f7fb;font-family:Arial,'Microsoft YaHei',sans-serif;color:#1d2b3a">
                  <div style="max-width:560px;margin:32px auto;padding:32px;background:#ffffff;border:1px solid #dbe6f0;border-radius:14px">
                    <p style="margin:0 0 8px;font-size:12px;letter-spacing:2px;color:#5b7892">SWUST MINECRAFT</p>
                    <h1 style="margin:0 0 20px;font-size:22px">%s</h1>
                    <p style="margin:0 0 12px">你正在注册社团皮肤站账号，本次邮箱验证码为：</p>
                    <div style="margin:20px 0;padding:16px 20px;border-radius:10px;background:#eef6fb;color:#0f6b8f;font-size:30px;font-weight:700;letter-spacing:8px;text-align:center">%s</div>
                    <p style="margin:0 0 8px">验证码 %d 分钟内有效，请勿泄露给他人。</p>
                    <p style="margin:0;color:#7a8794;font-size:12px">如果这不是你本人的操作，请忽略本邮件。</p>
                  </div>
                </body>
                </html>
                """.formatted(escapeHtml(siteName), escapeHtml(code), minutes);
    }

    private static String escapeHtml(String value) {
        return String.valueOf(value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record Response(int code, String text) {
    }

    private static final class Smtp {
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private Smtp(BufferedReader reader, BufferedWriter writer) {
            this.reader = reader;
            this.writer = writer;
        }

        private Response read() throws IOException {
            StringBuilder text = new StringBuilder();
            while (true) {
                String line = reader.readLine();
                if (line == null) throw new EOFException("SMTP 连接已断开");
                text.append(line).append('\n');
                if (line.length() < 4 || line.charAt(3) != '-') {
                    int code;
                    try {
                        code = Integer.parseInt(line.substring(0, 3));
                    } catch (RuntimeException e) {
                        throw new IOException("SMTP 响应格式异常：" + line);
                    }
                    return new Response(code, text.toString().trim());
                }
            }
        }

        private Response command(String command, int... expected) throws IOException {
            writer.write(command);
            writer.write("\r\n");
            writer.flush();
            Response response = read();
            expect(response, expected);
            return response;
        }

        private void writeData(String payload) throws IOException {
            for (String line : payload.split("\r\n", -1)) {
                if (line.startsWith(".")) writer.write(".");
                writer.write(line);
                writer.write("\r\n");
            }
            writer.write(".\r\n");
            writer.flush();
        }

        private void expect(Response response, int... expected) throws IOException {
            for (int code : expected) {
                if (response.code() == code) return;
            }
            throw new IOException("SMTP 返回 " + response.code() + "：" + response.text());
        }
    }
}
