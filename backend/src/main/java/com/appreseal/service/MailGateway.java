package com.appreseal.service;

import com.appreseal.config.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 找回密码邮件。生产走 SMTP；测试把验证码留在内存里，不写进 HTTP 响应。
 */
@Service
public class MailGateway {
    private static final Logger log = LoggerFactory.getLogger(MailGateway.class);

    private final AppProperties props;
    private final ConcurrentHashMap<String, String> captured = new ConcurrentHashMap<>();

    public MailGateway(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void logMode() {
        if (props.getMail().isCapture()) {
            log.warn("邮件验证码处于捕获模式，仅用于测试，不要在生产打开");
        } else if (canSend()) {
            log.info("找回密码邮件已启用：{}", props.getMail().getHost());
        } else {
            log.info("找回密码邮件未配置，忘记密码接口会返回暂不可用");
        }
    }

    public boolean canSend() {
        AppProperties.Mail mail = props.getMail();
        if (!mail.isEnabled()) {
            return false;
        }
        if (mail.isCapture()) {
            return true;
        }
        return notBlank(mail.getHost()) && notBlank(mail.getFrom());
    }

    public void sendResetCode(String to, String code, long ttlMinutes) {
        if (props.getMail().isCapture()) {
            captured.put(to.toLowerCase(Locale.ROOT), code);
            log.info("重置验证码已捕获（测试模式），收件人 {}", to);
            return;
        }
        if (!canSend()) {
            throw new IllegalStateException("邮件服务未配置");
        }
        try {
            JavaMailSenderImpl sender = sender();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(InternetAddress.parse(props.getMail().getFrom())[0]);
            helper.setTo(to);
            helper.setSubject("AppReseal 密码重置验证码");
            helper.setText("""
                    你正在重置 AppReseal 账号密码。

                    验证码：%s
                    %d 分钟内有效，请勿告诉他人。

                    如果不是你本人操作，忽略这封邮件即可。
                    """.formatted(code, Math.max(1, ttlMinutes)), false);
            sender.send(message);
            log.info("已发送密码重置邮件到 {}", to);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("邮件发送失败", e);
        }
    }

    /** 仅捕获模式返回验证码，供测试读取。生产永远是 null。 */
    public String capturedCode(String email) {
        if (!props.getMail().isCapture() || email == null) {
            return null;
        }
        return captured.get(email.trim().toLowerCase(Locale.ROOT));
    }

    private JavaMailSenderImpl sender() {
        AppProperties.Mail mail = props.getMail();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.getHost());
        sender.setPort(mail.getPort() > 0 ? mail.getPort() : 587);
        if (notBlank(mail.getUsername())) {
            sender.setUsername(mail.getUsername());
            sender.setPassword(mail.getPassword() == null ? "" : mail.getPassword());
        }
        Properties p = sender.getJavaMailProperties();
        p.put("mail.smtp.auth", Boolean.toString(notBlank(mail.getUsername())));
        p.put("mail.smtp.starttls.enable", Boolean.toString(mail.isStarttls()));
        p.put("mail.smtp.ssl.enable", Boolean.toString(mail.isSsl()));
        p.put("mail.smtp.connectiontimeout", "8000");
        p.put("mail.smtp.timeout", "8000");
        p.put("mail.smtp.writetimeout", "8000");
        return sender;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
