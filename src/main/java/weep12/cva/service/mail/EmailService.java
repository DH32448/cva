package weep12.cva.service.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务——使用 Spring Mail + SMTP。
 *
 * <p>配置位于 application.yml 的 spring.mail 节点。
 * QQ 邮箱需使用授权码而非登录密码，在 QQ 邮箱设置→账户→POP3/IMAP/SMTP 中生成。
 * SMTP 服务器: smtp.qq.com, 端口: 587 (STARTTLS)。</p>
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * 发送 HTML 格式邮件。
     *
     * @param to      收件人地址
     * @param subject 邮件主题
     * @param html    邮件正文（HTML）
     */
    public void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("邮件发送成功: to={}, subject={}", to, subject);
        } catch (MessagingException e) {
            log.error("邮件发送失败: to={}, error={}", to, e.getMessage());
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    /**
     * 发送验证码邮件——使用固定模板。
     *
     * @param to   收件人邮箱
     * @param code 6位验证码
     * @param type 用途类型（login/register）
     */
    public void sendVerificationCode(String to, String code, String type) {
        String purpose = "register".equals(type) ? "注册" : "登录";
        String subject = "【CVA商城】" + purpose + "验证码";
        String html = buildCodeEmailTemplate(code, purpose);
        sendHtml(to, subject, html);
    }

    /**
     * 构建验证码邮件 HTML 模板。
     */
    private String buildCodeEmailTemplate(String code, String purpose) {
        return """
                <div style="max-width:480px;margin:0 auto;padding:32px 24px;
                            background:#fff;border-radius:8px;box-shadow:0 2px 12px rgba(0,0,0,.08);
                            font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;">
                  <h2 style="margin:0 0 8px;color:#1a1a1a;font-size:20px;">CVA 商城</h2>
                  <p style="margin:0 0 24px;color:#666;font-size:14px;">您正在进行%s操作，验证码如下：</p>
                  <div style="background:#f5f7fa;border-radius:6px;padding:20px;text-align:center;margin-bottom:24px;">
                    <span style="font-size:32px;font-weight:700;letter-spacing:6px;color:#333;">%s</span>
                  </div>
                  <p style="margin:0 0 8px;color:#999;font-size:12px;">验证码 5 分钟内有效，请勿泄露给他人。</p>
                  <p style="margin:0;color:#999;font-size:12px;">如非本人操作，请忽略此邮件。</p>
                </div>
                """.formatted(purpose, code);
    }
}
